#!/usr/bin/env bash
# Roll host/ onto the api-hub Docker host and restart the stack.
#
# Runs over SSM Send Command, not SSH: there is no key to hold and no inbound
# port to open. The CI deploy job calls this after assuming the deploy role via
# OIDC; you can call it yourself with any AWS identity that has the same
# permissions.
#
#   ./deploy.sh                 # deploy host/ as it stands
#   AWS_REGION=eu-west-1 ./deploy.sh
#
# The host directory is shipped inside the command itself as a base64 tar — a
# few kilobytes, well under the Send Command payload limit, and it means the
# host is brought to exactly what this repository says with no intermediate
# bucket to provision or clean up.
#
# The application secrets travel the same way, from this process's environment
# into /opt/api-hub/secrets.env — see "Secrets" below.
set -euo pipefail

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-central-1}}"
PROJECT_TAG="${PROJECT_TAG:-api-hub}"
NAME_TAG="${NAME_TAG:-api-hub-host}"
REMOTE_DIR="${REMOTE_DIR:-/opt/api-hub}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\033[1m==>\033[0m %s\n' "$*"; }
die() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[ -f "$HERE/host/docker-compose.yml" ] || die "no host/docker-compose.yml next to this script"

# --- Secrets ---------------------------------------------------------------
#
# Strapi will not boot without these, and PostgreSQL will not initialise without
# the password. api-hub is the only hub in this family that has any: the other
# four render models that live in their repositories and hold no state. They are read from this process's environment — repository
# secrets in CI, your shell for a manual deploy — and rendered into
# /opt/api-hub/secrets.env, which host/docker-compose.yml loads with `env_file`.
#
# All present  -> the file is rewritten.
# None present -> the file is left exactly as it is, so a manual deploy from a
#                 laptop that holds no secrets does not decapitate the host.
# Some present -> refuse. A half-written file boots Strapi against the wrong
#                 credentials, and rotating half of them signs everyone out
#                 while leaving the encrypted fields unreadable.
SECRET_KEYS=(
  POSTGRES_PASSWORD
  APP_KEYS
  API_TOKEN_SALT
  ADMIN_JWT_SECRET
  TRANSFER_TOKEN_SALT
  JWT_SECRET
  ENCRYPTION_KEY
)

present=() missing=()
for key in "${SECRET_KEYS[@]}"; do
  if [ -n "${!key:-}" ]; then present+=("$key"); else missing+=("$key"); fi
done

SECRETS_B64=""
if [ ${#present[@]} -gt 0 ] && [ ${#missing[@]} -gt 0 ]; then
  die "incomplete secrets: missing ${missing[*]} — set all ${#SECRET_KEYS[@]} or none"
elif [ ${#present[@]} -gt 0 ]; then
  say "Rendering secrets.env (${#present[@]} values)"
  SECRETS_FILE="# Written by iac/deploy.sh. Do not edit by hand — the next deploy
# overwrites it from the repository secrets."
  for key in "${SECRET_KEYS[@]}"; do
    SECRETS_FILE+="
$key=${!key}"
  done
  # Strapi reads the database password under its own name; one secret, two
  # spellings, so neither service needs the other's vocabulary.
  SECRETS_FILE+="
DATABASE_PASSWORD=$POSTGRES_PASSWORD"
  # Never written to disk here, and never printed: it goes straight into the
  # command payload below.
  SECRETS_B64="$(printf '%s\n' "$SECRETS_FILE" | base64 | tr -d '\n')"
else
  say "No secrets in the environment — leaving secrets.env untouched"
fi

say "Resolving the host"
INSTANCE_ID="$(aws ec2 describe-instances \
  --region "$REGION" \
  --filters "Name=tag:Name,Values=$NAME_TAG" "Name=instance-state-name,Values=running" \
  --query 'Reservations[].Instances[].InstanceId | [0]' \
  --output text)"
[ -n "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "None" ] || die "no running instance tagged Name=$NAME_TAG in $REGION"

# A host that is running but not registered with SSM cannot be reached, and the
# failure would otherwise surface as an opaque InvalidInstanceId from
# send-command.
aws ssm describe-instance-information \
  --region "$REGION" \
  --filters "Key=InstanceIds,Values=$INSTANCE_ID" \
  --query 'InstanceInformationList[0].PingStatus' \
  --output text | grep -q Online ||
  die "$INSTANCE_ID is not Online in SSM — check the instance profile and that the agent is running"

say "Packing host/"
PAYLOAD="$(tar -C "$HERE/host" -czf - . | base64 | tr -d '\n')"

# The tar carries only what is tracked in host/, so /opt/api-hub/.env — written
# once by user-data.sh and holding ACME_EMAIL — is never overwritten. secrets.env
# is written separately, above, and only when this process was given the values.
#
# AWS-RunShellScript writes the command to _script.sh and runs it with /bin/sh,
# which on Ubuntu is dash. Dash has no `pipefail`, so setting it at the top of
# the remote script aborts the whole deploy on line 1 with
# "set: Illegal option -o pipefail". Hand the body to bash explicitly instead —
# a `#!/bin/bash` shebang would not help, since the agent chooses the
# interpreter rather than executing the file directly.
#
# `set -x` starts *after* the payload is unpacked and the secrets are written:
# tracing a multi-kilobyte base64 blob would push the useful output past the
# 24,000-character limit that get-command-invocation returns, and tracing the
# other one would print the credentials into the command history.
SECRETS_STEP=""
if [ -n "$SECRETS_B64" ]; then
  SECRETS_STEP="printf %s '$SECRETS_B64' | base64 -d >$REMOTE_DIR/secrets.env
chown ubuntu:ubuntu $REMOTE_DIR/secrets.env
chmod 0640 $REMOTE_DIR/secrets.env"
fi

read -r -d '' REMOTE_SCRIPT <<REMOTE || true
exec /bin/bash -s <<'INNER'
set -euo pipefail
cd $REMOTE_DIR
printf %s '$PAYLOAD' | base64 -d | tar -xzf - -C $REMOTE_DIR
$SECRETS_STEP
set -x
chown -R ubuntu:ubuntu $REMOTE_DIR
docker compose pull
# --wait blocks until every healthcheck passes. api-registry's start_period is
# 180s for the schema migration, so the 600s command timeout is the real budget.
docker compose up -d --remove-orphans --wait
docker compose ps
# The superseded images are unreferenced once the containers have swapped;
# without this the root volume fills up over a few dozen deploys. Volumes are
# untouched — \`image prune\` never removes one, which is what keeps the
# registry's database out of its way.
docker image prune -f
INNER
REMOTE

say "Sending to $INSTANCE_ID"
COMMAND_ID="$(aws ssm send-command \
  --region "$REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --comment "api-hub deploy" \
  --timeout-seconds 600 \
  --parameters "$(jq -n --arg s "$REMOTE_SCRIPT" '{commands: [$s], executionTimeout: ["600"]}')" \
  --query 'Command.CommandId' \
  --output text)"

say "Waiting for $COMMAND_ID"
# `command-executed` polls to a terminal state but exits non-zero on failure
# before we can show the output, so swallow it and read the status ourselves.
aws ssm wait command-executed \
  --region "$REGION" \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" >/dev/null 2>&1 || true

RESULT="$(aws ssm get-command-invocation \
  --region "$REGION" \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" \
  --output json)"

printf '%s' "$RESULT" | jq -r '.StandardOutputContent'
STATUS="$(printf '%s' "$RESULT" | jq -r '.Status')"

if [ "$STATUS" != "Success" ]; then
  printf '%s' "$RESULT" | jq -r '.StandardErrorContent' >&2
  die "deploy finished with status $STATUS"
fi

say "Deployed"
