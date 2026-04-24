# Grafana-Cloud Alert Rules

`charts/hedera-mirror-common/alerts/rules.tf` is the source of truth for the Grafana Cloud alert rules.
A Jenkins pipeline in the devops infra repo polls this file via the GitHub API every 5 minutes, runs `terraform plan` against a Terraform Cloud workspace, and applies anything that changed.

## Making a change

- Edit `rules.tf`
- PR your changes
- Merge the PR
- Grab a snack and check back in 5 minutes

Any changes will be applied to Grafana Cloud within up to ~5 minutes.

## Don'ts

- **Don't re-export the file from Grafana.** The export emits fresh UIDs that won't line up with live terraform state. Any changes are to be done only by editing and merging `rules.tf`.

## HCL alert rules file

- `charts/hedera-mirror-common/alerts/rules.tf` — the 6 rule groups (Grpc, Importer, Monitor, Rest, RestJava, Web3), each as a `grafana_rule_group` resource. Originally exported from Grafana Cloud.

## Files on devops' side

- `terraform/deployments/mirrornode-grafana-alert-rules/` — Terraform workspace with provider config, backend, and variables. The `rules.tf` you edit gets pulled into this directory during the Jenkins pipeline runtime.
- `pipelines/sync-mirrornode-grafana-alert-rules.Jenkinsfile` — the Jenkins pipeline, scheduled to run every 5 minutes.
- `pipelines/scripts/sync-mirrornode-grafana-alert-rules.sh` — the sync script (called by the pipeline): fetches `rules.tf` via a GitHub API call, runs `terraform plan`, and `terraform apply` only if the plan has returned with differences.
