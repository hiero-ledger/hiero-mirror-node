{{/* vim: set filetype=mustache: */}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hedera-mirror.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Constructs the database name.
*/}}
{{- define "hedera-mirror.stackgres" -}}
{{- printf "%s-citus" (include "hedera-mirror.fullname" .) -}}
{{- end -}}

{{/*
Constructs the database host that should be used by all components.
*/}}
{{- define "hedera-mirror.db" -}}
{{- if .Values.db.host -}}
{{- tpl .Values.db.host . -}}
{{- else if and .Values.postgresql.enabled (gt (.Values.postgresql.pgpool.replicaCount | int) 0) -}}
{{- include "postgresql-ha.pgpool" .Subcharts.postgresql -}}
{{- else if .Values.postgresql.enabled -}}
{{- include "postgresql-ha.postgresql" .Subcharts.postgresql -}}
{{ else if .Values.stackgres.enabled -}}
{{- printf "%s-reads" (include "hedera-mirror.stackgres" .) -}}
{{- end -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hedera-mirror.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains .Release.Name $name  -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hedera-mirror.labels" -}}
{{ include "hedera-mirror.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: hedera-mirror-node
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ include "hedera-mirror.chart" . }}
{{- if .Values.labels }}
{{ toYaml .Values.labels }}
{{- end }}
{{- end -}}

{{/*
Expand the name of the chart.
*/}}
{{- define "hedera-mirror.name" -}}
{{- if .Values.global.useReleaseForNameLabel -}}
{{- .Release.Name -}}
{{- else -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
Namespace
*/}}
{{- define "hedera-mirror.namespace" -}}
{{- default .Release.Namespace .Values.global.namespaceOverride -}}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hedera-mirror.selectorLabels" -}}
app.kubernetes.io/component: hedera-mirror
app.kubernetes.io/name: {{ include "hedera-mirror.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "hedera-mirror.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "hedera-mirror.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Acceptance Test Labels
*/}}
{{- define "hedera-mirror-acceptance.labels" -}}
{{ include "hedera-mirror.labels" . }}
{{- end -}}
