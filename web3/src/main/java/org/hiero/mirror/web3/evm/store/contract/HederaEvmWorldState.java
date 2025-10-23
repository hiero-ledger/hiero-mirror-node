// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.WorldStateAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import jakarta.inject.Named;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.Store;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

@SuppressWarnings("java:S107")
@Named
public class HederaEvmWorldState implements HederaEvmMutableWorldState {

    private final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final EvmProperties evmProperties;
    private final AbstractCodeCache abstractCodeCache;

    private final AccountAccessor accountAccessor;
    private final TokenAccessor tokenAccessor;
    private final Store store;

    private final EntityAddressSequencer entityAddressSequencer;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;
    private final CodeFactory codeFactory;

    @SuppressWarnings("java:S107")
    public HederaEvmWorldState(
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final EvmProperties evmProperties,
            final AbstractCodeCache abstractCodeCache,
            final AccountAccessor accountAccessor,
            final TokenAccessor tokenAccessor,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final Store store,
            final CodeFactory codeFactory) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.abstractCodeCache = abstractCodeCache;
        this.accountAccessor = accountAccessor;
        this.tokenAccessor = tokenAccessor;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
        this.entityAddressSequencer = entityAddressSequencer;
        this.store = store;
        this.codeFactory = codeFactory;
    }

    public Account get(final Address address) {
        if (address == null) {
            return null;
        }
        if (hederaEvmEntityAccess.isTokenAccount(address) && evmProperties.isRedirectTokenCallsEnabled()) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        if (!hederaEvmEntityAccess.isUsable(address)) {
            return null;
        }
        final long balance = hederaEvmEntityAccess.getBalance(address);
        return new WorldStateAccount(address, Wei.of(balance), abstractCodeCache, hederaEvmEntityAccess, codeFactory);
    }

    @Override
    public Hash rootHash() {
        return Hash.EMPTY;
    }

    @Override
    public Hash frontierRootHash() {
        return rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HederaEvmWorldUpdater updater() {
        return new Updater(
                this,
                accountAccessor,
                hederaEvmEntityAccess,
                tokenAccessor,
                evmProperties,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
    }

    @Override
    public void close() {
        // default no-op
    }

    public static class Updater extends AbstractLedgerWorldUpdater<HederaEvmMutableWorldState, Account>
            implements HederaEvmWorldUpdater {
        private final HederaEvmEntityAccess hederaEvmEntityAccess;
        private final TokenAccessor tokenAccessor;
        private final EvmProperties evmProperties;
        private final EntityAddressSequencer entityAddressSequencer;
        private final MirrorEvmContractAliases mirrorEvmContractAliases;

        @SuppressWarnings("java:S107")
        protected Updater(
                final HederaEvmWorldState world,
                final AccountAccessor accountAccessor,
                final HederaEvmEntityAccess hederaEvmEntityAccess,
                final TokenAccessor tokenAccessor,
                final EvmProperties evmProperties,
                final EntityAddressSequencer contractAddressState,
                final MirrorEvmContractAliases mirrorEvmContractAliases,
                final Store store) {
            super(world, accountAccessor, store);
            this.tokenAccessor = tokenAccessor;
            this.hederaEvmEntityAccess = hederaEvmEntityAccess;
            this.evmProperties = evmProperties;
            this.entityAddressSequencer = contractAddressState;
            this.mirrorEvmContractAliases = mirrorEvmContractAliases;
        }

        @Override
        public Address newContractAddress(Address sponsor) {
            return asTypedEvmAddress(entityAddressSequencer.getNewContractId(sponsor));
        }

        @Override
        public long getSbhRefund() {
            return 0;
        }

        @Override
        public Account getForMutation(final Address address) {
            final HederaEvmWorldState wrapped = (HederaEvmWorldState) wrappedWorldView();
            return wrapped.get(address);
        }

        @Override
        public WorldUpdater updater() {
            return new HederaEvmStackedWorldStateUpdater(
                    this,
                    accountAccessor,
                    hederaEvmEntityAccess,
                    tokenAccessor,
                    evmProperties,
                    entityAddressSequencer,
                    mirrorEvmContractAliases,
                    store);
        }
    }
}
