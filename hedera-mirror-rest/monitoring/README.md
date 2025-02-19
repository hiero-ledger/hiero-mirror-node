# Monitoring a live deployment of Hedera Mirror Node

This code runs on an external server outside of the Hedera mirror node deployment, and periodically polls the REST APIs
exposed by the Hedera mirror node to ensure that the deployed APIs are working.

## Monitoring APIs:

A process runs that periodically polls the APIs exposed by the deployment of a Hedera mirror node.
It then checks the responses using a few simple checks for those APIs.
The results of these checks are exposed as a set of REST APIs by this monitoring service as follows:

| API                 | HTTP return code | Description                                                                                                                                          |
| ------------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| /api/v1/status      | 200 (OK)         | Provides a list of results of all tests run on all servers                                                                                           |
| /api/v1/status/{id} | 200 (OK)         | If all tests pass for a server, then it returns the results                                                                                          |
|                     | 4xx              | If any tests fail for a server, or if the server is not running, then it returns a 4xx error code to make it easy to integrate with alerting systems |

---

## Quickstart

### Requirements

- [ ] List of addresses of Hedera mirror nodes that you want to monitor
- [ ] An existing topic ID of the target Hedera Mirror node environment if you want to run topic message tests
- [ ] An external server where you want to run this code to monitor the mirror node. You will need two TCP ports on the
      server.
- [ ] npm
- [ ] PM2

```
git clone git@github.com:hiero-ledger/hiero-mirror-node.git
cd hiero-mirror-node/hedera-mirror-rest/monitoring
```

To install the dependencies and configure the monitor:

```
npm install
vi config/serverlist.json // Insert the mirror node deployments you want to monitor
```

To customize per-resource configuration:

- set `enabled` to `true` or `false` to enable / disable tests for a resource
- `freshnessThreshold` (in seconds) for `balance`, `transaction`, `topic`, and `token` (balances of a token) can be
  adjusted independently if needed. Set to 0 to disable freshness check for a resource
- `intervalMultiplier` for all resources. The tests for a resource will run every `interval * intervalMultiplier`
  seconds. For `stateproof`, it defaults to 10, so the tests run at a lower frequency to reduce cost
- `limit` threshold for `account`, `balance`, `transaction`, `topic`, and `token` can be adjusted independently if
  needed, e.g.,for environments with lower traffic volume. For `token`, `tokenBalancesLimit` is the limit for a token's
  balance query and `tokensLimit` is the limit for the token discovery query
- set `topic.topicId` to an existing topic ID of the target environment. If not set, topic message tests will be skipped
- set `token.tokenId` to an existing token ID of the target environment in case it's needed to pass certain token tests,
  e.g., `tokenBalancesLimit` for the token. If not set, the tests will use the first token from the
  token discovery API response

See [default.serverlist.json](/hedera-mirror-rest/monitoring/config/default.serverlist.json) for a full
list of configuration properties.

To run the monitor:

```
PORT=3000 pm2 start server.js --node-args="--import=extensionless/register"
```

The server will start polling Hedera mirror nodes specified in the config/serverlist.json file. The default interval to
populate the data is 60 seconds. You can verify the output using `curl http://<host>:<port>/api/v1/status` command. By
default, the response only includes the details of failed tests. You can pass `result=` query parameter to filter test
result details by the result type, the accepted values are `all`, `failed`, and `passed`. For example, to show the
details of the passed test results, use `curl http://<host>:<port>/api/v1/status?result=passed`.

## Docker

Create a custom `serverlist.json` in the current working directory. Then execute:

```shell
docker run -it --rm -e CONFIG_PATH=/config -v "${PWD}/serverlist.json:/config/serverlist.json" -p 3000:3000 gcr.io/mirrornode/hedera-mirror-rest-monitor
```
