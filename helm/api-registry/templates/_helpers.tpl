{{/*
Expand the name of the chart.
*/}}
{{- define "api-registry.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name, capped at 63 chars for the DNS label limit.
*/}}
{{- define "api-registry.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "api-registry.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "api-registry.labels" -}}
helm.sh/chart: {{ include "api-registry.chart" . }}
{{ include "api-registry.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: api-hub
app.kubernetes.io/component: cms
{{- end }}

{{- define "api-registry.selectorLabels" -}}
app.kubernetes.io/name: {{ include "api-registry.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "api-registry.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "api-registry.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Image reference, defaulting the tag to the chart appVersion.
*/}}
{{- define "api-registry.image" -}}
{{- printf "%s:%s" .Values.image.repository (default .Chart.AppVersion .Values.image.tag) }}
{{- end }}

{{/*
Name of the Secret holding the Strapi application secrets.
*/}}
{{- define "api-registry.secretName" -}}
{{- default (include "api-registry.fullname" .) .Values.strapi.existingSecret }}
{{- end }}

{{/*
Resolve one Strapi secret.

Precedence: an explicitly pinned value, then whatever the live Secret already
holds, then a freshly generated one. The middle case is what makes `helm
upgrade` non-destructive — see the comment on `strapi:` in values.yaml for what
rotating each of these actually breaks.

Usage: include "api-registry.secret" (dict "ctx" $ "key" "JWT_SECRET" "value" .Values.strapi.jwtSecret "gen" 32)
*/}}
{{- define "api-registry.secret" -}}
{{- $ctx := .ctx -}}
{{- if .value -}}
{{- .value -}}
{{- else -}}
{{- $existing := lookup "v1" "Secret" $ctx.Release.Namespace (include "api-registry.fullname" $ctx) -}}
{{- if and $existing $existing.data (index $existing.data .key) -}}
{{- index $existing.data .key | b64dec -}}
{{- else -}}
{{- randAlphaNum (.gen | int) -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Session signing keys, as the comma-separated list config/server.ts expects.

Same preserve-on-upgrade rule as api-registry.secret, but generated as a pair:
Strapi rotates cookie signing through the list, so a second key lets the first
be retired later without invalidating every live session at once.
*/}}
{{- define "api-registry.appKeys" -}}
{{- if .Values.strapi.appKeys -}}
{{- .Values.strapi.appKeys -}}
{{- else -}}
{{- $existing := lookup "v1" "Secret" .Release.Namespace (include "api-registry.fullname" .) -}}
{{- if and $existing $existing.data (index $existing.data "APP_KEYS") -}}
{{- index $existing.data "APP_KEYS" | b64dec -}}
{{- else -}}
{{- printf "%s,%s" (randAlphaNum 32) (randAlphaNum 32) -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Public origin Strapi should advertise.

Explicit app.url wins; otherwise derive it from the first ingress host so the
common case needs no duplicated configuration. Empty when there is no ingress —
Strapi then falls back to host:port, which is correct for a port-forward.
*/}}
{{- define "api-registry.publicUrl" -}}
{{- if .Values.app.url -}}
{{- .Values.app.url -}}
{{- else if .Values.ingress.enabled -}}
{{- $host := (first .Values.ingress.hosts).host -}}
{{- if .Values.ingress.tls -}}
{{- printf "https://%s" $host -}}
{{- else -}}
{{- printf "http://%s" $host -}}
{{- end -}}
{{- end -}}
{{- end }}
