{{- define "bgstore.image" -}}
{{- $image := index .Values.images .component -}}
{{- if $image.digest -}}
{{ $image.repository }}@{{ $image.digest }}
{{- else -}}
{{ $image.repository }}:{{ $image.tag }}
{{- end -}}
{{- end -}}

{{- define "bgstore.labels" -}}
app.kubernetes.io/part-of: bgstore
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}
