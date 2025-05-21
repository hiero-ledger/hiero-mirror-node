## Enhancements

- Fix ethereum.feature hbar leaking due to not deleting the signer account [#11139](https://github.com/hiero-ledger/hiero-mirror-node/issues/11139)
- HIP-1046 gRPC web proxy endpoints [#11136](https://github.com/hiero-ledger/hiero-mirror-node/issues/11136)
- Fix failing acceptance tests with the latest version of services and hedera app [#11121](https://github.com/hiero-ledger/hiero-mirror-node/issues/11121)
- Support both hedera and hiero configuration property prefixes [#11085](https://github.com/hiero-ledger/hiero-mirror-node/issues/11085)
- Bump hedera-app to 0.62 [#11051](https://github.com/hiero-ledger/hiero-mirror-node/issues/11051)
- HIP-1081 Refactor BlockStreamPoller [#11023](https://github.com/hiero-ledger/hiero-mirror-node/issues/11023)
- DB migration needed for `null` contract keys [#10988](https://github.com/hiero-ledger/hiero-mirror-node/issues/10988)
- HIP-1056 Update Blockfile Reader to use block\_timestamp [#10679](https://github.com/hiero-ledger/hiero-mirror-node/issues/10679)
- Revert the temporary workaround for the throttle from hedera-app [#10589](https://github.com/hiero-ledger/hiero-mirror-node/issues/10589)
- Integrate modularized EVM library [#8828](https://github.com/hiero-ledger/hiero-mirror-node/issues/8828)

## Bug Fixes

- Several web3 acceptance tests fail in integration [#11169](https://github.com/hiero-ledger/hiero-mirror-node/issues/11169)
- Node Transacton migration throws error [#11157](https://github.com/hiero-ledger/hiero-mirror-node/issues/11157)
- Web3 request timeout [#11144](https://github.com/hiero-ledger/hiero-mirror-node/issues/11144)
- `NullPointerException` on fractional fees [#11137](https://github.com/hiero-ledger/hiero-mirror-node/issues/11137)
- Importer halts if batch transactions are not stored [#11081](https://github.com/hiero-ledger/hiero-mirror-node/issues/11081)
- Warnings of type `Category NODE payer account should have resulted in failure upstream` are logged in grafana on mainnet [#11079](https://github.com/hiero-ledger/hiero-mirror-node/issues/11079)
