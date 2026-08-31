variable "aws_region" {
  description = "AWS region hosting the Docker host. Must match the region of var.ssh_key_name."
  type        = string
  default     = "eu-central-1"
}

variable "aws_profile" {
  description = "Named profile from ~/.aws/config to authenticate with. Leave null to use the ambient credential chain (env vars, SSO, instance role)."
  type        = string
  default     = null
}

variable "domain" {
  description = "Apex domain the components are published under. DNS for it is served by DigitalOcean, outside this stack — only used to build URLs and the dns_records output."
  type        = string
  default     = "obya.ch"
}

variable "subdomains" {
  description = <<-DESC
    Subdomain per publicly reachable component. Keys are the compose service
    names in host/docker-compose.yml; the values must match the site blocks in
    host/Caddyfile — nothing derives one from the other, so a name added here
    also needs a block there.

    api-registry-db is absent on purpose: PostgreSQL is not an HTTP service and
    is reachable on the compose network only.

    api-onboarding and api-scorer are the two worth a second thought. Both are
    HTTP APIs rather than sites, and api-portal reaches them over the compose
    network, so neither *has* to be public — the local charts expose both, which
    is why they are here. Dropping an entry plus its Caddy block makes that
    component internal-only, and the portal keeps working.
  DESC
  type        = map(string)
  default = {
    api-portal     = "api-portal"
    api-registry   = "api-registry"
    api-onboarding = "api-onboarding"
    api-scorer     = "api-scorer"
  }
}

variable "instance_type" {
  description = <<-DESC
    EC2 instance type. Must be x86_64 — the images are built linux/amd64 only
    (see .github/workflows/build-images.yml).

    t3.medium (4 GiB) rather than the t3.small the other hubs run on, because
    this one carries a JVM and a database that the others do not:
    helm/api-onboarding/values.yaml budgets it 512Mi–1Gi, Strapi peaks while it
    migrates its schema on first boot, and PostgreSQL wants its own shared
    buffers. Six containers in 2 GiB would OOM during the first deploy.
    user-data.sh adds a 2 GiB swapfile on top, as doc-hub's does.
  DESC
  type        = string
  default     = "t3.medium"
}

variable "ssh_key_name" {
  description = "Optional name of an existing EC2 key pair, for break-glass SSH. Leave null: administration and deployment both go through SSM Session Manager."
  type        = string
  default     = null
}

variable "ssh_allowed_cidrs" {
  description = "CIDR blocks allowed to reach port 22. Empty by default — there is no inbound admin surface unless you deliberately open one."
  type        = list(string)
  default     = []
}

variable "acme_email" {
  description = "Contact address Caddy registers with Let's Encrypt; receives expiry warnings if renewal ever stops working."
  type        = string
}

variable "root_volume_size" {
  description = "Root EBS volume size in GiB. Five images and their layers, the swapfile, the PostgreSQL volume and Strapi's uploads. The JVM image is the large one — eclipse-temurin:25-jre plus the extracted Spring layers."
  type        = number
  default     = 30
}

variable "github_repository" {
  description = "owner/name of the repository allowed to assume the deploy role."
  type        = string
  default     = "vondacho/api-hub"
}

variable "github_deploy_ref" {
  description = "Git ref the deploy role is bound to. Only a workflow running on this exact ref can assume it."
  type        = string
  default     = "refs/heads/main"
}

variable "github_deploy_environment" {
  description = "GitHub Actions environment the deploy job declares. It selects the `sub` claim GitHub puts in the OIDC token, so it must match the workflow's `environment:` name exactly."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = <<-DESC
    Create the GitHub OIDC provider, rather than referencing one the account
    already has. AWS allows exactly one per issuer URL account-wide, so exactly
    one stack may own it — and in this account ba-hub already does, which is why
    this defaults to false.

    Set it to true only in an account that has none. Either way the value is
    checked against the account at plan time (oidc.tf), so a wrong answer fails
    the plan with an explanation instead of failing the apply half-built.
  DESC
  type        = bool
  default     = false
}
