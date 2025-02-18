## Release Checklist

This checklist verifies a release is rolled out successfully.

## Preparation

- [ ] Milestone created
- [ ] Milestone field populated on relevant [issues](/../../issues?q=is%3Aclosed+no%3Amilestone+sort%3Aupdated-desc)
- [ ] Nothing open for [milestone](/../../issues?q=is%3Aopen+sort%3Aupdated-desc+milestone%3A0.125.0)
- [ ] GitHub checks for branch are passing
- [ ] No pre-release or snapshot dependencies present in build files
- [ ] Verify hedera-services protobuf dependency doesn't contain any unsupported fields or messages
- [ ] Automated Kubernetes deployment to integration successful
- [ ] Tag release

## Release Candidates

### Previewnet

Deployed automatically on every tag.

- [ ] Deployed

### Performance

- [ ] Deployed
- [ ] gRPC API performance tests
- [ ] Importer performance tests

### Mainnet Staging

- [ ] Deployed
- [ ] REST API performance tests
- [ ] REST Java API performance tests
- [ ] Web3 API performance tests

## Generally Available

- [ ] Publish release
- [ ] Publish marketplace release

### Previewnet

Deployed automatically on every tag.

- [ ] Deployed

### Testnet

A GA tag will trigger an automatic deployment to NA. Upon success, a PR for EU will automatically get created.

- [ ] Disk Snapshot for Citus is Taken (can be EU or NA)
- [ ] Deployed NA
- [ ] Deployed EU

### Pre-Production

These preprod environments are automatically deployed for any GA release. Ensure the deployments are successful.

- [ ] Dev
- [ ] Integration Docker
- [ ] Staging Large
- [ ] Staging Small

### Mainnet

Wait about a week after the testnet deployment to give it time to bake, then deploy to NA first. Upon success, a PR for
EU will automatically get created.

- [ ] Disk Snapshot for Citus is Taken (can be EU or NA)
- [ ] Deployed NA
- [ ] Deployed EU

## Post Release

- [ ] Update any completed HIPs to `Final` status and populate the `release`.
