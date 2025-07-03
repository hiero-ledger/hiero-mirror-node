# K6 Performance Tests

This module covers the [k6](https://k6.io/) based performance tests for Mirror Node APIs including rest, rest-java,
rosetta, and web3.

## Setup

The k6 test engine is needed to run the tests. Please follow
the [official documentation](https://k6.io/docs/getting-started/installation/) to install k6. Ensure the following OS
properties are set to avoid resource exhaustion:

```shell
ulimit -n 1048576
echo "1" > /proc/sys/net/ipv4/tcp_tw_reuse
echo "16384 65535" > /proc/sys/net/ipv4/ip_local_port_range
```

## Configuration

Configuration of the k6 tests is done via environment variables. Environment variables can be passed directly to k6 or
placed in an environment file and sourced before each run. For example, here's a `k6.env`:

```shell
export BASE_URL=https://testnet.mirrornode.hedera.com
export DEFAULT_DURATION=1s
export DEFAULT_LIMIT=100
export DEFAULT_VUS=1
```

This file can then be sourced before executing k6:

```shell
source k6.env
```

For non-domain specific parameters like `DEFAULT_DURATION`, `DEFAULT_VUS`, etc. a sane default will be used if not
provided. For domain specific parameters, when the value of a parameter is explicitly set then that value will be used,
otherwise its value will be found by querying the APIs. The `DEFAULT_SETUP_TIMEOUT` controls how much time it should
spend querying for defaults. However, it's strongly recommended to explicitly provide values for all domain specific
parameters to produce consistent results between runs. Some entities have considerably more data than others and
be considerably slower if they're picked and vice versa.

### Common

The following parameters can be used to configure all tests regardless of API:

| Name                  | Default          | Description                                                        |
| --------------------- | ---------------- | ------------------------------------------------------------------ |
| BASE_URL              | http://localhost | The URL prefix without `/api/v1` to connect to                     |
| DEFAULT_DURATION      | 120s             | How much time to execute each test                                 |
| DEFAULT_LIMIT         | 100              | For list APIs, the number of results to return in the response     |
| DEFAULT_SETUP_TIMEOUT | 5m               | The amount of time to discover domain specific defaults            |
| DEFAULT_VUS           | 10               | The number of virtual users k6 should use to parallelize execution |

### REST API

The following parameters can be used to configure a REST test:

| Name                               | Default | Description                        |
| ---------------------------------- | ------- | ---------------------------------- |
| DEFAULT_ACCOUNT_ID                 |         |                                    |
| DEFAULT_ACCOUNT_ID_NFTS            |         |                                    |
| DEFAULT_ACCOUNT_ID_TOKEN           |         |                                    |
| DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE |         |                                    |
| DEFAULT_ACCOUNT_BALANCE            |         |                                    |
| DEFAULT_BALANCE_TIMESTAMP          | now()   |                                    |
| DEFAULT_BLOCK_NUMBER               |         |                                    |
| DEFAULT_BLOCK_HASH                 |         |                                    |
| DEFAULT_CONTRACT_ID                |         |                                    |
| DEFAULT_CONTRACT_TIMESTAMP         |         |                                    |
| DEFAULT_CONTRACT_RESULT_HASH       |         |                                    |
| DEFAULT_NFT_ID                     |         |                                    |
| DEFAULT_NFT_SERIAL                 |         |                                    |
| DEFAULT_PUBLIC_KEY                 |         |                                    |
| DEFAULT_SCHEDULE_ACCOUNT_ID        |         |                                    |
| DEFAULT_SCHEDULE_ID                |         |                                    |
| DEFAULT_START_ACCOUNT              | 0       |                                    |
| DEFAULT_TOKEN_BALANCE_TIMESTAMP    | now()   |                                    |
| DEFAULT_TOKEN_ID                   |         |                                    |
| DEFAULT_TOKEN_NAME                 |         |                                    |
| DEFAULT_TOKEN_TIMESTAMP            | now()   |                                    |
| DEFAULT_TOPIC_ID                   |         |                                    |
| DEFAULT_TOPIC_SEQUENCE             |         |                                    |
| DEFAULT_TOPIC_TIMESTAMP            |         |                                    |
| DEFAULT_TRANSACTION_HASH           |         |                                    |
| DEFAULT_TRANSACTION_ID             |         |                                    |
| REST_TEST_EXCLUDE                  | ^$      | The rest test scenarios to exclude |
| REST_TEST_INCLUDE                  | .\*     | The rest test scenarios to include |

### REST Java API

The following parameters can be used to configure a rest-java test:

| Name                                      | Default | Description                                     |
| ----------------------------------------- | ------- | ----------------------------------------------- |
| DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER       |         | The account to be used for pending airdrops     |
| DEFAULT_ACCOUNT_ID_AIRDROP_SENDER         |         | The account to be used for outstanding airdrops |
| DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER   |         |                                                 |
| DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_SPENDER |         |                                                 |
| DEFAULT_TOPIC_WITH_FEE_ID                 |         | Topic with custom fee id                        |
| RESTJAVA_TEST_EXCLUDE                     | ^$      | The rest-java test scenarios to exclude         |
| RESTJAVA_TEST_INCLUDE                     | .\*     | The rest-java test scenarios to include         |

### Rosetta API

The following parameters can be used to configure a rosetta test:

| Name                     | Default | Description                           |
| ------------------------ | ------- | ------------------------------------- |
| DEFAULT_BLOCK_INDEX      |         |                                       |
| DEFAULT_BLOCK_HASH       |         |                                       |
| DEFAULT_NETWORK          |         |                                       |
| DEFAULT_TRANSACTION_HASH |         |                                       |
| ROSETTA_TEST_EXCLUDE     | ^$      | The rosetta test scenarios to exclude |
| ROSETTA_TEST_INCLUDE     | .\*     | The rosetta test scenarios to include |

### Web3 API

The following parameters can be used to configure a web3 test:

| Name                                                         | Default | Description                                                                                                                                                                       |
|--------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ACCOUNT_ADDRESS                                              |         | 64 character hex encoded account address without `0x` prefix                                                                                                                      |
| AMOUNT                                                       |         | 64 character hex encoded amount without `0x` prefix                                                                                                                               |
| ASSOCIATED_ACCOUNT                                           |         | 64 character hex encoded account evm address without `0x` prefix - associated to TOKEN_ADDRESS and NON_FUNGIBLE_TOKEN_ADDRESS                                                     |
| DEFAULT_ACCOUNT_ADDRESS                                      |         | 40 character hex encoded account address without `0x` prefix                                                                                                                      |
| DEFAULT_CONTRACT_ADDRESS                                     |         | 40 character hex encoded contract address without `0x` prefix for `Parent.sol`                                                                                                    |
| ERC_CONTRACT_ADDRESS                                         |         | 40 character hex encoded contract address without `0x` prefix for `ErcTestContract.sol`                                                                                           |
| ESTIMATE_PRECOMPILE_CONTRACT                                 |         | 40 character hex encoded contract address without `0x` prefix for `EstimatePrecompileContract.sol`                                                                                |
| FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS                       |         | 64 character hex encoded fungible token address without `0x` prefix - with a freeze key set                                                                                       |
| FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS    |         | 64 character hex encoded account evm address without `0x` prefix - an account associated to FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS                                                |
| HTS_CONTRACT_ADDRESS                                         |         | 40 character hex encoded contract address without `0x` prefix for `PrecompileTestContract.sol`                                                                                    |
| KEY_TYPE                                                     |         | 64 character hex encoded key type without `0x` prefix                                                                                                                             |
| NON_FUNGIBLE_TOKEN_ADDRESS                                   |         | 64 character hex encoded non-fungible token address without `0x` prefix                                                                                                           |
| NON_FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS                   |         | 64 character hex encoded non-fungible token address without `0x` prefix - with a freeze key set                                                                                   |
| PAYER_ACCOUNT                                                |         | 40 character hex encoded account address without `0x` prefix - with a lot of balance that can be used for costy operations such as token create                                   |
| PRECOMPILE_CONTRACT                                          |         | 40 character hex encoded contract address without `0x` prefix for `PrecompileTestContract.sol`                                                                                    |
| RECEIVER_ADDRESS                                             |         | 64 character hex encoded account evm address without `0x` prefix - associated account                                                                                             |
| RUN_COMPLEX_TESTS                                            | true    | If set to false, complex function tests will not run.                                                                                                                             |
| RUN_ESTIMATE_TESTS                                           | true    | If set to false, estimate gas tests will not run.                                                                                                                                 |
| RUN_MODIFICATION_TESTS                                       | true    | If set to false, modification tests will not run.                                                                                                                                 |
| RUN_WITH_VARIABLES                                           | true    | if set to false, tests will be run with data from modificationFunctions.json                                                                                                      |
| SERIAL_NUMBER                                                |         | 64 character hex encoded nft serial number without `0x` prefix                                                                                                                    |
| SPENDER_ADDRESS                                              |         | 64 character hex encoded account address without `0x` prefix                                                                                                                      |
| STORAGE_SLOTS_CALLDATA                                       |         | Configurable callData for a contract call, example `0x2ef8bbe7`                                                                                                                   |
| STORAGE_SLOTS_CONTRACT                                       |         | 40 character hex encoded contract address without `0x` prefix                                                                                                                     |
| TOKEN_ADDRESS                                                |         | 64 character hex encoded token address without `0x` prefix                                                                                                                        |
| TOKEN_FREEZE_KEY_ACCOUNT_ADDRESS                             |         | 64 character hex encoded account evm address without `0x` prefix - a freeze key account for FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS and NON_FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS |
| WEB3_TEST_EXCLUDE                                            | ^$      | The web3 test scenarios to exclude                                                                                                                                                |
| WEB3_TEST_INCLUDE                                            | .\*     | The web3 test scenarios to include                                                                                                                                                |

For k6 to be run we need to deploy the relevant contracts in `test/src/test/resources/solidity` first. For
that, we can use Hedera SDK. Example for ERC_CONTRACT deployment
with js SDK

```js
const contractCreate = await new ContractCreateFlow()
  .setBytecode(
    "HERE YOU NEED TO PUT BYTECODE FROM test/src/test/resources/solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json"
  )
  .setGas(200_000)
  .execute(client);
```

### Filter Test Case

You can use the following test suite specific environment variables to filter the test cases in a suite to run,

- `REST_TEST_EXCLUDE` to exclude test cases in the JS REST test suite
- `REST_TEST_INCLUDE` to include test cases in the JS REST test suite

The value should be a regex string to match lower case test case names. The `exclude` filter has priority over the
`include` filter. Substitute the `REST` prefix with the appropriate suite name for other test suites.

- `REST` for JS REST test suite
- `RESTJAVA` for Java REST test suite
- `ROSETTA` for Rosetta test suite
- `WEB3` for Web3 test suite

Some examples:

- `REST_TEST_EXCLUDE='^transaction.*$'` will exclude all test cases that start with `transaction`
- `REST_TEST_EXCLUDE='^(transaction|topic).*$'` will exclude all test cases that start with either `transaction`
  or `topic`
- `REST_TEST_INCLUDE='^(account|token).*$'` will include only test cases that start with either `account` or `token`

To run a testkube test / testsuite with test case filters, run the following:

```shell
testkube run testsuite test-suite-rest -v REST_TEST_EXCLUDE='^transaction.*$' -v WEB3_TEST_INCLUDE='^.*receive.*$'
```

Note you can pass multiple `-v` flags, one for each filter.

## Execution

The tests are organized per API, and they reside in `src/rest`, `src/rest-java`, `src/rosetta`, and `src/web3`
respectively. The API performance tests can be run as a test suite or individually.

### Test Suite

To run a test suite, such as rest, use the following command:

```shell
source k6.env && k6 run src/rest/apis.js
```

The test suite will run the tests sequentially with a configurable grace period in between tests so that they don't
interfere with each other. Once the tests complete, `k6` will show a test summary. Disregard the per scenario RPS
reported in the `http_reqs` section since it's calculated as the total requests in a scenario divided by the run time of
the test suite.

At the end of a test suite run, a simplified Markdown format `report.md` will be generated. Below is an example
of such a report. The main columns to consider are the `Pass RPS` and the `Avg. Req Duration`.

| Scenario | URL       | VUS  | Pass%  | RPS       | Pass RPS  | Avg. Req Duration | Skipped? | Comment |
| -------- | --------- | ---- | ------ | --------- | --------- | ----------------- | -------- | ------- |
| accounts | /accounts | 1500 | 100.00 | 1390.03/s | 1390.03/s | 351.87ms          | No       |         |
| blocks   | /block    | 1500 | 99.9   | 5571.63/s | 5572.19/s | 300.11ms          | No       |         |

### Single Test

To run a single test, such as the rosetta `accountBalance` test, use a command similar to the below:

```shell
source k6.env && k6 run src/rosetta/test/accountBalance.js
```

When it completes, k6 will show a similar summary report. However, there will not be a report file generated.
