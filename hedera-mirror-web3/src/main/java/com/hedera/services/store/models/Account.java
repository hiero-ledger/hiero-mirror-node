// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Account model from hedera-services.
 * <p>
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 * <p>
 * Differences with the original: 1. Removed fields like memo, key, isReceiverSigRequired, isSmartContract 2. Added
 * field accountAddress for convenience 3. Changed collection types to SortedMap and SortedSet 4. Added constructors and
 * set methods for creating new instances and achieve immutability 5. Added factory method that returns empty instance
 * 6. Added isEmptyAccount() method
 */
@Getter
public class Account extends HederaEvmAccount {

    public static final int UNLIMITED_AUTO_ASSOCIATIONS = -1;

    private static final Account EMPTY_ACCOUNT = new Account(0L, Id.DEFAULT, 0L);

    private final Address accountAddress;

    private final Supplier<SortedSet<FcTokenAllowanceId>> approveForAllNfts;

    private final long autoRenewSecs;

    private final Supplier<Long> balance;

    private final long createdTimestamp;

    private final Supplier<SortedMap<EntityNum, Long>> cryptoAllowances;

    private final boolean deleted;

    private final Long entityId;

    private final long ethereumNonce;

    private final long expiry;

    private final Supplier<SortedMap<FcTokenAllowanceId, Long>> fungibleTokenAllowances;

    private final Id id;

    private final boolean isSmartContract;

    private final JKey key;

    private final int maxAutoAssociations;

    private final Supplier<Integer> numAssociations;

    private final Supplier<Integer> numPositiveBalances;

    private final int numTreasuryTitles;

    private final Supplier<Long> ownedNfts;

    private final Id proxy;

    private final int usedAutoAssociations;

    @Builder(toBuilder = true)
    @SuppressWarnings("java:S107")
    public Account(
            ByteString alias,
            Long entityId,
            Id id,
            long expiry,
            Supplier<Long> balance,
            boolean deleted,
            Supplier<Long> ownedNfts,
            long autoRenewSecs,
            Id proxy,
            int maxAutoAssociations,
            Supplier<SortedMap<EntityNum, Long>> cryptoAllowances,
            Supplier<SortedMap<FcTokenAllowanceId, Long>> fungibleTokenAllowances,
            Supplier<SortedSet<FcTokenAllowanceId>> approveForAllNfts,
            Supplier<Integer> numAssociations,
            Supplier<Integer> numPositiveBalances,
            int numTreasuryTitles,
            long ethereumNonce,
            boolean isSmartContract,
            JKey key,
            long createdTimestamp,
            int usedAutoAssociations) {
        super(id.asEvmAddress());
        setAlias(alias);
        this.entityId = entityId;
        this.id = id;
        this.expiry = expiry;
        this.balance = balance;
        this.deleted = deleted;
        this.ownedNfts = ownedNfts;
        this.autoRenewSecs = autoRenewSecs;
        this.proxy = proxy;
        this.accountAddress = id.asEvmAddress();
        this.maxAutoAssociations = maxAutoAssociations;
        this.cryptoAllowances = cryptoAllowances;
        this.fungibleTokenAllowances = fungibleTokenAllowances;
        this.approveForAllNfts = approveForAllNfts;
        this.numAssociations = numAssociations;
        this.numPositiveBalances = numPositiveBalances;
        this.numTreasuryTitles = numTreasuryTitles;
        this.ethereumNonce = ethereumNonce;
        this.isSmartContract = isSmartContract;
        this.key = key;
        this.createdTimestamp = createdTimestamp;
        this.usedAutoAssociations = usedAutoAssociations;
    }

    /**
     * Create a partial account with only ID and balance values. Used for treasury accounts as those are the only fields
     * we need.
     */
    public Account(Long entityId, Id id, long balance) {
        this(
                ByteString.EMPTY,
                entityId,
                id,
                0L,
                () -> balance,
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
                0L,
                false,
                null,
                0L,
                0);
    }

    /**
     * Create a partial account with only alias, ID and balance values. Used for treasury accounts as those are the only
     * fields we need.
     */
    public Account(ByteString alias, Long entityId, Id id, long balance) {
        this(
                alias,
                entityId,
                id,
                0L,
                () -> balance,
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
                0L,
                false,
                null,
                0L,
                0);
    }

    public static Account getEmptyAccount() {
        return EMPTY_ACCOUNT;
    }

    public static Account getDummySenderAccount(Address senderAddress) {
        return new Account(
                0L, Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)), Long.MAX_VALUE);
    }

    public static Account getDummySenderAccountWithAlias(Address senderAddress) {
        return new Account(
                ByteString.copyFrom(senderAddress.toArray()),
                0L,
                Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)),
                Long.MAX_VALUE);
    }

    public Account autoAssociate() {
        final int updatedNumAssociations = getNumAssociations() + 1;
        return toBuilder()
                .numAssociations(() -> updatedNumAssociations)
                .usedAutoAssociations(usedAutoAssociations + 1)
                .build();
    }

    public boolean canAutoAssociate() {
        return maxAutoAssociations == UNLIMITED_AUTO_ASSOCIATIONS || usedAutoAssociations + 1 <= maxAutoAssociations;
    }

    public Account decrementUsedAutomaticAssociations() {
        return setUsedAutoAssociations(getUsedAutoAssociations() - 1);
    }

    public Long getBalance() {
        return balance != null && balance.get() != null ? balance.get() : 0L;
    }

    public Account setBalance(long balance) {
        return toBuilder().balance(() -> balance).build();
    }

    public Long getOwnedNfts() {
        return ownedNfts != null ? ownedNfts.get() : 0L;
    }

    public Account setOwnedNfts(long newOwnedNfts) {
        return toBuilder().ownedNfts(() -> newOwnedNfts).build();
    }

    public SortedMap<EntityNum, Long> getCryptoAllowances() {
        return cryptoAllowances != null ? cryptoAllowances.get() : Collections.emptySortedMap();
    }

    public SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
        return fungibleTokenAllowances != null ? fungibleTokenAllowances.get() : Collections.emptySortedMap();
    }

    public Account setFungibleTokenAllowances(SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        return toBuilder()
                .fungibleTokenAllowances(() -> fungibleTokenAllowances)
                .build();
    }

    public SortedSet<FcTokenAllowanceId> getApproveForAllNfts() {
        return approveForAllNfts != null ? approveForAllNfts.get() : Collections.emptySortedSet();
    }

    public Account setApproveForAllNfts(SortedSet<FcTokenAllowanceId> approveForAllNfts) {
        return toBuilder().approveForAllNfts(() -> approveForAllNfts).build();
    }

    public Integer getNumAssociations() {
        return numAssociations != null ? numAssociations.get() : 0;
    }

    public Account setNumAssociations(int numAssociations) {
        return toBuilder().numAssociations(() -> numAssociations).build();
    }

    public Integer getNumPositiveBalances() {
        return numPositiveBalances != null ? numPositiveBalances.get() : 0;
    }

    public Account setNumPositiveBalances(int newNumPositiveBalances) {
        return toBuilder().numPositiveBalances(() -> newNumPositiveBalances).build();
    }

    public boolean isAutoAssociateEnabled() {
        return maxAutoAssociations == -1 || maxAutoAssociations > 0;
    }

    public boolean isEmptyAccount() {
        return this.equals(getEmptyAccount());
    }

    public Account setCryptoAllowance(SortedMap<EntityNum, Long> cryptoAllowances) {
        return toBuilder().cryptoAllowances(() -> cryptoAllowances).build();
    }

    public Account setDeleted(boolean deleted) {
        return toBuilder().deleted(deleted).build();
    }

    public Account setExpiry(long expiry) {
        return toBuilder().expiry(expiry).build();
    }

    public Account setIsSmartContract(boolean isSmartContract) {
        return toBuilder().isSmartContract(isSmartContract).build();
    }

    public Account setMaxAutoAssociations(int maxAutoAssociations) {
        return toBuilder().maxAutoAssociations(maxAutoAssociations).build();
    }

    public Account setNumTreasuryTitles(int numTreasuryTitles) {
        return toBuilder().numTreasuryTitles(numTreasuryTitles).build();
    }

    public Account setUsedAutoAssociations(int usedCount) {
        validateTrue(isValidUsedCount(usedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
        return toBuilder().usedAutoAssociations(usedCount).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Account account = (Account) o;
        return getExpiry() == account.getExpiry()
                && isDeleted() == account.isDeleted()
                && getAutoRenewSecs() == account.getAutoRenewSecs()
                && getMaxAutoAssociations() == account.getMaxAutoAssociations()
                && getNumTreasuryTitles() == account.getNumTreasuryTitles()
                && getEthereumNonce() == account.getEthereumNonce()
                && isSmartContract() == account.isSmartContract()
                && getCreatedTimestamp() == account.getCreatedTimestamp()
                && Objects.equals(getEntityId(), account.getEntityId())
                && Objects.equals(getId(), account.getId())
                && Objects.equals(getBalance(), account.getBalance())
                && Objects.equals(getOwnedNfts(), account.getOwnedNfts())
                && Objects.equals(getProxy(), account.getProxy())
                && Objects.equals(getAccountAddress(), account.getAccountAddress())
                && getCryptoAllowances().equals(account.getCryptoAllowances())
                && getFungibleTokenAllowances().equals(account.getFungibleTokenAllowances())
                && getApproveForAllNfts().equals(account.getApproveForAllNfts())
                && Objects.equals(getNumAssociations(), account.getNumAssociations())
                && Objects.equals(getNumPositiveBalances(), account.getNumPositiveBalances())
                && Objects.equals(getKey(), account.getKey())
                && getUsedAutoAssociations() == account.getUsedAutoAssociations();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getAlias(),
                getEntityId(),
                getId(),
                getExpiry(),
                getBalance(),
                isDeleted(),
                getOwnedNfts(),
                getAutoRenewSecs(),
                getProxy(),
                getAccountAddress(),
                getMaxAutoAssociations(),
                getCryptoAllowances(),
                getFungibleTokenAllowances(),
                getApproveForAllNfts(),
                getNumAssociations(),
                getNumPositiveBalances(),
                getNumTreasuryTitles(),
                getEthereumNonce(),
                isSmartContract(),
                getKey(),
                getCreatedTimestamp(),
                getUsedAutoAssociations());
    }

    @Override
    public String toString() {
        return "Account{" + "entityId="
                + entityId + ", id="
                + id + ", alias="
                + alias.toStringUtf8() + ", address="
                + address + ", expiry="
                + expiry + ", balance="
                + getBalance() + ", deleted="
                + deleted + ", ownedNfts="
                + getOwnedNfts() + ", autoRenewSecs="
                + autoRenewSecs + ", proxy="
                + proxy + ", accountAddress="
                + accountAddress + ", maxAutoAssociations="
                + maxAutoAssociations + ", cryptoAllowances="
                + getCryptoAllowances() + ", fungibleTokenAllowances="
                + getFungibleTokenAllowances() + ", approveForAllNfts="
                + getApproveForAllNfts() + ", numAssociations="
                + getNumAssociations() + ", numPositiveBalances="
                + getNumPositiveBalances() + ", numTreasuryTitles="
                + numTreasuryTitles + ", ethereumNonce="
                + ethereumNonce + ", isSmartContract="
                + isSmartContract + ", key="
                + key + ", createdTimestamp="
                + createdTimestamp + ", usedAutoAssociations="
                + usedAutoAssociations + "}";
    }

    private boolean isValidUsedCount(int usedCount) {
        return usedCount >= 0
                && (usedCount <= maxAutoAssociations || maxAutoAssociations == UNLIMITED_AUTO_ASSOCIATIONS);
    }
}
