resource "grafana_rule_group" "rule_group_5bf8ee7b5c98da5e" {
  disable_provenance = false
  org_id             = 1
  name               = "Grpc"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, statusCode) (rate(grpc_server_processing_duration_seconds_count{application=\\\"hedera-mirror-grpc\\\",statusCode!~\\\"CANCELLED|DEADLINE_EXCEEDED|INVALID_ARGUMENT|NOT_FOUND|OK\\\"}[5m])) / sum by (cluster, namespace, pod, statusCode) (rate(grpc_server_processing_duration_seconds_count{application=\\\"hedera-mirror-grpc\\\"}[5m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ (index $values \"A\").Value | humanizePercentage }} gRPC {{ $labels.statusCode }} error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror gRPC API error rate exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"hedera-mirror-grpc\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"hedera-mirror-grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror gRPC API CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"hedera-mirror-grpc\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"hedera-mirror-grpc\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "Mirror gRPC API database connection utilization exceeds 75%"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"hedera-mirror-grpc\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"hedera-mirror-grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror gRPC API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_grpc_consensus_latency_seconds_sum{application=\\\"hedera-mirror-grpc\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_grpc_consensus_latency_seconds_count{application=\\\"hedera-mirror-grpc\\\"}[5m])) > 15\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High latency of {{ (index $values \"A\").Value | humanizeDuration }} between the main nodes and {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror gRPC API consensus to delivery (C2MD) latency exceeds 15s"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"hedera-mirror-grpc\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"hedera-mirror-grpc\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror gRPC API memory usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"hedera-mirror-grpc\\\",level=\\\"error\\\"}[1m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "High rate of log errors"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, type) (hedera_mirror_grpc_subscribers{application=\\\"hedera-mirror-grpc\\\"}) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }} has {{ index $values \"A\" }} subscribers for {{ $labels.type }}"
      summary     = "Mirror gRPC API has no subscribers"
    }
    labels = {
      application = "hedera-mirror-grpc"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"hedera-mirror-grpc\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"hedera-mirror-grpc\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror gRPC API query latency exceeds 1s"
    }
    labels = {
      application = "hedera-mirror-grpc"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_b56cf69bf40c913c" {
  disable_provenance = false
  org_id             = 1
  name               = "Importer"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_duration_seconds_sum{application=\\\"hedera-mirror-importer\\\",type=\\\"BALANCE\\\"}[15m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_duration_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"BALANCE\\\"}[15m])) > 120\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizeDuration }} trying to parse balance stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Took longer than 2m to parse balance stream files"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_latency_seconds_sum{application=\\\"hedera-mirror-importer\\\",type=\\\"BALANCE\\\"}[15m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_latency_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"BALANCE\\\"}[15m])) > 960\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "The difference between the file timestamp and when it was processed is {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Importer balance stream processing has fallen behind"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, type, action) (rate(hedera_mirror_download_request_seconds_count{application=\\\"hedera-mirror-importer\\\",status!~\\\"^2.*\\\"}[2m])) / sum by (cluster, namespace, pod, type, action) (rate(hedera_mirror_download_request_seconds_count{application=\\\"hedera-mirror-importer\\\"}[2m]))) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizePercentage }} error rate trying to {{ if ne $labels.action \"list\" }} retrieve{{ end }} {{ $labels.action }} {{ $labels.type }} files from cloud storage for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Cloud storage error rate exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type, action) (rate(hedera_mirror_download_request_seconds_sum{application=\\\"hedera-mirror-importer\\\",status=~\\\"^2.*\\\"}[2m])) / sum by (cluster, namespace, pod, type, action) (rate(hedera_mirror_download_request_seconds_count{application=\\\"hedera-mirror-importer\\\",status=~\\\"^2.*\\\"}[2m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizeDuration }} cloud storage latency trying to {{ if ne $labels.action \"list\" }}retrieve{{ end }} {{ $labels.action }} {{ $labels.type }} files from cloud storage for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Cloud storage latency exceeds 2s"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hedera_mirror_download_stream_verification_seconds_count{application=\\\"hedera-mirror-importer\\\",success=\\\"false\\\"}[3m])) / sum by (cluster, namespace, pod, type) (rate(hedera_mirror_download_stream_verification_seconds_count{application=\\\"hedera-mirror-importer\\\"}[3m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Error rate of {{ (index $values \"A\").Value | humanizePercentage }} trying to download and verify {{ $labels.type }} stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "{{ $labels.type }} file verification error rate exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"hedera-mirror-importer\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"hedera-mirror-importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Importer CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"hedera-mirror-importer\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"hedera-mirror-importer\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "Mirror Importer database connection utilization exceeds 75%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"hedera-mirror-importer\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"hedera-mirror-importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Importer file descriptor usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"hedera-mirror-importer\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"hedera-mirror-importer\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Importer memory usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"hedera-mirror-importer\\\",level=\\\"error\\\"}[2m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "High rate of log errors"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (increase(hedera_mirror_parse_duration_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"BALANCE\\\"}[16m])) < 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "Have not processed a balance stream file in {{ $labels.namespace }} for the last 15 min"
      summary     = "Missing balance stream files"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hedera_mirror_download_signature_verification_total{application=\\\"hedera-mirror-importer\\\",status=\\\"CONSENSUS_REACHED\\\"}[2m])) / sum by (cluster, namespace, pod, type) (rate(hedera_mirror_download_signature_verification_total{application=\\\"hedera-mirror-importer\\\"}[2m])) < 0.33\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ $labels.namespace }} only able to achieve {{ (index $values \"A\").Value | humanizePercentage }} consensus during {{ $labels.type }} stream signature verification"
      summary     = "Unable to verify {{ $labels.type }} stream signatures"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(hedera_mirror_transaction_latency_seconds_count{application=\\\"hedera-mirror-importer\\\"}[5m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Record stream TPS has dropped to {{ index $values \"A\" }} for {{ $labels.namespace }}. This may be because importer is down, can't connect to cloud storage, main nodes are not uploading, error parsing the streams, no traffic, etc."
      summary     = "No transactions seen for 2m"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type) (rate(hedera_mirror_parse_duration_seconds_count{application=\\\"hedera-mirror-importer\\\",success=\\\"false\\\"}[3m])) / sum by (cluster, namespace, pod, type) (rate(hedera_mirror_parse_duration_seconds_count{application=\\\"hedera-mirror-importer\\\"}[3m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Encountered {{ (index $values \"A\").Value| humanizePercentage }} errors trying to parse {{ $labels.type }} stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Error rate parsing {{ $labels.type }} exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, type, entity) (rate(hedera_mirror_importer_publish_duration_seconds_sum{application=\\\"hedera-mirror-importer\\\"}[3m])) / sum by (cluster, namespace, pod, type, entity) (rate(hedera_mirror_importer_publish_duration_seconds_count{application=\\\"hedera-mirror-importer\\\"}[3m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "Took {{ (index $values \"A\").Value| humanizeDuration }} to publish {{ $labels.entity }}s to {{ $labels.type }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Slow {{ $labels.type }} publishing"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"hedera-mirror-importer\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"hedera-mirror-importer\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average database query latency of {{ (index $values \"A\").Value| humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Importer query latency exceeds 1s"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hedera_mirror_reconciliation{application=\\\"hedera-mirror-importer\\\"}) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "Unable to reconcile balance information for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror reconciliation job failed"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_duration_seconds_sum{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[3m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_duration_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[3m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value| humanizeDuration }} trying to parse record stream files for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Took longer than 2s to parse record stream files"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_latency_seconds_sum{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[3m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_parse_latency_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[3m])) > 20\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "The difference between the file timestamp and when it was processed is {{ (index $values \"A\").Value| humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Importer record stream processing has fallen behind"
    }
    labels = {
      application = "hedera-mirror-importer"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(hedera_mirror_stream_close_latency_seconds_sum{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(hedera_mirror_stream_close_latency_seconds_count{application=\\\"hedera-mirror-importer\\\",type=\\\"RECORD\\\"}[5m])) > 10\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} file stream should close every 2s but is actually {{ (index $values \"A\").Value | humanizeDuration }}. This could just be due to the lack of traffic in the environment, but it could potentially be something more serious to look into."
      summary     = "Record stream close interval exceeds 10s"
    }
    labels = {
      application = "hedera-mirror-importer"
      area        = "downloader"
      severity    = "critical"
      type        = "RECORD"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_f04e3f23ed64f10a" {
  disable_provenance = false
  org_id             = 1
  name               = "Monitor"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"hedera-mirror-monitor\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"hedera-mirror-monitor\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Monitor CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"hedera-mirror-monitor\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"hedera-mirror-monitor\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Monitor memory usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"hedera-mirror-monitor\\\",level=\\\"error\\\"}[2m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "High rate of log errors"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\",status!=\\\"SUCCESS\\\"}[2m])) / sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) > 0.5\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizePercentage }} error rate publishing '{{ $labels.scenario }}' scenario from {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Publish error rate exceeds 50%"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(hedera_mirror_monitor_publish_submit_seconds_sum{application=\\\"hedera-mirror-monitor\\\"}[2m])) by (cluster, namespace, pod, scenario) / sum(rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) by (cluster, namespace, pod, scenario) > 7\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizeDuration }} publish latency for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Publish latency exceeds 7s"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\",status=~\\\"(PLATFORM_NOT_ACTIVE|UNAVAILABLE)\\\"}[2m])) / sum by (cluster, namespace) (rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) > 0.33\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizePercentage }} PLATFORM_NOT_ACTIVE or UNAVAILABLE errors while attempting to publish in {{ $labels.namespace }}"
      summary     = "Platform is not active"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_submit_seconds_sum{application=\\\"hedera-mirror-monitor\\\"}[2m])) / sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_submit_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) > 0 or on () vector(0)) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    annotations = {
      description = "Publish TPS dropped to {{ index $values \"A\" }} for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Publishing stopped"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_handle_seconds_sum{application=\\\"hedera-mirror-monitor\\\"}[5m])) / sum by (cluster, namespace, pod, scenario) (rate(hedera_mirror_monitor_publish_handle_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[5m])) > 11\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "Averaging {{ (index $values \"A\").Value | humanizeDuration }} transaction latency for '{{ $labels.scenario }}' scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Submit to transaction being handled latency exceeds 11s"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(hedera_mirror_monitor_subscribe_e2e_seconds_sum{application=\\\"hedera-mirror-monitor\\\"}[2m])) by (cluster, namespace, pod, scenario, subscriber) / sum(rate(hedera_mirror_monitor_subscribe_e2e_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) by (cluster, namespace, pod, scenario, subscriber) > 14\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "Latency averaging {{ (index $values \"A\").Value | humanizeDuration }} for '{{ $labels.scenario }}' #{{ $labels.subscriber }} scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "End to end latency exceeds 14s"
    }
    labels = {
      application = "hedera-mirror-monitor"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"(sum by (cluster, namespace, pod, subscriber, scenario) (rate(hedera_mirror_monitor_subscribe_e2e_seconds_sum{application=\\\"hedera-mirror-monitor\\\"}[2m])) / sum by (cluster, namespace, pod, subscriber, scenario) (rate(hedera_mirror_monitor_subscribe_e2e_seconds_count{application=\\\"hedera-mirror-monitor\\\"}[2m])) > 0 or on () vector(0)) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "TPS dropped to {{ index $values \"A\" }} for '{{ $labels.scenario }}' #{{ $labels.subscriber }} scenario for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Subscription stopped"
    }
    labels = {
      application = "hedera-mirror-monitor"
      severity    = "critical"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_2612cf19d5434cb4" {
  disable_provenance = false
  org_id             = 1
  name               = "Rest"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(api_request_total{code=~\\\"^5..\\\",container=\\\"rest\\\"}[1m])) / sum by (cluster, namespace) (rate(api_request_total{container=\\\"rest\\\"}[1m])) > 0.01\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "REST API 5xx error rate for {{ $labels.namespace }} is {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror REST API error rate exceeds 1%"
    }
    labels = {
      application = "hedera-mirror-rest"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (nodejs_process_cpu_usage_percentage{container=\\\"rest\\\"}) / 100 > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror REST API CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-rest"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(api_all_request_total{container=\\\"rest\\\"}[3m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "REST API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "No Mirror REST API requests seen for awhile"
    }
    labels = {
      application = "hedera-mirror-rest"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(api_request_duration_milliseconds_sum{container=\\\"rest\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(api_request_duration_milliseconds_count{container=\\\"rest\\\"}[5m])) > 2000\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} is taking {{ $value }} ms to generate a response"
      summary     = "Mirror REST API request latency exceeds 2s"
    }
    labels = {
      application = "hedera-mirror-rest"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_c0dfb8053db641fc" {
  disable_provenance = false
  org_id             = 1
  name               = "RestJava"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-rest-java\\\", status=\\\"SERVER_ERROR\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-rest-java\\\"}[5m])) by (cluster, namespace, pod) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ (index $values \"A\").Value | humanizePercentage }} Java REST API error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Java REST API error rate exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-rest-java"
      severity    = "critical"
    }
    is_paused = false
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(process_cpu_usage{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) / sum(system_cpu_count{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Java REST API CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(hikaricp_connections_active{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) / sum(hikaricp_connections_max{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "Mirror Java REST API database connection utilization exceeds 75%"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"hedera-mirror-rest-java\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"hedera-mirror-rest-java\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Java REST API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(jvm_memory_used_bytes{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) / sum(jvm_memory_max_bytes{application=\\\"hedera-mirror-rest-java\\\"}) by (cluster, namespace, pod) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Java REST API memory usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(increase(logback_events_total{application=\\\"hedera-mirror-rest-java\\\", level=\\\"error\\\"}[1m])) by (cluster, namespace, pod) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "High rate of log errors"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-rest-java\\\"}[3m])) by (cluster, namespace) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Java REST API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "No Java REST API requests seen for a while"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(spring_data_repository_invocations_seconds_sum{application=\\\"hedera-mirror-rest-java\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(spring_data_repository_invocations_seconds_count{application=\\\"hedera-mirror-rest-java\\\"}[5m])) by (cluster, namespace, pod) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Java REST API query latency exceeds 1s"
    }
    labels = {
      application = "hedera-mirror-rest-java"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum(rate(http_server_requests_seconds_sum{application=\\\"hedera-mirror-rest-java\\\"}[5m])) by (cluster, namespace, pod) / sum(rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-rest-java\\\"}[5m])) by (cluster, namespace, pod) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average request latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Rest Java API request latency exceeds 2s"
    }
    labels = {
      application = "hedera-mirror-rest-java"
      severity    = "warning"
    }
    is_paused = false
  }
}
resource "grafana_rule_group" "rule_group_5f5a0f74394b7ab3" {
  disable_provenance = false
  org_id             = 1
  name               = "Web3"
  folder_uid       = "ed3d21bc-0684-4f81-a791-f2787cca85c3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-web3\\\",status=\\\"SERVER_ERROR\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-web3\\\"}[5m])) > 0.05\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "2m"
    annotations = {
      description = "{{ (index $values \"A\").Value  | humanizePercentage }} Web3 server error rate for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Web3 API error rate exceeds 5%"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_cpu_usage{application=\\\"hedera-mirror-web3\\\"}) / sum by (cluster, namespace, pod) (system_cpu_count{application=\\\"hedera-mirror-web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} CPU usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Web3 API CPU usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (hikaricp_connections_active{application=\\\"hedera-mirror-web3\\\"}) / sum by (cluster, namespace, pod) (hikaricp_connections_max{application=\\\"hedera-mirror-web3\\\"}) > 0.75\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} is using {{ (index $values \"A\").Value | humanizePercentage }} of available database connections"
      summary     = "Mirror Web3 API database connection utilization exceeds 75%"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (jvm_memory_used_bytes{application=\\\"hedera-mirror-web3\\\"}) / sum by (cluster, namespace, pod) (jvm_memory_max_bytes{application=\\\"hedera-mirror-web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} memory usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Web3 API memory usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (increase(logback_events_total{application=\\\"hedera-mirror-web3\\\",level=\\\"error\\\"}[1m])) >= 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      description = "Logs for {{ $labels.namespace }}/{{ $labels.pod }} have reached {{ index $values \"A\" }} error messages/s in a 3m period"
      summary     = "High rate of log errors"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace) (rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-web3\\\"}[3m])) <= 0\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "Web3 API has not seen any requests to {{ $labels.namespace }} for 5m"
      summary     = "No Web3 API requests seen for awhile"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_sum{application=\\\"hedera-mirror-web3\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(spring_data_repository_invocations_seconds_count{application=\\\"hedera-mirror-web3\\\"}[5m])) > 1\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average database query latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Web3 API query latency exceeds 1s"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_sum{application=\\\"hedera-mirror-web3\\\"}[5m])) / sum by (cluster, namespace, pod) (rate(http_server_requests_seconds_count{application=\\\"hedera-mirror-web3\\\"}[5m])) > 2\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "1m"
    annotations = {
      description = "High average request latency of {{ (index $values \"A\").Value | humanizeDuration }} for {{ $labels.namespace }}/{{ $labels.pod }}"
      summary     = "Mirror Web3 API request latency exceeds 2s"
    }
    labels = {
      application = "hedera-mirror-web3"
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

      datasource_uid = "grafanacloud-prom"
      model          = "{\"editorMode\":\"code\",\"expr\":\"sum by (cluster, namespace, pod) (process_files_open_files{application=\\\"hedera-mirror-web3\\\"}) / sum by (cluster, namespace, pod) (process_files_max_files{application=\\\"hedera-mirror-web3\\\"}) > 0.8\",\"instant\":true,\"intervalMs\":1000,\"legendFormat\":\"__auto\",\"maxDataPoints\":43200,\"range\":false,\"refId\":\"A\"}"
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      description = "{{ $labels.namespace }}/{{ $labels.pod }} file descriptor usage reached {{ (index $values \"A\").Value | humanizePercentage }}"
      summary     = "Mirror Web3 API file descriptor usage exceeds 80%"
    }
    labels = {
      application = "hedera-mirror-web3"
      area        = "resource"
      severity    = "critical"
    }
    is_paused = false
  }
}

