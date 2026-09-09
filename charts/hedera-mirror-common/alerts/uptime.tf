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
