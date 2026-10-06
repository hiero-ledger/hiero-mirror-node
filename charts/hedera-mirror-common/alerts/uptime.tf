###############################################################################
# Uptime synthetic checks and alert rules
#
# Check targets are placeholders. 
# The sync pipeline substitutes the real values before running terraform.
###############################################################################

data "grafana_synthetic_monitoring_probes" "uptime" {}

resource "grafana_folder" "uptime" {
  uid   = "afwyyg26ibz0gd"
  title = "Uptime"
}

###############################################################################
# Synthetic Monitoring checks
###############################################################################

resource "grafana_synthetic_monitoring_check" "mainnet_uptime" {
  job                = "mainnet uptime"
  target             = "__MAINNET_UPTIME_TARGET__"
  enabled            = true
  frequency          = 60000
  timeout            = 10000
  basic_metrics_only = true
  alert_sensitivity  = "none"

  probes = [
    data.grafana_synthetic_monitoring_probes.uptime.probes.Singapore,
    data.grafana_synthetic_monitoring_probes.uptime.probes.Frankfurt,
    data.grafana_synthetic_monitoring_probes.uptime.probes.NorthVirginia,
  ]

  labels = {
    environment = "mainnet"
    team        = "mirror-node"
  }

  settings {
    http {
      method              = "GET"
      ip_version          = "V4"
      no_follow_redirects = false
      fail_if_ssl         = false
      fail_if_not_ssl     = false
    }
  }
}

resource "grafana_synthetic_monitoring_check" "testnet_uptime" {
  job                = "testnet uptime"
  target             = "__TESTNET_UPTIME_TARGET__"
  enabled            = true
  frequency          = 60000
  timeout            = 10000
  basic_metrics_only = true
  alert_sensitivity  = "none"

  probes = [
    data.grafana_synthetic_monitoring_probes.uptime.probes.Singapore,
    data.grafana_synthetic_monitoring_probes.uptime.probes.Frankfurt,
    data.grafana_synthetic_monitoring_probes.uptime.probes.NorthVirginia,
  ]

  labels = {
    environment = "testnet"
    team        = "mirror-node"
  }

  settings {
    http {
      method              = "GET"
      ip_version          = "V4"
      no_follow_redirects = false
      fail_if_ssl         = false
      fail_if_not_ssl     = false
    }
  }
}

###############################################################################
# Alert rules
#
# The comparison needs to be in expression B, not the PromQL.
# 'probe_success == 0' causes healthy series to drop, 
# leaving a passing check in 'No Data'.
###############################################################################

resource "grafana_rule_group" "uptime_checks" {
  disable_provenance = false
  name               = "Checks"
  folder_uid         = grafana_folder.uptime.uid
  interval_seconds   = 60

  rule {
    name      = "MainnetUptimeFailure"
    condition = "B"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "grafanacloud-prom"
      model = jsonencode({
        editorMode    = "code"
        expr          = "probe_success{job=\"mainnet uptime\"}"
        instant       = true
        intervalMs    = 1000
        legendFormat  = "__auto"
        maxDataPoints = 43200
        range         = false
        refId         = "A"
      })
    }

    data {
      ref_id = "B"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        conditions = [
          {
            evaluator = {
              params = [1]
              type   = "lt"
            }
            operator = {
              type = "and"
            }
            query = {
              params = ["A"]
            }
            reducer = {
              params = []
              type   = "last"
            }
            type = "query"
          }
        ]
        intervalMs    = 1000
        maxDataPoints = 43200
        refId         = "B"
        type          = "classic_conditions"
      })
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      summary = "An uptime check on mainnet mirror node is failing"
    }
    labels = {
      area         = "uptime"
      env_category = "production"
      environment  = "mainnet"
      severity     = "critical"
    }
    is_paused = false
  }

  rule {
    name      = "TestnetUptimeFailure"
    condition = "B"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "grafanacloud-prom"
      model = jsonencode({
        editorMode    = "code"
        expr          = "probe_success{job=\"testnet uptime\"}"
        instant       = true
        intervalMs    = 1000
        legendFormat  = "__auto"
        maxDataPoints = 43200
        range         = false
        refId         = "A"
      })
    }

    data {
      ref_id = "B"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        conditions = [
          {
            evaluator = {
              params = [1]
              type   = "lt"
            }
            operator = {
              type = "and"
            }
            query = {
              params = ["A"]
            }
            reducer = {
              params = []
              type   = "last"
            }
            type = "query"
          }
        ]
        intervalMs    = 1000
        maxDataPoints = 43200
        refId         = "B"
        type          = "classic_conditions"
      })
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "3m"
    annotations = {
      summary = "An uptime check on testnet mirror node is failing"
    }
    labels = {
      area         = "uptime"
      env_category = "production"
      environment  = "testnet"
      severity     = "critical"
    }
    is_paused = false
  }
}

###############################################################################
# Individual cluster alert rules
#
# The rules below alert per cluster and are built differently from the
# uptime rules above on purpose.
# They allow alerting on each individual cluster in dual-cluster envs.
###############################################################################

locals {
  individual_clusters = join("|", ["mainnet-eu", "mainnet-na", "testnet-eu", "testnet-na"])
}

resource "grafana_rule_group" "uptime_clusters" {
  disable_provenance = false
  name               = "Clusters"
  folder_uid         = grafana_folder.uptime.uid
  interval_seconds   = 60

  rule {
    name      = "ClusterHealthCheckFailing"
    condition = "C"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model = jsonencode({
        editorMode    = "code"
        expr          = "sum by (cluster, env_category) (rate(traefik_service_requests_total{cluster=~\"${local.individual_clusters}\",service=~\".+-monitor-[0-9]+@kubernetes\",code=\"200\"}[2m])) or 0 * sum by (cluster, env_category) (rate(traefik_service_requests_total{cluster=~\"${local.individual_clusters}\",service=~\".+-monitor-[0-9]+@kubernetes\"}[2m]))"
        instant       = true
        intervalMs    = 1000
        legendFormat  = "__auto"
        maxDataPoints = 43200
        range         = false
        refId         = "A"
      })
    }

    data {
      ref_id = "B"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        expression    = "A"
        intervalMs    = 1000
        maxDataPoints = 43200
        reducer       = "last"
        refId         = "B"
        settings = {
          mode = "dropNN"
        }
        type = "reduce"
      })
    }

    data {
      ref_id = "C"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        conditions = [
          {
            evaluator = {
              params = [0.001]
              type   = "lt"
            }
          }
        ]
        expression    = "B"
        intervalMs    = 1000
        maxDataPoints = 43200
        refId         = "C"
        type          = "threshold"
      })
    }

    no_data_state  = "NoData"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      summary = "Cluster {{ $labels.cluster }} is out of load balancer rotation: all health-checks have failed."
    }
    labels = {
      area         = "uptime"
      env_category = "production"
      severity     = "critical"
    }
    is_paused = false
  }

  rule {
    name      = "ClusterNotReporting"
    condition = "C"

    data {
      ref_id = "A"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = var.prometheus_datasource_uid
      model = jsonencode({
        editorMode    = "code"
        expr          = "time() - max by (cluster, env_category) ((timestamp(up{cluster=~\"${local.individual_clusters}\",job=~\".*traefik.*\"}) and up{cluster=~\"${local.individual_clusters}\",job=~\".*traefik.*\"} == 1) or max_over_time((timestamp(up{cluster=~\"${local.individual_clusters}\",job=~\".*traefik.*\"}) and up{cluster=~\"${local.individual_clusters}\",job=~\".*traefik.*\"} == 1)[1d:1m]))"
        instant       = true
        intervalMs    = 1000
        legendFormat  = "__auto"
        maxDataPoints = 43200
        range         = false
        refId         = "A"
      })
    }

    data {
      ref_id = "B"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        expression    = "A"
        intervalMs    = 1000
        maxDataPoints = 43200
        reducer       = "last"
        refId         = "B"
        settings = {
          mode = "dropNN"
        }
        type = "reduce"
      })
    }

    data {
      ref_id = "C"

      relative_time_range {
        from = 600
        to   = 0
      }

      datasource_uid = "__expr__"
      model = jsonencode({
        conditions = [
          {
            evaluator = {
              params = [120]
              type   = "gt"
            }
          }
        ]
        expression    = "B"
        intervalMs    = 1000
        maxDataPoints = 43200
        refId         = "C"
        type          = "threshold"
      })
    }

    no_data_state  = "Alerting"
    exec_err_state = "Error"
    for            = "5m"
    annotations = {
      summary = "Cluster {{ $labels.cluster }} has failed to scrape Traefik for {{ humanizeDuration (index $values \"B\").Value }}, load balancer health cannot be evaluated."
    }
    labels = {
      area         = "uptime"
      env_category = "production"
      severity     = "critical"
    }
    is_paused = false
  }
}
