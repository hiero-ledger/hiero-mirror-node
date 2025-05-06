## Enhancements

- Add approval integration tests for HIP-906 - Proxy Redirect Contract for Hbar Allowance and Approval [#11013](https://github.com/hiero-ledger/hiero-mirror-node/issues/11013)
- Add Acceptance tests for topic update, to remove the custom fees [#11010](https://github.com/hiero-ledger/hiero-mirror-node/issues/11010)
- Modify mirror node state to throw an error if it sees a new state in hedera-app [#11003](https://github.com/hiero-ledger/hiero-mirror-node/issues/11003)
- Add IsAuthorizedTests for HAC system contract similar to IsAuthorizedTest in consensus node [#10933](https://github.com/hiero-ledger/hiero-mirror-node/issues/10933)
- Add AliasTests for HAC system contract similar to AliasTest in consensus node [#10932](https://github.com/hiero-ledger/hiero-mirror-node/issues/10932)
- Rename npm packages to `@hiero-ledger` [#10909](https://github.com/hiero-ledger/hiero-mirror-node/issues/10909)
- HIP-1056 Test Genesis Block [#10901](https://github.com/hiero-ledger/hiero-mirror-node/issues/10901)
- Simplify `getTreasury` and `getAutoRenewAccount` methods in `TokenReadableKVState` [#10807](https://github.com/hiero-ledger/hiero-mirror-node/issues/10807)
- Support long form EVM address in `/tokens/{id}` endpoint [#10799](https://github.com/hiero-ledger/hiero-mirror-node/issues/10799)
- Non-zero realm E2E testing [#10743](https://github.com/hiero-ledger/hiero-mirror-node/issues/10743)
- Adapt tests in web3 to support different shard and realm [#10719](https://github.com/hiero-ledger/hiero-mirror-node/issues/10719)
- Add tests for update token custom fees for modularized [#10419](https://github.com/hiero-ledger/hiero-mirror-node/issues/10419)
- Add tests for HederaAccountService precompile for modularized [#10417](https://github.com/hiero-ledger/hiero-mirror-node/issues/10417)

## Bug Fixes

- Release notes generation not working [#11069](https://github.com/hiero-ledger/hiero-mirror-node/issues/11069)
- Acceptance tests fail to validate partially up nodes [#11060](https://github.com/hiero-ledger/hiero-mirror-node/issues/11060)
- `NullPointerException` on AccountID.accountNum() [#11002](https://github.com/hiero-ledger/hiero-mirror-node/issues/11002)
- `NullPointerException` on missing account key [#10989](https://github.com/hiero-ledger/hiero-mirror-node/issues/10989)
- Permission denied for /usr/etc/hedera/application.yml [#10984](https://github.com/hiero-ledger/hiero-mirror-node/issues/10984)
- Account with complex key unduly returned by `accounts?account.publickey={key}` [#10904](https://github.com/hiero-ledger/hiero-mirror-node/issues/10904)
- `NullPointerException` on missing contract key [#10900](https://github.com/hiero-ledger/hiero-mirror-node/issues/10900)
- Fix InitializeEntityBalanceMigration to match entityId conversion to the correct entityId size [#10898](https://github.com/hiero-ledger/hiero-mirror-node/issues/10898)
- Investigate and optimize `TokenAccountRepository.findByIdAndTimestamp` query [#10888](https://github.com/hiero-ledger/hiero-mirror-node/issues/10888)
- Investigate ERROR in estimatePrecompile.feature acceptance test [#10627](https://github.com/hiero-ledger/hiero-mirror-node/issues/10627)

## Documentation

- HIP-1081: Design block node support [#10737](https://github.com/hiero-ledger/hiero-mirror-node/issues/10737)
