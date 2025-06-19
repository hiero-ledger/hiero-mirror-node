// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
@Named
public class MirrorEntityAccess implements HederaEvmEntityAccess {
    private final ContractRepository contractRepository;
    private final ContractStateService contractStateService;
    private final Store store;

    // An account is usable if it isn't deleted or if it has balance==0 but is not the 0-address
    // or the empty account.  (This allows the special case where a synthetic 0-address account
    // is used in eth_estimateGas.)
    @SuppressWarnings("java:S1126") // "replace this if-then-else statement by a single return"
    @Override
    public boolean isUsable(final Address address) {
        // Do not consider expiry/renewal at this time.  It is not enabled in the network.
        // When it is handled it must be gated on (already existing) mirror node feature flags
        // (properties).

        final var account = store.getAccount(address, OnMissing.DONT_THROW);

        final var balance = account.getBalance();
        final var isDeleted = account.isDeleted();

        if (isDeleted) {
            return false;
        }

        if (balance > 0) {
            return true;
        }

        if (Address.ZERO.equals(address)) {
            return false;
        }

        return !account.isEmptyAccount();
    }

    @Override
    public long getBalance(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        if (account.isEmptyAccount()) {
            return 0L;
        }
        return account.getBalance();
    }

    @Override
    public long getNonce(Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        if (account.isEmptyAccount()) {
            return 0L;
        }
        return account.getEthereumNonce();
    }

    @Override
    public boolean isExtant(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        return !account.isEmptyAccount();
    }

    @Override
    public boolean isTokenAccount(final Address address) {
        final var maybeToken = store.getToken(address, OnMissing.DONT_THROW);
        return !maybeToken.isEmptyToken();
    }

    @Override
    public ByteString alias(final Address address) {
        var account = store.getAccount(address, OnMissing.DONT_THROW);
        if (!account.isEmptyAccount()) {
            return account.getAlias();
        }
        return ByteString.EMPTY;
    }

    @Override
    public Bytes getStorage(final Address address, final Bytes key) {
        final var entityId = fetchEntityId(address);

        if (entityId == 0L) {
            return Bytes.EMPTY;
        }

        var contractId = EntityId.of(entityId);

        return store.getHistoricalTimestamp()
                .map(t -> contractStateService.findStorageByBlockTimestamp(
                        contractId, key.trimLeadingZeros().toArrayUnsafe(), t))
                .orElseGet(() -> contractStateService.findStorage(contractId, key.toArrayUnsafe()))
                .map(Bytes::wrap)
                .orElse(Bytes.EMPTY);
    }

    @Override
    public Bytes fetchCodeIfPresent(final Address address) {
        final var entityId = fetchEntityId(address);

        if (entityId == 0) {
            return null;
        }

        final var runtimeCode = contractRepository.findRuntimeBytecode(entityId);
        return runtimeCode.map(Bytes::wrap).orElse(null);
    }

    private Long fetchEntityId(final Address address) {
        if (isMirror(address.toArrayUnsafe())) {
            return entityIdNumFromEvmAddress(address);
        }
        var entityId = store.getAccount(address, OnMissing.DONT_THROW).getEntityId();
        if (entityId == 0L) {
            entityId = store.getToken(address, OnMissing.DONT_THROW).getEntityId();
        }
        return entityId;
    }
}
