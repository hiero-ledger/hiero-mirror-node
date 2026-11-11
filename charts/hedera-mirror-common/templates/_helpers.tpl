{{/* vim: set filetype=mustache: */}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hedera-mirror-common.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hedera-mirror-common.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hedera-mirror-common.labels" -}}
{{ include "hedera-mirror-common.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ include "hedera-mirror-common.chart" . }}
{{- if .Values.labels }}
{{ toYaml .Values.labels }}
{{- end }}
{{- end -}}

{{/*
Expand the name of the chart.
*/}}
{{- define "hedera-mirror-common.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Namespace
*/}}
{{- define "hedera-mirror-common.namespace" -}}
{{- default .Release.Namespace .Values.global.namespaceOverride -}}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hedera-mirror-common.selectorLabels" -}}
app.kubernetes.io/name: {{ include "hedera-mirror-common.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "hedera-mirror-common.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "hedera-mirror-common.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Validate the ZFS disk performance values and render them as JSON for zfs-manager
*/}}
{{- define "hedera-mirror-common.zfsDiskPerformance" -}}
{{- $disk := .Values.zfs.init.disk -}}
{{- if not (or (kindIs "invalid" $disk.nodes) (kindIs "map" $disk.nodes)) -}}
{{- fail "zfs.init.disk.nodes must be a map of node ID to performance values" -}}
{{- end -}}
{{- $nodes := $disk.nodes | default dict -}}
{{- $entries := dict "coordinator" $disk.coordinator "worker" $disk.worker -}}
{{- range $nodeId, $values := $nodes -}}
{{- $_ := set $entries (printf "nodes.%s" $nodeId) $values -}}
{{- end -}}
{{- range $name, $values := $entries -}}
{{- if not (or (kindIs "invalid" $values) (kindIs "map" $values)) -}}
{{- fail (printf "zfs.init.disk.%s must be a map of performance values" $name) -}}
{{- end -}}
{{- range $field := list "provisionedIops" "provisionedThroughput" -}}
{{- $raw := get ($values | default dict) $field -}}
{{- $value := "" -}}
{{- if kindIs "string" $raw -}}
{{- $value = $raw -}}
{{- else if or (kindIs "float64" $raw) (kindIs "int64" $raw) (kindIs "int" $raw) -}}
{{- $value = printf "%d" (int64 $raw) -}}
{{- if ne (float64 $raw) (float64 (int64 $raw)) -}}
{{- $value = "invalid" -}}
{{- end -}}
{{- else if not (kindIs "invalid" $raw) -}}
{{- $value = "invalid" -}}
{{- end -}}
{{- if and $value (not (regexMatch "^[1-9][0-9]*$" $value)) -}}
{{- fail (printf "zfs.init.disk.%s.%s must be empty or a positive whole number" $name $field) -}}
{{- end -}}
{{- if and $value (ne $disk.type "hyperdisk-balanced") -}}
{{- fail (printf "zfs.init.disk.%s.%s is only supported for disk type hyperdisk-balanced" $name $field) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- pick $disk "coordinator" "nodes" "worker" | toJson -}}
{{- end -}}
