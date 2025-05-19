// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmStackedWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;

@SuppressWarnings("java:S107")
public class HederaEvmStackedWorldStateUpdater
        extends AbstractEvmStackedLedgerUpdater<HederaEvmMutableWorldState, Account>
        implements HederaEvmWorldUpdater, HederaEvmStackedWorldUpdater {

    private static final byte[] NON_CANONICAL_REFERENCE = new byte[20];
    protected final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final EvmProperties evmProperties;
    private final EntityAddressSequencer entityAddressSequencer;
    private final TokenAccessor tokenAccessor;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    public HederaEvmStackedWorldStateUpdater(
            final AbstractLedgerWorldUpdater<HederaEvmMutableWorldState, Account> updater,
            final AccountAccessor accountAccessor,
            final HederaEvmEntityAccess hederaEvmEntityAccess,
            final TokenAccessor tokenAccessor,
            final EvmProperties evmProperties,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final Store store) {
        super(updater, accountAccessor, tokenAccessor, hederaEvmEntityAccess, store);
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.entityAddressSequencer = entityAddressSequencer;
        this.tokenAccessor = tokenAccessor;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    @Override
    public MutableAccount getOrCreate(Address address) {
        final MutableAccount account = getAccount(address);
        // if the account exists, return it
        if (account != null) {
            return account;
        }
        // if account is not present we create a ghost account that will not be persisted
        return createGhostAccount(address);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MutableAccount createAccount(Address address, long nonce, Wei balance) {
        persistAccount(address, nonce, balance);
        final UpdateTrackingAccount account = new UpdateTrackingAccount<>(address, null);
        account.setNonce(nonce);
        account.setBalance(balance);
        return track(account);
    }

    @Override
    public Account get(final Address address) {
        if (isTokenRedirect(address)) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        return super.get(address);
    }

    @Override
    public MutableAccount getAccount(final Address address) {
        if (isTokenRedirect(address)) {
            final var proxyAccount = new HederaEvmWorldStateTokenAccount(address);
            return new UpdateTrackingAccount<>(proxyAccount, null);
        }

        return super.getAccount(address);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void trackLazilyCreatedAccount(final Address address) {
        final UpdateTrackingAccount newMutable = new UpdateTrackingAccount<>(address, null);
        track(newMutable);
    }

    private void persistAccount(Address address, long nonce, Wei balance) {
        final var resolvedAddress = mirrorEvmContractAliases.resolveForEvm(address);
        final var isResolvedAddressZero = Address.ZERO.equals(resolvedAddress);

        final var accountModel = new com.hedera.services.store.models.Account(
                !isResolvedAddressZero && !address.equals(resolvedAddress)
                        ? ByteString.copyFrom(address.toArray())
                        : ByteString.EMPTY,
                0L,
                !isResolvedAddressZero
                        ? Id.fromGrpcAccount(accountIdFromEvmAddress(resolvedAddress))
                        : Id.fromGrpcAccount(accountIdFromEvmAddress(address)),
                0L,
                balance::toLong,
                false,
                () -> 0L,
                0L,
                null,
                0,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                () -> 0,
                () -> 0,
                0,
                nonce,
                false,
                null,
                0L,
                0);
        store.updateAccount(accountModel);
    }

    /**
     * Returns the mirror form of the given EVM address.
     *
     * @param evmAddress an EVM address
     * @return its mirror form
     */
    public byte[] permissivelyUnaliased(final byte[] evmAddress) {
        return mirrorEvmContractAliases
                .resolveForEvm(Address.wrap(Bytes.wrap(evmAddress)))
                .toArrayUnsafe();
    }

    /**
     * Returns the mirror form of the given EVM address if it exists; or 20 bytes of binary zeros if the given address
     * is the mirror address of an account with an EIP-1014 address. We refer to canonicalAddress as the alias/evm based
     * address value of a given account.
     *
     * @param evmAddress an EVM address
     * @return its mirror form, or binary zeros if an EIP-1014 address should have been used for this account
     */
    public byte[] unaliased(final byte[] evmAddress) {
        final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
        if (!addressOrAlias.equals(tokenAccessor.canonicalAddress(addressOrAlias))
                && !evmProperties.callsToNonExistingEntitiesEnabled(addressOrAlias)) {
            return NON_CANONICAL_REFERENCE;
        }
        return mirrorEvmContractAliases.resolveForEvm(addressOrAlias).toArrayUnsafe();
    }

    @Override
    public Address priorityAddress(Address addressOrAlias) {
        return accountAccessor.canonicalAddress(addressOrAlias);
    }

    @Override
    public Address newAliasedContractAddress(Address sponsor, Address alias) {
        final var mirrorAddress = newContractAddress(sponsor);
        // Only link the alias if it's not already in use, or if the target of the alleged link
        // doesn't actually exist. (In the first case, a CREATE2 that tries to re-use an existing
        // alias address is going to fail in short order; in the second case, the existing link
        // must have been created by an inline create2 that failed, but didn't revert us---we are
        // free to re-use this alias).
        if (!mirrorEvmContractAliases.isInUse(alias) || isMissingTarget(alias)) {
            mirrorEvmContractAliases.link(alias, mirrorAddress);
        }
        return mirrorAddress;
    }

    @Override
    public Address newContractAddress(Address sponsor) {
        return asTypedEvmAddress(entityAddressSequencer.getNewContractId(sponsor));
    }

    @Override
    public long getSbhRefund() {
        return 0;
    }

    public Store getStore() {
        return store;
    }

    private boolean isMissingTarget(final Address alias) {
        final var target = mirrorEvmContractAliases.resolveForEvm(alias);
        return Id.DEFAULT.equals(store.getAccount(target, OnMissing.DONT_THROW).getId());
    }

    private boolean isTokenRedirect(final Address address) {
        return hederaEvmEntityAccess.isTokenAccount(address) && evmProperties.isRedirectTokenCallsEnabled();
    }

    private MutableAccount createGhostAccount(Address address) {
        final var ghostAcc = com.hedera.services.store.models.Account.getDummySenderAccount(address)
                .setMaxAutoAssociations(1);
        store.updateAccount(ghostAcc);
        return createAccount(address, 0, Wei.ZERO);
    }

    public boolean contractIsTokenTreasury(final Address addressOrAlias) {
        final var address = mirrorEvmContractAliases.resolveForEvm(addressOrAlias);
        com.hedera.services.store.models.Account account = store.getAccount(address, OnMissing.THROW);
        return account.getNumTreasuryTitles() > 0;
    }

    public boolean contractHasAnyBalance(final Address addressOrAlias) {
        final var address = mirrorEvmContractAliases.resolveForEvm(addressOrAlias);
        com.hedera.services.store.models.Account account = store.getAccount(address, OnMissing.THROW);
        return account.getNumPositiveBalances() > 0;
    }

    public boolean contractOwnsNfts(final Address addressOrAlias) {
        final var address = mirrorEvmContractAliases.resolveForEvm(addressOrAlias);
        com.hedera.services.store.models.Account account = store.getAccount(address, OnMissing.THROW);
        return account.getOwnedNfts() > 0;
    }
}
