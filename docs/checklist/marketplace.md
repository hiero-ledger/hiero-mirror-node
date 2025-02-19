# GCP Marketplace

This document outlines the steps necessary to upgrade the version of the mirror node on GCP Marketplace.

## Local Changes

- [ ] Check out the git tag to release to Marketplace
- [ ] Bump PostgreSQL version in `release.sh`
- [ ] Bump BATS version in `release.sh`
- [ ] Update `schema.yaml` with any updated Helm properties
- [ ] Update marketplace `values.yaml`

## Test Locally

- [ ] Build a deployer image using the [instructions](/charts/marketplace/gcp/BUILDING.md)
- [ ] Create a test GKE cluster
- [ ] Run `mpdev verify` successfully
- [ ] Run `mpdev install` successfully

## Submit

- [ ] Review all metadata in Marketplace to ensure it's up-to-date
- [ ] Create a new version in Marketplace
- [ ] Automated verification passes
- [ ] Submitted to Google
- [ ] Approved by Google
- [ ] View the preview and deploy the new version via Marketplace to a new GKE cluster
- [ ] Delete temporary cluster
- [ ] Published
