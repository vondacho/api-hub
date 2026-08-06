{{/*
Expand the name of the chart.
*/}}
{{- define "api-registry-db.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name, capped at 63 chars for the DNS label limit.
*/}}
{{- define "api-registry-db.fullname" -}}
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

{{- define "api-registry-db.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "api-registry-db.labels" -}}
helm.sh/chart: {{ include "api-registry-db.chart" . }}
{{ include "api-registry-db.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: my-api-portal
app.kubernetes.io/component: database
{{- end }}

{{- define "api-registry-db.selectorLabels" -}}
app.kubernetes.io/name: {{ include "api-registry-db.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "api-registry-db.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "api-registry-db.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Image reference, defaulting the tag to the chart appVersion.
*/}}
{{- define "api-registry-db.image" -}}
{{- printf "%s:%s" .Values.image.repository (default .Chart.AppVersion .Values.image.tag) }}
{{- end }}

{{/*
Name of the Secret holding POSTGRES_PASSWORD — either one supplied by the
operator or the one this chart creates.
*/}}
{{- define "api-registry-db.secretName" -}}
{{- default (include "api-registry-db.fullname" .) .Values.auth.existingSecret }}
{{- end }}

{{/*
The password to install with.

Precedence: an explicitly pinned value, then whatever the live Secret already
holds, then a freshly generated one. The middle case is what makes `helm
upgrade` safe — without it every upgrade would mint a new password while the
running PostgreSQL cluster kept the old one, and the registry would start
failing to authenticate.
*/}}
{{- define "api-registry-db.password" -}}
{{- if .Values.auth.password -}}
{{- .Values.auth.password -}}
{{- else -}}
{{- $existing := lookup "v1" "Secret" .Release.Namespace (include "api-registry-db.fullname" .) -}}
{{- if and $existing $existing.data (index $existing.data "POSTGRES_PASSWORD") -}}
{{- index $existing.data "POSTGRES_PASSWORD" | b64dec -}}
{{- else -}}
{{- randAlphaNum 24 -}}
{{- end -}}
{{- end -}}
{{- end }}
