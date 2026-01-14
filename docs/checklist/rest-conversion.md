# REST API Conversion Checklist

This checklist verifies that the JavaScript-based REST API was converted to Java successfully.

## Phase One

Phase one implements the new API in Java with it disabled by default. Thorough testing is conducted to verify no
regressions are introduced.

### REST Java

- [ ] Controller, services, repositories, etc. implemented in rest-java
- [ ] Use Jooq for any dynamic SQL, repositories for any static queries
- [ ] New `Cache-Control` entry added to `rest-java/src/main/resources/application.yml`
- [ ] Controller integration tests added with equivalent or better coverage to existing rest spec tests.

### REST

- [ ] `REST_JAVA_INCLUDE` and `testFiles` updated in `rest/build.gradle.kts`
- [ ] All existing rest spec tests passing against rewritten Java endpoint

### Acceptance

- [ ] Acceptance tests updated to use `MirrorNodeClient.callConvertedRestEndpoint()`
- [ ] Passing acceptance tests

### Performance

- [ ] K6 tests for endpoint duplicated in rest-java folder
- [ ] K6 tests passing with equal to or greater than rps as older API

### Deployment

- [ ] New rest-java route added to `docker-compose.yml` haproxy config
- [ ] New `routes.<apiName>` property added to `charts/hedera-mirror-rest-java/values.yaml` and defaulted to `false`
- [ ] New route added to ingress conditional on routes property
- [ ] New route added to gateway conditional on routes property
- [ ] Enable route in integration environment before tagging and test it
- [ ] Enable route in previewnet and watch metrics/logs
- [ ] Enable route in both testnet clusters for a release and watch metrics/logs

## Phase Two

Phase two enables the new route by default so that it rolls out to mainnet for the first time. The legacy code is kept
until we're confident there are no regressions.

- [ ] Enable route by default in Ingress and Gateway
- [ ] Update JavaScript monitor to use REST Java URL for route
- [ ] Remove temporary enablement in previewnet and testnet so that default is used

## Phase Three

Phase three cleans up the legacy code and configuration so that only the rest-java route remains.

- [ ] Delete JavaScript code and tests
- [ ] Delete `Cache-Control` entry in rest config
- [ ] Delete duplicate rest k6 test
- [ ] Remove `routes.<apiName>` property in chart and its use in ingress and gateway
- [ ] Acceptance tests updated to use `MirrorNodeClient.callRestJavaEndpoint()`
- [ ] `REST_JAVA_INCLUDE` and `testFiles` updated to remove route and specs in `rest/build.gradle.kts`
