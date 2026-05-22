###############################################################################
# Shared configuration
###############################################################################

resource "grafana_folder" "mirror" {
  title = "Mirror"
  prevent_destroy_if_not_empty = true
}

variable "prometheus_datasource_uid" {
  type        = string
  default     = "grafanacloud-prom"
  description = "UID of the Prometheus datasource to query."
}

###############################################################################
# Notification policies
###############################################################################

variable "prod_slack_contact_point" {
  type        = string
  default     = "mirror-node-prod"
  description = "Name of the manually-created Grafana contact point that posts to the production Slack channel."
}

variable "nonprod_slack_contact_point" {
  type        = string
  default     = "mirror-node-preprod"
  description = "Name of the manually-created Grafana contact point that posts to the non-prod Slack channel."
}

resource "grafana_notification_policy" "root" {
  contact_point = "grafana-default-email"
  group_by      = ["alertname", "cluster", "namespace"]

  group_wait      = "30s"
  group_interval  = "5m"
  repeat_interval = "4h"

  policy {
    contact_point = var.prod_slack_contact_point
    matcher {
      label = "severity"
      match = "=~"
      value = "warning|critical"
    }
    matcher {
      label = "env_category"
      match = "="
      value = "production"
    }
  }

  policy {
    contact_point = var.nonprod_slack_contact_point
    matcher {
      label = "severity"
      match = "=~"
      value = "warning|critical"
    }
    matcher {
      label = "env_category"
      match = "="
      value = "non-prod"
    }
    matcher {
      label = "namespace"
      match = "!~"
      value = "performance-citus"
    }
    matcher {
      label = "cluster"
      match = "!~"
      value = "staging-lg|staging-sm|staging-council"
    }
  }
}

###############################################################################
# Alert rules
###############################################################################

resource "grafana_rule_group" "rule_group_grpc" {
  disable_provenance = false
  name               = "Grpc"
  folder_uid         = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "GrpcErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, statusCode) (rate(grpc_server_processing_duration_seconds_count{application=\\\"grpc\\\",statusCode!~\\\"CANCELLED|DEADLINE_EXCEEDED|INVALID_ARGUMENT|NOT_FOUND|OK\\\"}[5m])) / sum by (cluster, namespace, pod, statusCode) (rate(grpc_server_processing_duration_seconds_count{application=\\\"grpc\\\"}[5m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ (index $values \"A\").Value | humanizePercentage }} gRPC {{ $labels.statusCode }} error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API error rate exceeds 5%"
    }
    labels = {
      application = "grpc"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcHighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"grpc\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API CPU usage exceeds 80%"
    }
    labels = {
      application = "grpc"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcHighDBConnections"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"grpc\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"grpc\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API database connection utilization exceeds 75%"
    }
    labels = {
      application = "grpc"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcHighFileDescriptors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"grpc\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "grpc"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcHighLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_grpc_consensus_latency_seconds_sum{application=\\\"grpc\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_grpc_consensus_latency_seconds_count{application=\\\"grpc\\\"}[5m])) > 15\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High latency of {{ (index $values \"A\").Value | humanizeDuration }} between the main nodes and {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API consensus to delivery (C2MD) latency exceeds 15s"
    }
    labels = {
      application = "grpc"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcHighMemory"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"grpc\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API memory usage exceeds 80%"
    }
    labels = {
      application = "grpc"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcLogErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"grpc\\\",level=\\\"error\\\"}[1m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "[{{ $labels.cluster }}] High rate of log errors"
    }
    labels = {
      application = "grpc"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcNoSubscribers"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, type) (hiero_mirror_grpc_subscribers{application=\\\"grpc\\\"}) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }} has {{ index $values \"A\" }} subscribers for {{ $labels.type }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API has no subscribers"
    }
    labels = {
      application = "grpc"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "GrpcQueryLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"grpc\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"grpc\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror gRPC API query latency exceeds 1s"
    }
    labels = {
      application = "grpc"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_importer" {
  disable_provenance = false
  name               = "Importer"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "ImporterBalanceParseLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_duration_seconds_sum{application=\\\"importer\\\",type=\\\"BALANCE\\\"}[15m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_duration_seconds_count{application=\\\"importer\\\",type=\\\"BALANCE\\\"}[15m])) > 120\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizeDuration }} trying to parse balance stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Took longer than 2m to parse balance stream files"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "BALANCE"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterBalanceStreamFallenBehind"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_latency_seconds_sum{application=\\\"importer\\\",type=\\\"BALANCE\\\"}[15m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_latency_seconds_count{application=\\\"importer\\\",type=\\\"BALANCE\\\"}[15m])) > 960\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: The difference between the file timestamp and when it was processed is {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer balance stream processing has fallen behind"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "BALANCE"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterCloudStorageErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, type, action) (rate(hiero_mirror_importer_stream_request_seconds_count{application=\\\"importer\\\",status!~\\\"^2.*\\\"}[2m])) / sum by (cluster, namespace, pod, type, action) (rate(hiero_mirror_importer_stream_request_seconds_count{application=\\\"importer\\\"}[2m]))) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizePercentage }} error rate trying to {{ if ne $labels.action \"list\" }} retrieve{{ end }} {{ $labels.action }} {{ $labels.type }} files from cloud storage for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Cloud storage error rate exceeds 5%"
    }
    labels = {
      application = "importer"
      area        = "cloud"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterCloudStorageLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type, action) (rate(hiero_mirror_importer_stream_request_seconds_sum{application=\\\"importer\\\",status=~\\\"^2.*\\\"}[2m])) / sum by (cluster, namespace, pod, type, action) (rate(hiero_mirror_importer_stream_request_seconds_count{application=\\\"importer\\\",status=~\\\"^2.*\\\"}[2m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizeDuration }} cloud storage latency trying to {{ if ne $labels.action \"list\" }}retrieve{{ end }} {{ $labels.action }} {{ $labels.type }} files from cloud storage for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Cloud storage latency exceeds 2s"
    }
    labels = {
      application = "importer"
      area        = "cloud"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterFileVerificationErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_stream_verification_seconds_count{application=\\\"importer\\\",success=\\\"false\\\"}[3m])) / sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_stream_verification_seconds_count{application=\\\"importer\\\"}[3m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Error rate of {{ (index $values \"A\").Value | humanizePercentage }} trying to download and verify {{ $labels.type }} stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] {{ $labels.type }} file verification error rate exceeds 5%"
    }
    labels = {
      application = "importer"
      area        = "downloader"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterHighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"importer\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer CPU usage exceeds 80%"
    }
    labels = {
      application = "importer"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterHighDBConnections"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"importer\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"importer\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "[{{ $labels.cluster }}] Mirror Importer database connection utilization exceeds 75%"
    }
    labels = {
      application = "importer"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterHighFileDescriptors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"importer\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer file descriptor usage exceeds 80%"
    }
    labels = {
      application = "importer"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterHighMemory"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"importer\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer memory usage exceeds 80%"
    }
    labels = {
      application = "importer"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterLogErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"importer\\\",level=\\\"error\\\"}[2m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "[{{ $labels.cluster }}] High rate of log errors"
    }
    labels = {
      application = "importer"
      area        = "log"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterNoBalanceFile"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (increase(hiero_mirror_importer_parse_duration_seconds_count{application=\\\"importer\\\",type=\\\"BALANCE\\\"}[16m])) < 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Have not processed a balance stream file in {{ $labels.namespace }} for the last 15 min"
      summary     = "[{{ $labels.cluster }}] Missing balance stream files"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "BALANCE"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterNoConsensus"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_stream_signature_verification_total{application=\\\"importer\\\",status=\\\"CONSENSUS_REACHED\\\"}[2m])) / sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_stream_signature_verification_total{application=\\\"importer\\\"}[2m])) < 0.33\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }} only able to achieve {{ (index $values \"A\").Value | humanizePercentage }} consensus during {{ $labels.type }} stream signature verification"
      summary     = "[{{ $labels.cluster }}] Unable to verify {{ $labels.type }} stream signatures"
    }
    labels = {
      application = "importer"
      area        = "downloader"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterNoTransactions"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(hiero_mirror_importer_transaction_latency_seconds_count{application=\\\"importer\\\"}[5m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Record stream TPS has dropped to {{ index $values \"A\" }} for {{ $labels.namespace }}. This may be because importer is down, can't connect to cloud storage, main nodes are not uploading, error parsing the streams, no traffic, etc."
      summary     = "[{{ $labels.cluster }}] No transactions seen for 2m"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "RECORD"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterParseErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_parse_duration_seconds_count{application=\\\"importer\\\",success=\\\"false\\\"}[3m])) / sum by (cluster, namespace, pod, type) (rate(hiero_mirror_importer_parse_duration_seconds_count{application=\\\"importer\\\"}[3m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Encountered {{ (index $values \"A\").Value| humanizePercentage }} errors trying to parse {{ $labels.type }} stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Error rate parsing {{ $labels.type }} exceeds 5%"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterPublishLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type, entity) (rate(hiero_mirror_importer_publish_duration_seconds_sum{application=\\\"importer\\\"}[3m])) / sum by (cluster, namespace, pod, type, entity) (rate(hiero_mirror_importer_publish_duration_seconds_count{application=\\\"importer\\\"}[3m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: Took {{ (index $values \"A\").Value| humanizeDuration }} to publish {{ $labels.entity }}s to {{ $labels.type }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Slow {{ $labels.type }} publishing"
    }
    labels = {
      application = "importer"
      area        = "publisher"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterQueryLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"importer\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"importer\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average database query latency of {{ (index $values \"A\").Value| humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer query latency exceeds 1s"
    }
    labels = {
      application = "importer"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterReconciliationFailed"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hiero_mirror_importer_reconciliation{application=\\\"importer\\\"}) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: Unable to reconcile balance information for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror reconciliation job failed"
    }
    labels = {
      application = "importer"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterRecordParseLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_duration_seconds_sum{application=\\\"importer\\\",type=\\\"RECORD\\\"}[3m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_duration_seconds_count{application=\\\"importer\\\",type=\\\"RECORD\\\"}[3m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value| humanizeDuration }} trying to parse record stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Took longer than 2s to parse record stream files"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "RECORD"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterRecordStreamFallenBehind"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_latency_seconds_sum{application=\\\"importer\\\",type=\\\"RECORD\\\"}[3m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_parse_latency_seconds_count{application=\\\"importer\\\",type=\\\"RECORD\\\"}[3m])) > 20\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: The difference between the file timestamp and when it was processed is {{ (index $values \"A\").Value| humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Importer record stream processing has fallen behind"
    }
    labels = {
      application = "importer"
      area        = "parser"
      severity    = "critical"
      type        = "RECORD"
    }
    is_paused = false
  }
  rule {
    name      = "ImporterStreamCloseInterval"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_stream_close_latency_seconds_sum{application=\\\"importer\\\",type=\\\"RECORD\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(hiero_mirror_importer_stream_close_latency_seconds_count{application=\\\"importer\\\",type=\\\"RECORD\\\"}[5m])) > 10\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} file stream should close every 2s but is actually {{ (index $values \"A\").Value | humanizeDuration }}. This could just be due to the lack of traffic in the environment, but it could potentially be something more serious to look into."
      summary     = "[{{ $labels.cluster }}] Record stream close interval exceeds 10s"
    }
    labels = {
      application = "importer"
      area        = "downloader"
      severity    = "critical"
      type        = "RECORD"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_monitor" {
  disable_provenance = false
  name               = "Monitor"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "MonitorHighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"monitor\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"monitor\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Monitor CPU usage exceeds 80%"
    }
    labels = {
      application = "monitor"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorHighMemory"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"monitor\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"monitor\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Monitor memory usage exceeds 80%"
    }
    labels = {
      application = "monitor"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorLogErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"monitor\\\",level=\\\"error\\\"}[2m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "[{{ $labels.cluster }}] High rate of log errors"
    }
    labels = {
      application = "monitor"
      area        = "log"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorPublishErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\",status!=\\\"SUCCESS\\\"}[2m])) / sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\"}[2m])) > 0.5\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizePercentage }} error rate publishing '{{ $labels.scenario }}' scenario from {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Publish error rate exceeds 50%"
    }
    labels = {
      application = "monitor"
      mode        = "publish"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorPublishLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(hiero_mirror_monitor_publish_submit_seconds_sum{application=\\\"monitor\\\"}[2m])) by (cluster, namespace, pod, scenario) / sum(rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\"}[2m])) by (cluster, namespace, pod, scenario) > 7\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizeDuration }} publish latency for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Publish latency exceeds 7s"
    }
    labels = {
      application = "monitor"
      mode        = "publish"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorPublishPlatformNotActive"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\",status=~\\\"(PLATFORM_NOT_ACTIVE|UNAVAILABLE)\\\"}[2m])) / sum by (cluster, namespace) (rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\"}[2m])) > 0.33\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizePercentage }} PLATFORM_NOT_ACTIVE or UNAVAILABLE errors while attempting to publish in {{ $labels.namespace }}"
      summary     = "[{{ $labels.cluster }}] Platform is not active"
    }
    labels = {
      application = "monitor"
      mode        = "publish"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorPublishStopped"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_submit_seconds_sum{application=\\\"monitor\\\"}[2m])) / sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_submit_seconds_count{application=\\\"monitor\\\"}[2m])) > 0 or on () vector(0)) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    annotations = {
      description = "{{ $labels.cluster }}: Publish TPS dropped to {{ index $values \"A\" }} for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Publishing stopped"
    }
    labels = {
      application = "monitor"
      mode        = "publish"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorPublishToHandleLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_handle_seconds_sum{application=\\\"monitor\\\"}[5m])) / sum by (cluster, namespace, pod, scenario) (rate(hiero_mirror_monitor_publish_handle_seconds_count{application=\\\"monitor\\\"}[5m])) > 11\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Averaging {{ (index $values \"A\").Value | humanizeDuration }} transaction latency for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Submit to transaction being handled latency exceeds 11s"
    }
    labels = {
      application = "monitor"
      mode        = "publish"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorSubscribeLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(hiero_mirror_monitor_subscribe_e2e_seconds_sum{application=\\\"monitor\\\"}[2m])) by (cluster, namespace, pod, scenario, subscriber) / sum(rate(hiero_mirror_monitor_subscribe_e2e_seconds_count{application=\\\"monitor\\\"}[2m])) by (cluster, namespace, pod, scenario, subscriber) > 14\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Latency averaging {{ (index $values \"A\").Value | humanizeDuration }} for '{{ $labels.scenario }}' #{{ $labels.subscriber }} scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] End to end latency exceeds 14s"
    }
    labels = {
      application = "monitor"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "MonitorSubscribeStopped"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, subscriber, scenario) (rate(hiero_mirror_monitor_subscribe_e2e_seconds_sum{application=\\\"monitor\\\"}[2m])) / sum by (cluster, namespace, pod, subscriber, scenario) (rate(hiero_mirror_monitor_subscribe_e2e_seconds_count{application=\\\"monitor\\\"}[2m])) > 0 or on () vector(0)) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: TPS dropped to {{ index $values \"A\" }} for '{{ $labels.scenario }}' #{{ $labels.subscriber }} scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Subscription stopped"
    }
    labels = {
      application = "monitor"
      severity    = "critical"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_rest" {
  disable_provenance = false
  name               = "Rest"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "RestErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(api_request_total{code=~\\\"^5..\\\",container=\\\"rest\\\"}[1m])) / sum by (cluster, namespace) (rate(api_request_total{container=\\\"rest\\\"}[1m])) > 0.01\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: REST API 5xx error rate for {{ $labels.namespace }} is {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror REST API error rate exceeds 1%"
    }
    labels = {
      application = "rest"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestHighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (nodejs_process_cpu_usage_percentage{container=\\\"rest\\\"}) / 100 > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror REST API CPU usage exceeds 80%"
    }
    labels = {
      application = "rest"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestNoRequests"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(api_all_request_total{container=\\\"rest\\\"}[3m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: REST API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "[{{ $labels.cluster }}] No Mirror REST API requests seen for awhile"
    }
    labels = {
      application = "rest"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "RestRequestLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(api_request_duration_milliseconds_sum{container=\\\"rest\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(api_request_duration_milliseconds_count{container=\\\"rest\\\"}[5m])) > 2000\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} is taking {{ $value }} ms to generate a response"
      summary     = "[{{ $labels.cluster }}] Mirror REST API request latency exceeds 2s"
    }
    labels = {
      application = "rest"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_restjava" {
  disable_provenance = false
  name               = "RestJava"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "RestJavaErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"rest-java\\\", status=\\\"SERVER_ERROR\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(http_server_requests_seconds_count{application=\\\"rest-java\\\"}[5m])) by (cluster, namespace, pod) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ (index $values \"A\").Value | humanizePercentage }} Java REST API error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API error rate exceeds 5%"
    }
    labels = {
      application = "rest-java"
      severity    = "critical"
    }
    is_paused = true
  }
  rule {
    name      = "RestJavaHighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(process_cpu_usage{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) / sum(system_cpu_count{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API CPU usage exceeds 80%"
    }
    labels = {
      application = "rest-java"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaHighDBConnections"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(hikaricp_connections_active{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) / sum(hikaricp_connections_max{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API database connection utilization exceeds 75%"
    }
    labels = {
      application = "rest-java"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaHighFileDescriptors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"rest-java\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"rest-java\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "rest-java"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaHighMemory"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) / sum(jvm_memory_max_bytes{application=\\\"rest-java\\\"}) by (cluster, namespace, pod) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API memory usage exceeds 80%"
    }
    labels = {
      application = "rest-java"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaLogErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(increase(logback_events_total{application=\\\"rest-java\\\", level=\\\"error\\\"}[1m])) by (cluster, namespace, pod) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "[{{ $labels.cluster }}] High rate of log errors"
    }
    labels = {
      application = "rest-java"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaNoRequests"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"rest-java\\\"}[3m])) by (cluster, namespace) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Java REST API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "[{{ $labels.cluster }}] No Java REST API requests seen for a while"
    }
    labels = {
      application = "rest-java"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaQueryLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(spring_data_repository_invocations_seconds_sum{application=\\\"rest-java\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(spring_data_repository_invocations_seconds_count{application=\\\"rest-java\\\"}[5m])) by (cluster, namespace, pod) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Java REST API query latency exceeds 1s"
    }
    labels = {
      application = "rest-java"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "RestJavaRequestLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_sum{application=\\\"rest-java\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(http_server_requests_seconds_count{application=\\\"rest-java\\\"}[5m])) by (cluster, namespace, pod) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average request latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Rest Java API request latency exceeds 2s"
    }
    labels = {
      application = "rest-java"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_web3" {
  disable_provenance = false
  name               = "Web3"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "Web3Errors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"web3\\\",status=\\\"SERVER_ERROR\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"web3\\\"}[5m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ (index $values \"A\").Value  | humanizePercentage }} Web3 server error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API error rate exceeds 5%"
    }
    labels = {
      application = "web3"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "Web3HighCPU"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"web3\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API CPU usage exceeds 80%"
    }
    labels = {
      application = "web3"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "Web3HighDBConnections"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"web3\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"web3\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API database connection utilization exceeds 75%"
    }
    labels = {
      application = "web3"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "Web3HighMemory"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"web3\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API memory usage exceeds 80%"
    }
    labels = {
      application = "web3"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "Web3LogErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"web3\\\",level=\\\"error\\\"}[1m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "{{ $labels.cluster }}: Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "[{{ $labels.cluster }}] High rate of log errors"
    }
    labels = {
      application = "web3"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "Web3NoRequests"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(http_server_requests_seconds_count{application=\\\"web3\\\"}[3m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Web3 API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "[{{ $labels.cluster }}] No Web3 API requests seen for awhile"
    }
    labels = {
      application = "web3"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "Web3QueryLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"web3\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"web3\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API query latency exceeds 1s"
    }
    labels = {
      application = "web3"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "Web3RequestLatency"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_sum{application=\\\"web3\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"web3\\\"}[5m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: High average request latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API request latency exceeds 2s"
    }
    labels = {
      application = "web3"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "Web3HighFileDescriptors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"web3\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: {{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "[{{ $labels.cluster }}] Mirror Web3 API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "web3"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
}

resource "grafana_rule_group" "rule_group_database" {
  disable_provenance = false
  name               = "Database"
  folder_uid       = grafana_folder.mirror.uid
  interval_seconds = 60

  rule {
    name      = "DatabaseInstanceDown"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pg_up) == 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Postgres has not been responding for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Postgres server instance is down"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseExporterErrors"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pg_exporter_last_scrape_error) == 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "OK"
    exec_err_state = "OK"
    for            = "10m"
    annotations = {
      description = "{{ $labels.cluster }}: postgres-exporter is not running or is showing errors for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Postgres exporter is down or showing errors"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseReplicationLagSizeTooLarge"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pg_replication_status_lag_size) > 1e+09\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: Replication lag on {{ $labels.namespace }}/{{ $labels.pod }} is currently {{ (index $values \"A\").Value | humanize1024 }}B behind the leader"
      summary     = "[{{ $labels.cluster }}] Postgres replication lag size exceeds 1GB"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseInactiveReplicationSlots"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pg_replication_slots_active) == 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "30m"
    annotations = {
      description = "{{ $labels.cluster }}: Inactive replication slots on {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "[{{ $labels.cluster }}] Postgres has inactive replication slots"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseDemotedNode"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pg_replication_is_replica) == 1 and sum by (cluster, namespace, pod) (changes(pg_replication_is_replica[2m])) > 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: Instance {{ $labels.namespace }}/{{ $labels.pod }} has been demoted to a replica"
      summary     = "[{{ $labels.cluster }}] Postgres node demoted to replica"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "warning"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseWaitingClients"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pgbouncer_show_pools_cl_waiting) > 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "OK"
    exec_err_state = "OK"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: PgBouncer {{ $labels.namespace }}/{{ $labels.pod }} has {{ (index $values \"A\").Value }} waiting clients"
      summary     = "[{{ $labels.cluster }}] PgBouncer has waiting clients"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseQueryTimeTooHigh"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (pgbouncer_show_stats_avg_query_time) > 3e+06\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.cluster }}: PgBouncer {{ $labels.namespace }}/{{ $labels.pod }} average query duration is {{ (index $values \"A\").Value }} microseconds, exceeding 3s"
      summary     = "[{{ $labels.cluster }}] PgBouncer average query duration exceeds 3s"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
  rule {
    name      = "DatabaseStorageFull"
    condition = "A"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, persistentvolumeclaim) (kubelet_volume_stats_used_bytes{node=~\\\".*(worker|coord).*\\\"}) / sum by (cluster, namespace, persistentvolumeclaim) (kubelet_volume_stats_capacity_bytes{node=~\\\".*(worker|coord).*\\\"}) >= 0.80\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.cluster }}: Storage for {{ $labels.namespace }}/{{ $labels.persistentvolumeclaim }} is {{ (index $values \"A\").Value | humanizePercentage }} full"
      summary     = "[{{ $labels.cluster }}] Database storage exceeds 80% capacity"
    }
    labels = {
      application = "hedera-mirror-common"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
}
