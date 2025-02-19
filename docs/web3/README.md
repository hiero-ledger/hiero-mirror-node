# Web3 API

The Web3 API provides Java-based REST APIs for the mirror node.

## Technologies

This module uses [Spring Boot](https://spring.io/projects/spring-boot) for its application framework. To serve the
APIs, [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
is used with annotation-based controllers. [Spring Data JPA](https://spring.io/projects/spring-data-jpa) with Hibernate
is used for the persistence layer.

## Supported Operations

| Estimate | Static | Operation Type                                                                            | Historical | Reads | Modifications |
| -------- | ------ | ----------------------------------------------------------------------------------------- | ---------- | ----- | ------------- |
| Y        | Y      | non precompile functions                                                                  | Y          | Y     | Y             |
| Y        | N      | non precompile functions with lazy account creation                                       | Y          | Y     | Y             |
| Y        | Y      | operations for ERC precompile functions (balance, symbol, tokenURI, name, decimals, etc.) | Y          | Y     | N             |
| Y        | Y      | read-only ERC precompile functions                                                        | Y          | Y     | N             |
| Y        | Y      | modifying ERC precompile functions                                                        | Y          | Y     | Y             |
| Y        | Y      | read-only operations for HTS system contract                                              | Y          | Y     | N             |
| Y        | N      | modifying operations for HTS system contract                                              | Y          | Y     | Y             |

_Note:_ Gas estimation only supports the `latest` block

## Unsupported Operations

| Operation Type                                                |
| ------------------------------------------------------------- |
| operations for HederaAccountService system contract           |
| operations for HederaScheduleService system contract          |
| token airdrop operations (airdropTokens, claimAirdrops, etc.) |
| update token custom fees                                      |
| HRC isAssociated()                                            |

## Acceptance Tests

The [acceptance tests](/hedera-mirror-test/README.md) contain a suite of tests to validate a web3 deployment.
The `@web3` acceptance tag can be used to specifically target the web3 module.

`./gradlew :test:acceptance --info -Dcucumber.filter.tags=@web3`

## Smoke Tests

The Web3 API uses [Postman](https://www.postman.com) tests to verify proper operation. The
[Newman](https://learning.postman.com/docs/running-collections/using-newman-cli/command-line-integration-with-newman)
command-line collection runner is used to execute the tests against a remote server. To use newman, either the
executable binary or Docker approach can be used. With either approach, a `baseUrl` variable can be supplied to
customize the target server.

To run the Postman tests, first ensure newman is installed locally using `npm`, then execute `newman`.

```shell
npm install -g newman
newman run charts/hedera-mirror-web3/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

Alternatively, Docker can be used execute the smoke tests:

```shell
docker run --rm -v "${PWD}/charts/hedera-mirror-web3/postman.json:/tmp/postman.json" -t postman/newman run /tmp/postman.json --env-var baseUrl=https://previewnet.mirrornode.hedera.com
```

_Note:_ To test against an instance running on the same machine as Docker use your local IP instead of 127.0.0.1.

## Manual Tests

Any REST client can be used to manually invoke the contract call API. In the below example, curl is used to simulate a
call to the `tinycentsToTinybars(uint256)` function in the exchange rate system contract with 100 cents as input.

```shell
curl -X 'POST' https://testnet.mirrornode.hedera.com/api/v1/contracts/call -H 'Content-Type: application/json' -d \
'{
  "block": "latest",
  "data": "0x2e3cff6a0000000000000000000000000000000000000000000000000000000000000064",
  "estimate": false,
  "gas": 15000000,
  "gasPrice": 100000000,
  "to": "0x0000000000000000000000000000000000000168"
}'
```
