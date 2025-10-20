## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on relevant [issues](https://github.com/hiero-ledger/hiero-mirror-node/issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open for [milestone](https://github.com/hiero-ledger/hiero-mirror-node/issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.139.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Verify HAPI protobuf dependency doesn't contain any unsupported fields or messages
- [ ] Automated Kubernetes deployment to integration successful
- [ ] Integration HAPI version is up to date with previewnet
- [ ] No abnormal exceptions in importer logs in integration since the first SNAPSHOT
- [ ] No breaking changes in API schema or behavior
- [ ] Tag release

## Release Candidates

### Previewnet

Deployed automatically on every tag.

- [ ] Deployed
- [ ] Helm Controller logs show successful reconciliation check
- [ ] Helm release status is healthy

### Performance

- [ ] Deployed
- [ ] Helm Controller logs show successful reconciliation check
- [ ] Helm release status is healthy
- [ ] gRPC API performance tests
- [ ] Importer performance tests

### Mainnet Staging

- [ ] Delete all deployments in `mainnet-citus` and let flux reconcile and recreate them to drop manual changes
  - `kubectl -n mainnet-citus delete deployments --all`
- [ ] Deployed
- [ ] Helm Controller logs show successful reconciliation check
- [ ] Helm release status is healthy
- [ ] No abnormal exceptions in importer logs
- [ ] REST API performance tests
- [ ] REST Java API performance tests
- [ ] Web3 API performance tests

## Generally Available

- [ ] Publish release
- [ ] Publish marketplace release

### Previewnet

Deployed automatically on every tag.

- [ ] Deployed
- [ ] Helm Controller logs show successful reconciliation check
- [ ] Helm release status is healthy

### Testnet

A GA tag will trigger an automatic deployment to NA. Upon success, a PR for EU will automatically get created.

- [ ] Latest Citus backup is successful
- [ ] Deployed NA
- [ ] Helm Controller logs show successful reconciliation check NA
- [ ] Helm release status is healthy NA
- [ ] Deployed EU
- [ ] Helm Controller logs show successful reconciliation check EU
- [ ] Helm release status is healthy EU

### Pre-Production

These preprod environments are automatically deployed for any GA release. Ensure the deployments are successful.

- [ ] Dev
- [ ] Integration Docker
- [ ] Staging Council
- [ ] Staging Large
- [ ] Staging Small

### Mainnet

Wait about a week after the testnet deployment to give it time to bake, then deploy to NA first. Upon success, a PR for
EU will automatically get created.

- [ ] Latest Citus backup is successful
- [ ] Deployed NA
- [ ] Helm Controller logs show successful reconciliation check NA
- [ ] Helm release status is healthy NA
- [ ] Deployed EU
- [ ] Helm Controller logs show successful reconciliation check EU
- [ ] Helm release status is healthy EU

## Post Release

- [ ] Update any completed HIPs to `Final` status and populate the `release`.
