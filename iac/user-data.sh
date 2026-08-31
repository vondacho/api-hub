#!/usr/bin/env bash
# Cloud-init for the api-hub Docker host.
#
# This script only *prepares* the machine: Docker, the compose plugin, swap, and
# the deployment directory. It deliberately knows nothing about which containers
# run there — that is host/docker-compose.yml, shipped by the deploy job in
# .github/workflows/build-images.yml. Keeping the two apart means editing the
# runtime is a git push, not an instance replacement.
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates curl gnupg

# Canonical's AMI ships the SSM agent as a snap, but not always started. This
# is the host's only management path — both the CI deploy job and
# `aws ssm start-session` go through it — so make sure it is up rather than
# assuming it.
snap start --enable amazon-ssm-agent || systemctl enable --now amazon-ssm-agent

# Swap. Not in ba-hub's copy of this script, and the reason is api-hub's shape:
# six containers, and unlike the other hubs two of them are heavy on their own —
# a JVM (helm/api-onboarding/values.yaml budgets it 512Mi-1Gi) and Strapi, which
# peaks higher still while it migrates its schema against an empty database on
# first boot, with PostgreSQL wanting its shared buffers at the same moment.
# var.instance_type is already t3.medium for that reason; this is the floor
# under the first deploy, not a substitute for the extra memory.
#
# Swap is a floor, not a fix — a host that leans on it is slow. If the JVM and
# the registry are regularly paging, move up an instance size and this becomes
# harmless ballast.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >>/etc/fstab
  # Prefer reclaiming page cache over swapping anonymous memory; the containers
  # are long-lived server processes whose working set should stay resident —
  # and PostgreSQL in particular is much slower once its buffers are paged out.
  sysctl -w vm.swappiness=10
  echo 'vm.swappiness=10' >/etc/sysctl.d/99-swappiness.conf
fi

# Docker's own repository rather than Ubuntu's docker.io package: only the
# former ships docker-compose-plugin, and `docker compose` is what the deploy
# job calls.
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg |
  gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

cat >/etc/apt/sources.list.d/docker.list <<REPO
deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable
REPO

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker ubuntu

# Containers must come back after a reboot without anyone logging in. The unit
# is a one-shot that replays whatever compose file is currently on disk; the
# `restart: unless-stopped` policies handle everything short of a reboot.
install -d -o ubuntu -g ubuntu /opt/api-hub

cat >/opt/api-hub/.env <<ENV
ACME_EMAIL=${acme_email}
ENV
chown ubuntu:ubuntu /opt/api-hub/.env
chmod 0640 /opt/api-hub/.env

# The application secrets live next to it in secrets.env, written by the deploy
# job from the repository secrets — see iac/deploy.sh. Created empty here so the
# compose file's `env_file` always resolves, even on a host that has never been
# deployed to.
touch /opt/api-hub/secrets.env
chown ubuntu:ubuntu /opt/api-hub/secrets.env
chmod 0640 /opt/api-hub/secrets.env

cat >/etc/systemd/system/api-hub.service <<'UNIT'
[Unit]
Description=api-hub containers
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/api-hub
ExecStart=/usr/bin/docker compose up -d --remove-orphans
ExecStop=/usr/bin/docker compose down

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
# Enabled but not started: there is no compose file on disk until the first
# deploy, and starting now would only log a failure.
systemctl enable api-hub.service
