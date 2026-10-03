# iac — AWS Docker host for api-hub

Terraform for a single EC2 instance that runs the api-hub components as
containers behind Caddy, published as:

- https://api-portal.obya.ch
- https://api-onboarding.obya.ch
- https://api-scorer.obya.ch
- https://api-registry.obya.ch

The images are the ones `.github/workflows/build-images.yml` already pushes to
GHCR — nothing is built here. This stack is the *host*; `host/` is what runs on
it; `deploy.sh` is what puts the two together.

The `helm/` charts are a separate, local path (Rancher Desktop, `*.localhost`)
and are unaffected by any of this.

This is the same stack as `ba-hub/iac`, `dev-hub/iac`, `doc-hub/iac` and
`arch-hub/iac`. The hubs are independent: separate instance, separate Elastic
IP, separate roles. They share the account, and therefore the single GitHub OIDC
provider, which ba-hub owns — see `create_github_oidc_provider` below.

**This is the only hub in the family with state and secrets.** The other four
render models that live in their repositories; this one persists registered
contracts in PostgreSQL, so it follows doc-hub's shape rather than dev-hub's:
a `secrets.env` rendered by the deploy job, and named volumes that must outlive
every rollout.

## Shape

```
        DigitalOcean DNS (obya.ch)   ← records added by hand, outside Terraform
    api-portal  api-onboarding  api-scorer  api-registry
                        │
                   Elastic IP
                        │
                   EC2 t3.medium ── security group: 80 + 443 only, no inbound admin port
                        │
                    Caddy :80 :443             TLS, Let's Encrypt, HTTP→HTTPS
                     ├── api-portal      :4321   (nothing below is published
                     ├── api-onboarding  :8080    to the host)
                     ├── api-scorer      :3000
                     └── api-registry    :1337
                              └── api-registry-db :5432   no site block at all

    GitHub Actions ──OIDC──► sts:AssumeRoleWithWebIdentity ──► ssm:SendCommand ──┘
```

Only Caddy is bound to the host, so no component can be reached over plain HTTP
and PostgreSQL is not reachable from outside the compose network at all.
Administration and deployment both arrive through the SSM agent's *outbound*
connection, so there is no inbound admin surface.

## What runs where

| Component | Port | Health check | Notes |
|---|---|---|---|
| `api-portal` | 4321 | `/healthz` | The Catalogue. Server-rendered, and the only component that talks to the other two — which is what lets the registry stay unexposed |
| `api-onboarding` | 8080 | `/actuator/health/readiness` | JVM. 90 s `start_period` for boot; the chart budgets 30 × 5 s for the same window |
| `api-scorer` | 3000 | `/healthz` | Spectral in-process. **Port 3000, not 8081** — the chart maps Service 8081 onto this container, and compose has no such indirection |
| `api-registry` | 1337 | `/_health` | Strapi. 180 s `start_period`: the first boot migrates the whole schema against an empty database |
| `api-registry-db` | 5432 | `pg_isready` | No subdomain and no Caddy block. `pg_isready` rather than a TCP probe — the latter passes while the cluster is still recovering, and Strapi would connect too early and fail its migration |

`api-onboarding` is configured by a **mounted `application.yaml`**, not env
vars — `host/api-onboarding/application.yaml`, bind-mounted at
`/app/config/application.yaml`, exactly as `helm/api-onboarding` does it. Spring
Boot loads `./config/` ahead of the classpath, so it wins over the defaults baked
into the jar while everything it does not mention still applies. It also flips
both adapters from the chart's `dummy` to `strapi` and `spectral`.

### Two things that are public and worth knowing

- **`/actuator*` is blocked at Caddy.** `application.yaml` exposes Actuator as
  `include: '*'` for probes and diagnostics, which also means `/env`,
  `/configprops`, `/heapdump` and `/mappings` answer to anyone who asks. The
  site block returns 404 for that prefix. The container's own healthcheck runs
  against `127.0.0.1` and never passes through Caddy, so it is unaffected.
- **`api-onboarding` and `api-scorer` are public APIs with no authentication in
  front of them.** They are published because the local charts publish them, and
  because an API platform whose API is unreachable is an odd thing. `api-portal`
  reaches both over the compose network, so neither *has* to be public: drop the
  entry from `var.subdomains` and its block from `host/Caddyfile` and the
  Catalogue keeps working. Decide this deliberately rather than inheriting it.

## How CI reaches AWS

The deploy job holds no AWS credential. It presents a token signed by GitHub,
STS validates it against the role's trust policy, and returns credentials that
expire when the job ends.

```
role      api-hub-deploy                      (iac/oidc.tf)
trusts    token.actions.githubusercontent.com
only if   aud         = sts.amazonaws.com
    and   repository  = vondacho/api-hub
    and   ref         = refs/heads/main
    and   environment = production
may do    ssm:SendCommand  → AWS-RunShellScript, on instances tagged Project=api-hub
          plus the read calls needed to find the host and collect the output
```

The three claim conditions are the load-bearing ones, and all must hold: a
workflow in another repository, on a branch, or outside the `production`
environment gets nothing. The `Project=api-hub` tag condition also keeps this
role off the ba-hub, dev-hub, doc-hub and arch-hub hosts in the same account,
and their roles off this one.

They are separate conditions rather than one `sub` string on purpose. GitHub
mints `sub` in two shapes and the choice is not ours to make:

```
classic    repo:vondacho/api-hub:environment:production
immutable  repo:vondacho@3777501/api-hub@<id>:environment:production
```

Every repository created since 2026-07-15 gets the immutable form
automatically — the numeric IDs stop a deleted-and-recreated repository name
from inheriting someone else's trust. This one predates that date and is still
on the classic form, but may be flipped later. Matching `sub` exactly would mean
pinning IDs that differ per fork and per account, and would break on that flip,
so the policy only requires `sub` to look like this repository (STS insists it
be constrained somehow) and does the real work with `repository`, `ref`, and
`environment`, which STS has validated natively for GitHub since January 2026.

Symptom when any of them disagrees with the token: `Could not assume role with
OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity`. To see what
GitHub actually mints:

```sh
gh api /repos/vondacho/api-hub/actions/oidc/customization/sub
```

The role cannot start, stop, or reconfigure the instance, and cannot touch any
instance that is not tagged as part of this project.

## Connecting to AWS yourself

Terraform authenticates through the standard AWS credential chain. Nothing in
this repository holds an AWS credential, and none is written into the state
file — the provider block sets only a region and an optional profile name.

Recommended, in order of preference:

1. **IAM Identity Center (SSO)** — short-lived, nothing long-lived on disk:

   ```sh
   aws configure sso --profile api-hub    # once
   aws sso login --profile api-hub        # per session, expires on its own
   ```

   then set `aws_profile = "api-hub"` in `terraform.tfvars`, or export
   `AWS_PROFILE=api-hub`.

2. **An IAM user's access keys in `~/.aws/credentials`** — `aws configure
   --profile api-hub`. The file is outside the repo and `chmod 600`. Give the
   user only what this stack touches (EC2, VPC read, IAM to create the two
   roles, SSM read for the AMI parameter), and rotate the keys periodically.

3. **Environment variables** (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
   `AWS_SESSION_TOKEN`) — fine for a one-off, but they leak into shell history
   and process listings, so prefer a profile for anything repeated.

Verify before applying: `aws sts get-caller-identity --profile api-hub`.

## Where the secrets live

| Secret | Lives in | Why not elsewhere |
|---|---|---|
| AWS credentials | `~/.aws/` (or an SSO session) on your machine only | Used solely to *provision*. CI gets short-lived credentials from OIDC instead. |
| CI's AWS credentials | nowhere — minted per job, expire with it | That is the whole point of OIDC. |
| Strapi + PostgreSQL credentials | GitHub repository secrets → `/opt/api-hub/secrets.env`, 0640 | Never in git, never in the state file, never in an image. See below. |
| SSH private key | nowhere — the host has no key pair by default | Replaced by SSM Session Manager, authenticated by your AWS identity. |
| Let's Encrypt account key | the `caddy_data` volume on the host | Generated there, never leaves. |
| GHCR pull credential | none — the packages are public | See step 1 below. |

Two things must stay out of git, and `.gitignore` already covers both:
`terraform.tfvars` and `terraform.tfstate` (the full resource inventory).
`.terraform.lock.hcl` is the exception — commit it, so every apply resolves the
same provider build.

### The seven application secrets

Set these as repository **secrets** (Settings → Secrets and variables → Actions
→ Secrets). They are the same set `helm/api-registry` generates on a local
install:

| Secret | Used by |
|---|---|
| `POSTGRES_PASSWORD` | `api-registry-db` — and re-emitted as `DATABASE_PASSWORD` for Strapi, so one value covers both spellings |
| `APP_KEYS` | Strapi session signing (comma-separated array) |
| `API_TOKEN_SALT` | Strapi |
| `ADMIN_JWT_SECRET` | Strapi admin sessions |
| `TRANSFER_TOKEN_SALT` | Strapi |
| `JWT_SECRET` | Strapi |
| `ENCRYPTION_KEY` | Strapi — **encrypts stored fields** |

Generate them once and leave them alone — rotating is not free. `ENCRYPTION_KEY`
makes already-stored encrypted fields unreadable; `APP_KEYS` or
`ADMIN_JWT_SECRET` signs every administrator out.

```sh
gh secret set POSTGRES_PASSWORD    -b "$(openssl rand -hex 32)"
gh secret set APP_KEYS             -b "$(for i in 1 2 3 4; do openssl rand -base64 32 | tr -d '\n'; [ $i -lt 4 ] && printf ,; done)"
gh secret set API_TOKEN_SALT       -b "$(openssl rand -base64 32)"
gh secret set ADMIN_JWT_SECRET     -b "$(openssl rand -base64 32)"
gh secret set TRANSFER_TOKEN_SALT  -b "$(openssl rand -base64 32)"
gh secret set JWT_SECRET           -b "$(openssl rand -base64 32)"
gh secret set ENCRYPTION_KEY       -b "$(openssl rand -base64 32)"
```

Keep each one to a single line with no quotes: `secrets.env` is `KEY=value`,
parsed literally.

`deploy.sh` takes **all seven or none**:

- *All present* — `secrets.env` is rewritten.
- *None present* — the file on the host is left exactly as it is, so a manual
  deploy from a laptop that holds no secrets does not decapitate the host.
- *Some present* — refused. A half-written file boots Strapi against the wrong
  credentials, and rotating half of them signs everyone out while leaving the
  encrypted fields unreadable.

⚠️ **`ENCRYPTION_KEY` is not rotatable in place.** Changing it makes every
already-encrypted field permanently unreadable. The chart marks its Secret
`helm.sh/resource-policy: keep` for the same reason.

## Before the first apply

1. **Make the five GHCR packages public** (`api-portal`, `api-onboarding`,
   `api-registry`, `api-scorer`, `api-registry-db`), in the repo's *Packages →
   package settings*. The host then pulls anonymously and there is no registry
   credential to provision or rotate. The images contain no secret — that is why
   `api-registry-db`'s Dockerfile deliberately sets no `POSTGRES_PASSWORD`.
2. **Know where `obya.ch` DNS lives.** It is served by DigitalOcean
   (`ns[1-3].digitalocean.com`), not Route 53, so Terraform does not touch it.
   You add the A records by hand after the apply — the `dns_records` output
   prints them.
3. **Nothing to decide about the GitHub OIDC provider.** An AWS account may hold
   only one per issuer URL, and this account's belongs to ba-hub, so
   `create_github_oidc_provider` defaults to `false` and this stack references
   it. `terraform plan` verifies that against the account before anything is
   created — `iac/oidc-provider-exists.sh` asks AWS which providers exist and
   who owns them (the `Project` tag), and a precondition turns a disagreement
   into a failed plan with the fix in the message.

   | Setting | Account | What happens now |
   |---|---|---|
   | `false` | provider exists | referenced — the normal case here |
   | `true` | another stack's provider exists | plan fails, naming the owner; set it to `false` |
   | `true` | none, or this stack's own | created, or kept — no false alarm on re-plan |
   | `false` | none | plan fails; set it to `true` in a fresh account |

## Apply

```sh
cp terraform.tfvars.example terraform.tfvars   # then edit
aws sts get-caller-identity                    # confirm you are the right principal
terraform init
terraform plan
terraform apply
```

Expect 12 resources: 1 security group + 3 rules, 1 instance, 1 EIP +
association, 2 IAM roles + policies, 1 instance profile. No OIDC provider — it
is ba-hub's, and this stack references it.

Then create the DNS records at DigitalOcean — `terraform output dns_records`
prints them ready to copy:

```
A  api-onboarding  <elastic ip>  TTL 300
A  api-portal      <elastic ip>  TTL 300
A  api-registry    <elastic ip>  TTL 300
A  api-scorer      <elastic ip>  TTL 300
```

Because the address is an Elastic IP, this is a one-time step: it survives an
instance rebuild. Confirm with `dig +short api-portal.obya.ch` before expecting
certificates — Caddy issues four of them on its first start, and each needs its
name to resolve or the ACME HTTP-01 challenge fails and retries with a backoff.

Finally, set two repository **variables** (same screen as the secrets, under
Variables). They are variables, not secrets: neither is sensitive, and a role ARN
is useless without a matching OIDC token.

| Variable | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw deploy_role_arn` |
| `AWS_REGION` | your `aws_region` (optional; defaults to `eu-central-1`) |

The deploy job also declares `environment: production`, so that environment has
to exist in the repository (Settings → Environments) — creating it is what makes
GitHub put the `environment` claim in the token the trust policy requires.

The first push to `main` after that runs the `deploy` job. Expect it to take a
few minutes the first time: Strapi migrates its whole schema against an empty
database before it answers a health check, and `docker compose up --wait` blocks
until it does.

```sh
gh variable set AWS_DEPLOY_ROLE_ARN -b "arn:aws:iam::740948698581:role/api-hub-deploy"
gh variable set AWS_REGION -b "eu-central-1"
```

## Day-to-day

Changing what runs on the host — an image tag, an env var, an adapter in
`application.yaml`, a new site in the Caddyfile — is an edit to `host/` and a
push to `main`. The instance is not touched.

Deploy by hand, with any identity holding the same permissions — the CI job runs
this exact script. Without the secrets in your environment it leaves
`secrets.env` alone:

```sh
./deploy.sh
```

Open a shell on the host without a key or an open port:

```sh
aws ssm start-session --target "$(terraform output -raw instance_id)"
sudo -i
cd /opt/api-hub
docker compose ps
docker compose logs -f api-registry
```

This needs the Session Manager plugin installed locally
(`brew install --cask session-manager-plugin`).

A reboot brings the stack back on its own: `user-data.sh` installs an
`api-hub.service` unit that replays `docker compose up -d`.

### Backups — read this before you need it

`registry_db` is a Docker named volume on the instance's root EBS volume. It is
**the only copy of the catalogue**, and nothing in this stack backs it up.
`terraform apply -replace=aws_instance.host` — the documented way to pick up a
newer AMI in the other hubs — **destroys it**. So does terminating the instance
for any other reason.

Dump before doing anything to the instance:

```sh
aws ssm start-session --target "$(terraform output -raw instance_id)"
sudo -i
cd /opt/api-hub
docker compose exec -T api-registry-db pg_dump -U strapi strapi | gzip >/tmp/strapi-$(date +%F).sql.gz
```

then copy it off the host. An EBS snapshot of the root volume works too and
needs no database knowledge, but it is instance-wide rather than a portable
dump.

### Break-glass

If the SSM agent ever wedges, the host becomes unreachable — that is the
trade-off for having no open admin port. Recover by setting `ssh_key_name` and
`ssh_allowed_cidrs` to your own address and applying; both default to nothing,
so this is a deliberate, visible change in the plan. `ssh_key_name` only takes
effect on a fresh instance, so an existing host needs a replace — and a replace
destroys `registry_db`, so dump it first.

## Cost

Roughly USD 34/month in `eu-central-1`: t3.medium on-demand (~30), 30 GiB gp3
(~3), plus egress. Higher than the other hubs because of the instance size: a
JVM, Strapi, PostgreSQL and two Node servers do not fit in the 2 GiB the others
run on. No Route 53 charge — DNS stays at DigitalOcean. SSM, the OIDC provider,
and IAM roles are free.

## Teardown

```sh
terraform destroy
```

⚠️ **This destroys the registry's database along with the instance.** Dump it
first — see *Backups* above.

Removes the instance, EIP, security group and both IAM roles. It does **not**
remove the OIDC provider — with `create_github_oidc_provider = false` this stack
only references ba-hub's, and Terraform never destroys what it does not own.

The DNS records and the GHCR packages are untouched; delete the four A records
at DigitalOcean by hand.
