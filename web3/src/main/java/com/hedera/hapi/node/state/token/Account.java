// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.JsonCodec;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Copied PBJ model from the Hedera Services with added Supplier fields for lazy loading. This model will be deleted as part of future enhancement.
 *
 * Representation of a Hedera Token Service account entity in the network Merkle tree.
 * <p>
 * As with all network entities, account has a unique entity number represented as shard.realm.X.
 * X can be an alias public key or an EVM address or a number.
 *
 * @param accountId <b>(1)</b> The unique entity id of the account.,
 * @param alias <b>(2)</b> The alias to use for this account, if any.,
 * @param key <b>(3)</b> (Optional) The key to be used to sign transactions from the account, if any.
 *            This key will not be set for hollow accounts until the account is finalized.
 *            This key should be set on all the accounts, except for immutable accounts (0.0.800 and 0.0.801).,
 * @param expirationSecond <b>(4)</b> The expiration time of the account, in seconds since the epoch.,
 * @param tinybarBalanceSupplier <b>(5)</b> The balance of the account, in tiny-bars wrapped in a supplier.,
 * @param memo <b>(6)</b> An optional description of the account with UTF-8 encoding up to 100 bytes.,
 * @param deleted <b>(7)</b> A boolean marking if the account has been deleted.,
 * @param stakedToMe <b>(8)</b> The amount of hbars staked to the account.,
 * @param stakePeriodStart <b>(9)</b> If this account stakes to another account, its value will be -1. It will
 *                         be set to the time when the account starts staking to a node.,
 * @param stakedId <b>(10, 11)</b> ID of the account or node to which this account is staking.,
 * @param declineReward <b>(12)</b> A boolean marking if the account declines rewards.,
 * @param receiverSigRequired <b>(13)</b> A boolean marking if the account requires a receiver signature.,
 * @param headTokenId <b>(14)</b> The token ID of the head of the linked list from token relations map for the account.,
 * @param headNftId <b>(15)</b> The NftID of the head of the linked list from unique tokens map for the account.,
 * @param headNftSerialNumber <b>(16)</b> The serial number of the head NftID of the linked list from unique tokens map for the account.,
 * @param numberOwnedNftsSupplier <b>(17)</b> The number of NFTs owned by the account wrapped in a supplier.,
 * @param maxAutoAssociations <b>(18)</b> The maximum number of tokens that can be auto-associated with the account.,
 * @param usedAutoAssociations <b>(19)</b> The number of used auto-association slots.,
 * @param numberAssociationsSupplier <b>(20)</b> The number of tokens associated with the account wrapped in a supplier. This number is used for
 *  *                           fee calculation during renewal of the account.,
 * @param smartContract <b>(21)</b> A boolean marking if the account is a smart contract.,
 * @param numberPositiveBalancesSupplier <b>(22)</b> The number of tokens with a positive balance associated with the account wrapped in a supplier.
 *  *                               If the account has positive balance in a token, it can not be deleted.,
 * @param ethereumNonce <b>(23)</b> The nonce of the account, used for Ethereum interoperability.,
 * @param stakeAtStartOfLastRewardedPeriod <b>(24)</b> The amount of hbars staked to the account at the start of the last rewarded period.,
 * @param autoRenewAccountId <b>(25)</b> (Optional) The id of an auto-renew account, in the same shard and realm as the account, that
 *                           has signed a transaction allowing the network to use its balance to automatically extend the account's
 *                           expiration time when it passes.,
 * @param autoRenewSeconds <b>(26)</b> The number of seconds the network should automatically extend the account's expiration by, if the
 *                         account has a valid auto-renew account, and is not deleted upon expiration.
 *                         If this is not provided in an allowed range on account creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD.
 *                         The default values for the minimum period and maximum period are 30 days and 90 days, respectively.,
 * @param contractKvPairsNumber <b>(27)</b> If this account is a smart-contract, number of key-value pairs stored on the contract.
 *                              This is used to determine the storage rent for the contract.,
 * @param cryptoAllowancesSupplier <b>(28)</b> (Optional) List of crypto allowances approved by the account in a supplier.
 *  *                         It contains account number for which the allowance is approved to and
 *  *                         the amount approved for that account.,
 * @param approveForAllNftAllowancesSupplier <b>(29)</b> (Optional) List of non-fungible token allowances approved for all by the account in a supplier.
 *  *                                   It contains account number approved for spending all serial numbers for the given
 *  *                                   NFT token number using approved_for_all flag.
 *  *                                   Allowances for a specific serial number is stored in the NFT itself in state.,
 * @param tokenAllowancesSupplier <b>(30)</b> (Optional) List of fungible token allowances approved by the account in a supplier.
 *  *                        It contains account number for which the allowance is approved to and  the token number.
 *  *                        It also contains and the amount approved for that account.,
 * @param numberTreasuryTitles <b>(31)</b> The number of tokens for which this account is treasury,
 * @param expiredAndPendingRemoval <b>(32)</b> A flag indicating if the account is expired and pending removal.
 *                                 Only the entity expiration system task toggles this flag when it reaches this account
 *                                 and finds it expired. Before setting the flag the system task checks if the account has
 *                                 an auto-renew account with balance. This is done to prevent a zero-balance account with a funded
 *                                 auto-renew account from being treated as expired in the interval between its expiration
 *                                 and the time the system task actually auto-renews it.,
 * @param firstContractStorageKey <b>(33)</b> The first key in the doubly-linked list of this contract's storage mappings;
 *                                It will be null if if the account is not a contract or the contract has no storage mappings.,
 * @param headPendingAirdropId <b>(34)</b> A pending airdrop ID at the head of the linked list for this account
 *                             from the account airdrops map.<br/>
 *                             The account airdrops are connected by including the "next" and "previous"
 *                             `PendingAirdropID` in each `AccountAirdrop` message.
 *                             <p>
 *                             This value SHALL NOT be empty if this account is "sender" for any
 *                             pending airdrop, and SHALL be empty otherwise.
 * @param numberPendingAirdrops <b>(35)</b> A number of pending airdrops.
 *                              <p>
 *                              This count SHALL be used to calculate rent _without_ walking the linked
 *                              list of pending airdrops associated to this account via the
 *                              `head_pending_airdrop_id` field.</p><br/>
 *                              This value MUST be updated for every airdrop, clam, or cancel transaction
 *                              that designates this account as a receiver.<br/>
 *                              This number MUST always match the count of entries in the "list"
 *                              identified by `head_pending_airdrop_id`.
 * @param numberHooksInUse <b>(36)</b> The number of hooks currently in use on this account.
 * @param firstHookId <b>(37)</b> If the account has more than zero hooks in use, the id of the first hook in its
 *                    doubly-linked list of hooks.
 * @param numberLambdaStorageSlots <b>(38)</b> The number of storage slots in use by this account's lambdas.
 */
public record Account(
        @Nullable AccountID accountId,
        @Nonnull Bytes alias,
        @Nullable Key key,
        long expirationSecond,
        Supplier<Long> tinybarBalanceSupplier,
        @Nonnull String memo,
        boolean deleted,
        long stakedToMe,
        long stakePeriodStart,
        OneOf<StakedIdOneOfType> stakedId,
        boolean declineReward,
        boolean receiverSigRequired,
        @Nullable TokenID headTokenId,
        @Nullable NftID headNftId,
        long headNftSerialNumber,
        Supplier<Long> numberOwnedNftsSupplier,
        int maxAutoAssociations,
        int usedAutoAssociations,
        Supplier<Integer> numberAssociationsSupplier,
        boolean smartContract,
        Supplier<Integer> numberPositiveBalancesSupplier,
        long ethereumNonce,
        long stakeAtStartOfLastRewardedPeriod,
        @Nullable AccountID autoRenewAccountId,
        long autoRenewSeconds,
        int contractKvPairsNumber,
        @Nonnull Supplier<List<AccountCryptoAllowance>> cryptoAllowancesSupplier,
        @Nonnull Supplier<List<AccountApprovalForAllAllowance>> approveForAllNftAllowancesSupplier,
        @Nonnull Supplier<List<AccountFungibleTokenAllowance>> tokenAllowancesSupplier,
        int numberTreasuryTitles,
        boolean expiredAndPendingRemoval,
        @Nonnull Bytes firstContractStorageKey,
        @Nullable PendingAirdropId headPendingAirdropId,
        long numberPendingAirdrops,
        long numberHooksInUse,
        long firstHookId,
        long numberLambdaStorageSlots) {
    /** Protobuf codec for reading and writing in protobuf format */
    public static final Codec<Account> PROTOBUF = new com.hedera.hapi.node.state.token.codec.AccountProtoCodec();
    /** JSON codec for reading and writing in JSON format */
    public static final JsonCodec<Account> JSON = new com.hedera.hapi.node.state.token.codec.AccountJsonCodec();

    /** Default instance with all fields set to default values */
    public static final Account DEFAULT = newBuilder().build();

    private static final Supplier<Long> DEFAULT_LONG_SUPPLIER = () -> 0L;
    private static final Supplier<Integer> DEFAULT_INTEGER_SUPPLIER = () -> 0;

    public Account(
            AccountID accountId,
            Bytes alias,
            Key key,
            long expirationSecond,
            long tinybarBalance,
            String memo,
            boolean deleted,
            long stakedToMe,
            long stakePeriodStart,
            OneOf<Account.StakedIdOneOfType> stakedId,
            boolean declineReward,
            boolean receiverSigRequired,
            TokenID headTokenId,
            NftID headNftId,
            long headNftSerialNumber,
            long numberOwnedNfts,
            int maxAutoAssociations,
            int usedAutoAssociations,
            int numberAssociations,
            boolean smartContract,
            int numberPositiveBalances,
            long ethereumNonce,
            long stakeAtStartOfLastRewardedPeriod,
            AccountID autoRenewAccountId,
            long autoRenewSeconds,
            int contractKvPairsNumber,
            List<AccountCryptoAllowance> cryptoAllowances,
            List<AccountApprovalForAllAllowance> approveForAllNftAllowances,
            List<AccountFungibleTokenAllowance> tokenAllowances,
            int numberTreasuryTitles,
            boolean expiredAndPendingRemoval,
            Bytes firstContractStorageKey,
            PendingAirdropId headPendingAirdropId,
            long numberPendingAirdrops,
            long numberHooksInUse,
            long firstHookId,
            long numberLambdaStorageSlots) {
        this(
                accountId,
                alias,
                key,
                expirationSecond,
                () -> tinybarBalance,
                memo,
                deleted,
                stakedToMe,
                stakePeriodStart,
                stakedId,
                declineReward,
                receiverSigRequired,
                headTokenId,
                headNftId,
                headNftSerialNumber,
                () -> numberOwnedNfts,
                maxAutoAssociations,
                usedAutoAssociations,
                () -> numberAssociations,
                smartContract,
                () -> numberPositiveBalances,
                ethereumNonce,
                stakeAtStartOfLastRewardedPeriod,
                autoRenewAccountId,
                autoRenewSeconds,
                contractKvPairsNumber,
                () -> cryptoAllowances,
                () -> approveForAllNftAllowances,
                () -> tokenAllowances,
                numberTreasuryTitles,
                expiredAndPendingRemoval,
                firstContractStorageKey,
                headPendingAirdropId,
                numberPendingAirdrops,
                numberHooksInUse,
                firstHookId,
                numberLambdaStorageSlots);
    }

    /**
     * Return a new builder for building a model object. This is just a shortcut for <code>new Model.Builder()</code>.
     *
     * @return a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Override the default hashCode method for
     * all other objects to make hashCode
     */
    @Override
    public int hashCode() {
        int result = 1;
        if (accountId != null && !accountId.equals(DEFAULT.accountId)) {
            result = 31 * result + accountId.hashCode();
        }
        if (!alias.equals(DEFAULT.alias)) {
            result = 31 * result + alias.hashCode();
        }
        if (key != null && !key.equals(DEFAULT.key)) {
            result = 31 * result + key.hashCode();
        }
        if (expirationSecond != DEFAULT.expirationSecond) {
            result = 31 * result + Long.hashCode(expirationSecond);
        }
        if (tinybarBalanceSupplier != null
                && DEFAULT.tinybarBalanceSupplier != null
                && !tinybarBalanceSupplier.get().equals(DEFAULT.tinybarBalanceSupplier.get())) {
            result = 31 * result + Long.hashCode(tinybarBalanceSupplier.get());
        }
        if (!memo.equals(DEFAULT.memo)) {
            result = 31 * result + memo.hashCode();
        }
        if (deleted != DEFAULT.deleted) {
            result = 31 * result + Boolean.hashCode(deleted);
        }
        if (stakedToMe != DEFAULT.stakedToMe) {
            result = 31 * result + Long.hashCode(stakedToMe);
        }
        if (stakePeriodStart != DEFAULT.stakePeriodStart) {
            result = 31 * result + Long.hashCode(stakePeriodStart);
        }
        if (stakedId != null && !stakedId.equals(DEFAULT.stakedId)) {
            result = 31 * result + stakedId.hashCode();
        }
        if (declineReward != DEFAULT.declineReward) {
            result = 31 * result + Boolean.hashCode(declineReward);
        }
        if (receiverSigRequired != DEFAULT.receiverSigRequired) {
            result = 31 * result + Boolean.hashCode(receiverSigRequired);
        }
        if (headTokenId != null && !headTokenId.equals(DEFAULT.headTokenId)) {
            result = 31 * result + headTokenId.hashCode();
        }
        if (headNftId != null && !headNftId.equals(DEFAULT.headNftId)) {
            result = 31 * result + headNftId.hashCode();
        }
        if (headNftSerialNumber != DEFAULT.headNftSerialNumber) {
            result = 31 * result + Long.hashCode(headNftSerialNumber);
        }
        if (numberOwnedNftsSupplier != null
                && DEFAULT.numberOwnedNftsSupplier != null
                && !numberOwnedNftsSupplier.get().equals(DEFAULT.numberOwnedNftsSupplier.get())) {
            result = 31 * result + Long.hashCode(numberOwnedNftsSupplier.get());
        }
        if (maxAutoAssociations != DEFAULT.maxAutoAssociations) {
            result = 31 * result + Integer.hashCode(maxAutoAssociations);
        }
        if (usedAutoAssociations != DEFAULT.usedAutoAssociations) {
            result = 31 * result + Integer.hashCode(usedAutoAssociations);
        }
        if (numberAssociationsSupplier != null
                && DEFAULT.numberAssociationsSupplier != null
                && !numberAssociationsSupplier.get().equals(DEFAULT.numberAssociationsSupplier.get())) {
            result = 31 * result + Integer.hashCode(numberAssociationsSupplier.get());
        }
        if (smartContract != DEFAULT.smartContract) {
            result = 31 * result + Boolean.hashCode(smartContract);
        }
        if (numberPositiveBalancesSupplier != null
                && DEFAULT.numberPositiveBalancesSupplier != null
                && !numberPositiveBalancesSupplier.get().equals(DEFAULT.numberPositiveBalancesSupplier.get())) {
            result = 31 * result + Integer.hashCode(numberPositiveBalancesSupplier.get());
        }
        if (ethereumNonce != DEFAULT.ethereumNonce) {
            result = 31 * result + Long.hashCode(ethereumNonce);
        }
        if (stakeAtStartOfLastRewardedPeriod != DEFAULT.stakeAtStartOfLastRewardedPeriod) {
            result = 31 * result + Long.hashCode(stakeAtStartOfLastRewardedPeriod);
        }
        if (autoRenewAccountId != null && !autoRenewAccountId.equals(DEFAULT.autoRenewAccountId)) {
            result = 31 * result + autoRenewAccountId.hashCode();
        }
        if (autoRenewSeconds != DEFAULT.autoRenewSeconds) {
            result = 31 * result + Long.hashCode(autoRenewSeconds);
        }
        if (contractKvPairsNumber != DEFAULT.contractKvPairsNumber) {
            result = 31 * result + Integer.hashCode(contractKvPairsNumber);
        }

        for (Object o : cryptoAllowancesSupplier.get()) {
            if (o != null) {
                result = 31 * result + o.hashCode();
            } else {
                result = 31 * result;
            }
        }
        for (Object o : approveForAllNftAllowancesSupplier.get()) {
            if (o != null) {
                result = 31 * result + o.hashCode();
            } else {
                result = 31 * result;
            }
        }
        for (Object o : tokenAllowancesSupplier.get()) {
            if (o != null) {
                result = 31 * result + o.hashCode();
            } else {
                result = 31 * result;
            }
        }
        if (numberTreasuryTitles != DEFAULT.numberTreasuryTitles) {
            result = 31 * result + Integer.hashCode(numberTreasuryTitles);
        }
        if (expiredAndPendingRemoval != DEFAULT.expiredAndPendingRemoval) {
            result = 31 * result + Boolean.hashCode(expiredAndPendingRemoval);
        }
        if (!firstContractStorageKey.equals(DEFAULT.firstContractStorageKey)) {
            result = 31 * result + firstContractStorageKey.hashCode();
        }
        if (headPendingAirdropId != null && !headPendingAirdropId.equals(DEFAULT.headPendingAirdropId)) {
            result = 31 * result + headPendingAirdropId.hashCode();
        }
        if (numberPendingAirdrops != DEFAULT.numberPendingAirdrops) {
            result = 31 * result + Long.hashCode(numberPendingAirdrops);
        }
        if (numberHooksInUse != DEFAULT.numberHooksInUse) {
            result = 31 * result + Long.hashCode(numberHooksInUse);
        }
        if (firstHookId != DEFAULT.firstHookId) {
            result = 31 * result + Long.hashCode(firstHookId);
        }
        if (numberLambdaStorageSlots != DEFAULT.numberLambdaStorageSlots) {
            result = 31 * result + Long.hashCode(numberLambdaStorageSlots);
        }
        long hashCode = result;
        // Shifts: 30, 27, 16, 20, 5, 18, 10, 24, 30
        hashCode += hashCode << 30;
        hashCode ^= hashCode >>> 27;
        hashCode += hashCode << 16;
        hashCode ^= hashCode >>> 20;
        hashCode += hashCode << 5;
        hashCode ^= hashCode >>> 18;
        hashCode += hashCode << 10;
        hashCode ^= hashCode >>> 24;
        hashCode += hashCode << 30;

        return (int) hashCode;
    }

    /**
     * Override the default equals method for
     */
    @Override
    public boolean equals(Object that) {
        if (that == null || this.getClass() != that.getClass()) {
            return false;
        }
        Account thatObj = (Account) that;
        if (accountId == null && thatObj.accountId != null) {
            return false;
        }
        if (accountId != null && !accountId.equals(thatObj.accountId)) {
            return false;
        }
        if (!alias.equals(thatObj.alias)) {
            return false;
        }
        if (key == null && thatObj.key != null) {
            return false;
        }
        if (key != null && !key.equals(thatObj.key)) {
            return false;
        }
        if (expirationSecond != thatObj.expirationSecond) {
            return false;
        }
        if (tinybarBalanceSupplier == null && thatObj.tinybarBalanceSupplier != null) {
            return false;
        }
        if (tinybarBalanceSupplier != null && thatObj.tinybarBalanceSupplier == null) {
            return false;
        }
        if (tinybarBalanceSupplier != null
                && !tinybarBalanceSupplier.get().equals(thatObj.tinybarBalanceSupplier.get())) {
            return false;
        }
        if (!memo.equals(thatObj.memo)) {
            return false;
        }
        if (deleted != thatObj.deleted) {
            return false;
        }
        if (stakedToMe != thatObj.stakedToMe) {
            return false;
        }
        if (stakePeriodStart != thatObj.stakePeriodStart) {
            return false;
        }
        if (stakedId == null && thatObj.stakedId != null) {
            return false;
        }
        if (stakedId != null && !stakedId.equals(thatObj.stakedId)) {
            return false;
        }
        if (declineReward != thatObj.declineReward) {
            return false;
        }
        if (receiverSigRequired != thatObj.receiverSigRequired) {
            return false;
        }
        if (headTokenId == null && thatObj.headTokenId != null) {
            return false;
        }
        if (headTokenId != null && !headTokenId.equals(thatObj.headTokenId)) {
            return false;
        }
        if (headNftId == null && thatObj.headNftId != null) {
            return false;
        }
        if (headNftId != null && !headNftId.equals(thatObj.headNftId)) {
            return false;
        }
        if (headNftSerialNumber != thatObj.headNftSerialNumber) {
            return false;
        }
        if (numberOwnedNftsSupplier == null && thatObj.numberOwnedNftsSupplier != null) {
            return false;
        }
        if (numberOwnedNftsSupplier != null && thatObj.numberOwnedNftsSupplier == null) {
            return false;
        }
        if (numberOwnedNftsSupplier != null
                && !numberOwnedNftsSupplier.get().equals(thatObj.numberOwnedNftsSupplier.get())) {
            return false;
        }
        if (maxAutoAssociations != thatObj.maxAutoAssociations) {
            return false;
        }
        if (usedAutoAssociations != thatObj.usedAutoAssociations) {
            return false;
        }
        if (numberAssociationsSupplier == null && thatObj.numberAssociationsSupplier != null) {
            return false;
        }
        if (numberAssociationsSupplier != null && thatObj.numberAssociationsSupplier == null) {
            return false;
        }
        if (numberAssociationsSupplier != null
                && !numberAssociationsSupplier.get().equals(thatObj.numberAssociationsSupplier.get())) {
            return false;
        }
        if (smartContract != thatObj.smartContract) {
            return false;
        }
        if (numberPositiveBalancesSupplier == null && thatObj.numberPositiveBalancesSupplier != null) {
            return false;
        }
        if (numberPositiveBalancesSupplier != null && thatObj.numberPositiveBalancesSupplier == null) {
            return false;
        }
        if (numberPositiveBalancesSupplier != null
                && !numberPositiveBalancesSupplier.get().equals(thatObj.numberPositiveBalancesSupplier.get())) {
            return false;
        }
        if (ethereumNonce != thatObj.ethereumNonce) {
            return false;
        }
        if (stakeAtStartOfLastRewardedPeriod != thatObj.stakeAtStartOfLastRewardedPeriod) {
            return false;
        }
        if (autoRenewAccountId == null && thatObj.autoRenewAccountId != null) {
            return false;
        }
        if (autoRenewAccountId != null && !autoRenewAccountId.equals(thatObj.autoRenewAccountId)) {
            return false;
        }
        if (autoRenewSeconds != thatObj.autoRenewSeconds) {
            return false;
        }
        if (contractKvPairsNumber != thatObj.contractKvPairsNumber) {
            return false;
        }
        if (!cryptoAllowancesSupplier.get().equals(thatObj.cryptoAllowancesSupplier.get())) {
            return false;
        }
        if (!approveForAllNftAllowancesSupplier.get().equals(thatObj.approveForAllNftAllowancesSupplier.get())) {
            return false;
        }
        if (!tokenAllowancesSupplier.get().equals(thatObj.tokenAllowancesSupplier.get())) {
            return false;
        }
        if (numberTreasuryTitles != thatObj.numberTreasuryTitles) {
            return false;
        }
        if (expiredAndPendingRemoval != thatObj.expiredAndPendingRemoval) {
            return false;
        }
        if (!firstContractStorageKey.equals(thatObj.firstContractStorageKey)) {
            return false;
        }
        if (headPendingAirdropId == null && thatObj.headPendingAirdropId != null) {
            return false;
        }
        if (headPendingAirdropId != null && !headPendingAirdropId.equals(thatObj.headPendingAirdropId)) {
            return false;
        }
        if (numberPendingAirdrops != thatObj.numberPendingAirdrops) {
            return false;
        }
        if (numberHooksInUse != thatObj.numberHooksInUse) {
            return false;
        }
        if (firstHookId != thatObj.firstHookId) {
            return false;
        }
        return numberLambdaStorageSlots == thatObj.numberLambdaStorageSlots;
    }

    @Override
    public String toString() {
        return alias.toString();
    }

    /**
     * Convenience method to check if the accountId has a value
     *
     * @return true of the accountId has a value
     */
    public boolean hasAccountId() {
        return accountId != null;
    }

    /**
     * Gets the value for accountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if accountId is null
     * @return the value for accountId if it has a value, or else returns the default value
     */
    public AccountID accountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasAccountId() ? accountId : defaultValue;
    }

    /**
     * Gets the value for accountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for accountId if it has a value
     * @throws NullPointerException if accountId is null
     */
    public @Nonnull AccountID accountIdOrThrow() {
        return requireNonNull(accountId, "Field accountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the accountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasAccountId()) {
            ifPresent.accept(accountId);
        }
    }

    /**
     * Convenience method to check if the key has a value
     *
     * @return true of the key has a value
     */
    public boolean hasKey() {
        return key != null;
    }

    /**
     * Gets the value for key if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if key is null
     * @return the value for key if it has a value, or else returns the default value
     */
    public Key keyOrElse(@Nonnull final Key defaultValue) {
        return hasKey() ? key : defaultValue;
    }

    /**
     * Gets the value for key if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for key if it has a value
     * @throws NullPointerException if key is null
     */
    public @Nonnull Key keyOrThrow() {
        return requireNonNull(key, "Field key is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the key has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasKey()) {
            ifPresent.accept(key);
        }
    }

    /**
     * Convenience method to check if the headTokenId has a value
     *
     * @return true of the headTokenId has a value
     */
    public boolean hasHeadTokenId() {
        return headTokenId != null;
    }

    /**
     * Gets the value for headTokenId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if headTokenId is null
     * @return the value for headTokenId if it has a value, or else returns the default value
     */
    public TokenID headTokenIdOrElse(@Nonnull final TokenID defaultValue) {
        return hasHeadTokenId() ? headTokenId : defaultValue;
    }

    /**
     * Gets the value for headTokenId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for headTokenId if it has a value
     * @throws NullPointerException if headTokenId is null
     */
    public @Nonnull TokenID headTokenIdOrThrow() {
        return requireNonNull(headTokenId, "Field headTokenId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the headTokenId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifHeadTokenId(@Nonnull final Consumer<TokenID> ifPresent) {
        if (hasHeadTokenId()) {
            ifPresent.accept(headTokenId);
        }
    }

    /**
     * Convenience method to check if the headNftId has a value
     *
     * @return true of the headNftId has a value
     */
    public boolean hasHeadNftId() {
        return headNftId != null;
    }

    /**
     * Gets the value for headNftId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if headNftId is null
     * @return the value for headNftId if it has a value, or else returns the default value
     */
    public NftID headNftIdOrElse(@Nonnull final NftID defaultValue) {
        return hasHeadNftId() ? headNftId : defaultValue;
    }

    /**
     * Gets the value for headNftId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for headNftId if it has a value
     * @throws NullPointerException if headNftId is null
     */
    public @Nonnull NftID headNftIdOrThrow() {
        return requireNonNull(headNftId, "Field headNftId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the headNftId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifHeadNftId(@Nonnull final Consumer<NftID> ifPresent) {
        if (hasHeadNftId()) {
            ifPresent.accept(headNftId);
        }
    }

    /**
     * Convenience method to check if the autoRenewAccountId has a value
     *
     * @return true of the autoRenewAccountId has a value
     */
    public boolean hasAutoRenewAccountId() {
        return autoRenewAccountId != null;
    }

    /**
     * Gets the value for autoRenewAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if autoRenewAccountId is null
     * @return the value for autoRenewAccountId if it has a value, or else returns the default value
     */
    public AccountID autoRenewAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasAutoRenewAccountId() ? autoRenewAccountId : defaultValue;
    }

    /**
     * Gets the value for autoRenewAccountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for autoRenewAccountId if it has a value
     * @throws NullPointerException if autoRenewAccountId is null
     */
    public @Nonnull AccountID autoRenewAccountIdOrThrow() {
        return requireNonNull(autoRenewAccountId, "Field autoRenewAccountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the autoRenewAccountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifAutoRenewAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasAutoRenewAccountId()) {
            ifPresent.accept(autoRenewAccountId);
        }
    }

    /**
     * Convenience method to check if the headPendingAirdropId has a value
     *
     * @return true of the headPendingAirdropId has a value
     */
    public boolean hasHeadPendingAirdropId() {
        return headPendingAirdropId != null;
    }

    /**
     * Gets the value for headPendingAirdropId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if headPendingAirdropId is null
     * @return the value for headPendingAirdropId if it has a value, or else returns the default value
     */
    public PendingAirdropId headPendingAirdropIdOrElse(@Nonnull final PendingAirdropId defaultValue) {
        return hasHeadPendingAirdropId() ? headPendingAirdropId : defaultValue;
    }

    /**
     * Gets the value for headPendingAirdropId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for headPendingAirdropId if it has a value
     * @throws NullPointerException if headPendingAirdropId is null
     */
    public @Nonnull PendingAirdropId headPendingAirdropIdOrThrow() {
        return requireNonNull(headPendingAirdropId, "Field headPendingAirdropId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the headPendingAirdropId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifHeadPendingAirdropId(@Nonnull final Consumer<PendingAirdropId> ifPresent) {
        if (hasHeadPendingAirdropId()) {
            ifPresent.accept(headPendingAirdropId);
        }
    }

    /**
     * Direct typed getter for one of field stakedAccountId.
     *
     * @return one of value or null if one of is not set or a different one of value
     */
    public @Nullable AccountID stakedAccountId() {
        return stakedId.kind() == StakedIdOneOfType.STAKED_ACCOUNT_ID ? (AccountID) stakedId.value() : null;
    }

    /**
     * Convenience method to check if the stakedId has a one-of with type STAKED_ACCOUNT_ID
     *
     * @return true of the one of kind is STAKED_ACCOUNT_ID
     */
    public boolean hasStakedAccountId() {
        return stakedId.kind() == StakedIdOneOfType.STAKED_ACCOUNT_ID;
    }

    /**
     * Gets the value for stakedAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if stakedAccountId is null
     * @return the value for stakedAccountId if it has a value, or else returns the default value
     */
    public AccountID stakedAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasStakedAccountId() ? stakedAccountId() : defaultValue;
    }

    /**
     * Gets the value for stakedAccountId if it was set, or throws a NullPointerException if it was not set.
     *
     * @return the value for stakedAccountId if it has a value
     * @throws NullPointerException if stakedAccountId is null
     */
    public @Nonnull AccountID stakedAccountIdOrThrow() {
        return requireNonNull(stakedAccountId(), "Field stakedAccountId is null");
    }

    /**
     * Direct typed getter for one of field stakedNodeId.
     *
     * @return one of value or null if one of is not set or a different one of value
     */
    public @Nullable Long stakedNodeId() {
        return stakedId.kind() == StakedIdOneOfType.STAKED_NODE_ID ? (Long) stakedId.value() : null;
    }

    /**
     * Convenience method to check if the stakedId has a one-of with type STAKED_NODE_ID
     *
     * @return true of the one of kind is STAKED_NODE_ID
     */
    public boolean hasStakedNodeId() {
        return stakedId.kind() == StakedIdOneOfType.STAKED_NODE_ID;
    }

    /**
     * Gets the value for stakedNodeId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if stakedNodeId is null
     * @return the value for stakedNodeId if it has a value, or else returns the default value
     */
    public Long stakedNodeIdOrElse(@Nonnull final Long defaultValue) {
        return hasStakedNodeId() ? stakedNodeId() : defaultValue;
    }

    /**
     * Gets the value for stakedNodeId if it was set, or throws a NullPointerException if it was not set.
     *
     * @return the value for stakedNodeId if it has a value
     * @throws NullPointerException if stakedNodeId is null
     */
    public @Nonnull Long stakedNodeIdOrThrow() {
        return requireNonNull(stakedNodeId(), "Field stakedNodeId is null");
    }

    /**
     * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
     * model object.
     *
     * @return a pre-populated builder
     */
    public Builder copyBuilder() {
        return new Builder(
                accountId,
                alias,
                key,
                expirationSecond,
                tinybarBalanceSupplier,
                memo,
                deleted,
                stakedToMe,
                stakePeriodStart,
                stakedId,
                declineReward,
                receiverSigRequired,
                headTokenId,
                headNftId,
                headNftSerialNumber,
                numberOwnedNftsSupplier,
                maxAutoAssociations,
                usedAutoAssociations,
                numberAssociationsSupplier,
                smartContract,
                numberPositiveBalancesSupplier,
                ethereumNonce,
                stakeAtStartOfLastRewardedPeriod,
                autoRenewAccountId,
                autoRenewSeconds,
                contractKvPairsNumber,
                cryptoAllowancesSupplier,
                approveForAllNftAllowancesSupplier,
                tokenAllowancesSupplier,
                numberTreasuryTitles,
                expiredAndPendingRemoval,
                firstContractStorageKey,
                headPendingAirdropId,
                numberPendingAirdrops,
                numberHooksInUse,
                firstHookId,
                numberLambdaStorageSlots);
    }

    public long tinybarBalance() {
        return tinybarBalanceSupplier.get();
    }

    public int numberAssociations() {
        return numberAssociationsSupplier.get();
    }

    public int numberPositiveBalances() {
        return numberPositiveBalancesSupplier.get();
    }

    public long numberOwnedNfts() {
        return numberOwnedNftsSupplier.get();
    }

    public List<AccountCryptoAllowance> cryptoAllowances() {
        return cryptoAllowancesSupplier.get();
    }

    public List<AccountApprovalForAllAllowance> approveForAllNftAllowances() {
        return approveForAllNftAllowancesSupplier.get();
    }

    public List<AccountFungibleTokenAllowance> tokenAllowances() {
        return tokenAllowancesSupplier.get();
    }

    /**
     * Enum for the type of "staked_id" oneof value
     */
    public enum StakedIdOneOfType implements com.hedera.pbj.runtime.EnumWithProtoMetadata {
        /**
         * Enum value for a unset OneOf, to avoid null OneOfs
         */
        UNSET(-1, "UNSET"),

        /**<b>(10)</b> ID of the new account to which this account is staking. If set to the sentinel <code>0.0.0</code> AccountID,
         * this field removes this account's staked account ID.
         */
        STAKED_ACCOUNT_ID(10, "staked_account_id"),

        /**<b>(11)</b> ID of the new node this account is staked to. If set to the sentinel <code>-1</code>, this field
         * removes this account's staked node ID.
         */
        STAKED_NODE_ID(11, "staked_node_id");

        /** The field ordinal in protobuf for this type */
        private final int protoOrdinal;

        /** The original field name in protobuf for this type */
        private final String protoName;

        /**
         * OneOf Type Enum Constructor
         *
         * @param protoOrdinal The oneof field ordinal in protobuf for this type
         * @param protoName The original field name in protobuf for this type
         */
        StakedIdOneOfType(final int protoOrdinal, String protoName) {
            this.protoOrdinal = protoOrdinal;
            this.protoName = protoName;
        }

        /**
         * Get enum from protobuf ordinal
         *
         * @param ordinal the protobuf ordinal number
         * @return enum for matching ordinal
         * @throws IllegalArgumentException if ordinal doesn't exist
         */
        public static StakedIdOneOfType fromProtobufOrdinal(int ordinal) {
            return switch (ordinal) {
                case 10 -> STAKED_ACCOUNT_ID;

                case 11 -> STAKED_NODE_ID;

                default -> throw new IllegalArgumentException("Unknown protobuf ordinal " + ordinal);
            };
        }

        /**
         * Get enum from string name, supports the enum or protobuf format name
         *
         * @param name the enum or protobuf format name
         * @return enum for matching name
         */
        public static StakedIdOneOfType fromString(String name) {
            return switch (name) {
                case "staked_account_id", "STAKED_ACCOUNT_ID" -> STAKED_ACCOUNT_ID;

                case "staked_node_id", "STAKED_NODE_ID" -> STAKED_NODE_ID;

                default -> throw new IllegalArgumentException("Unknown token kyc status " + name);
            };
        }

        /**
         * Get the oneof field ordinal in protobuf for this type
         *
         * @return The oneof field ordinal in protobuf for this type
         */
        public int protoOrdinal() {
            return protoOrdinal;
        }

        /**
         * Get the original field name in protobuf for this type
         *
         * @return The original field name in protobuf for this type
         */
        public String protoName() {
            return protoName;
        }
    }

    /**
     * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
     * paths use the constructor directly.
     */
    public static final class Builder {
        @Nullable
        private AccountID accountId = null;

        @Nonnull
        private Bytes alias = Bytes.EMPTY;

        @Nullable
        private Key key = null;

        private long expirationSecond = 0;
        private Supplier<Long> tinybarBalanceSupplier = DEFAULT_LONG_SUPPLIER;

        @Nonnull
        private String memo = "";

        private boolean deleted = false;
        private long stakedToMe = 0;
        private long stakePeriodStart = 0;
        private OneOf<Account.StakedIdOneOfType> stakedId =
                com.hedera.hapi.node.state.token.codec.AccountProtoCodec.STAKED_ID_UNSET;
        private boolean declineReward = false;
        private boolean receiverSigRequired = false;

        @Nullable
        private TokenID headTokenId = null;

        @Nullable
        private NftID headNftId = null;

        private long headNftSerialNumber = 0;
        private Supplier<Long> numberOwnedNftsSupplier = DEFAULT_LONG_SUPPLIER;
        private int maxAutoAssociations = 0;
        private int usedAutoAssociations = 0;
        private Supplier<Integer> numberAssociationsSupplier = DEFAULT_INTEGER_SUPPLIER;
        private boolean smartContract = false;
        private Supplier<Integer> numberPositiveBalancesSupplier = DEFAULT_INTEGER_SUPPLIER;
        private long ethereumNonce = 0;
        private long stakeAtStartOfLastRewardedPeriod = 0;

        @Nullable
        private AccountID autoRenewAccountId = null;

        private long autoRenewSeconds = 0;
        private int contractKvPairsNumber = 0;

        @Nonnull
        private Supplier<List<AccountCryptoAllowance>> cryptoAllowancesSupplier = Collections::emptyList;

        @Nonnull
        private Supplier<List<AccountApprovalForAllAllowance>> approveForAllNftAllowancesSupplier =
                Collections::emptyList;

        @Nonnull
        private Supplier<List<AccountFungibleTokenAllowance>> tokenAllowancesSupplier = Collections::emptyList;

        private int numberTreasuryTitles = 0;
        private boolean expiredAndPendingRemoval = false;

        @Nonnull
        private Bytes firstContractStorageKey = Bytes.EMPTY;

        @Nullable
        private PendingAirdropId headPendingAirdropId = null;

        private long numberPendingAirdrops = 0;

        private long numberHooksInUse = 0;

        private long firstHookId = 0;

        private long numberLambdaStorageSlots = 0;

        /**
         * Create an empty builder
         */
        public Builder() {}

        /**
         * Create a pre-populated Builder.
         *
         * @param accountId <b>(1)</b> The unique entity id of the account.,
         * @param alias <b>(2)</b> The alias to use for this account, if any.,
         * @param key <b>(3)</b> (Optional) The key to be used to sign transactions from the account, if any.
         *            This key will not be set for hollow accounts until the account is finalized.
         *            This key should be set on all the accounts, except for immutable accounts (0.0.800 and 0.0.801).,
         * @param expirationSecond <b>(4)</b> The expiration time of the account, in seconds since the epoch.,
         * @param tinybarBalanceSupplier <b>(5)</b> The balance of the account, in tiny-bars wrapped in a supplier.,
         * @param memo <b>(6)</b> An optional description of the account with UTF-8 encoding up to 100 bytes.,
         * @param deleted <b>(7)</b> A boolean marking if the account has been deleted.,
         * @param stakedToMe <b>(8)</b> The amount of hbars staked to the account.,
         * @param stakePeriodStart <b>(9)</b> If this account stakes to another account, its value will be -1. It will
         *                         be set to the time when the account starts staking to a node.,
         * @param stakedId <b>(10, 11)</b> ID of the account or node to which this account is staking.,
         * @param declineReward <b>(12)</b> A boolean marking if the account declines rewards.,
         * @param receiverSigRequired <b>(13)</b> A boolean marking if the account requires a receiver signature.,
         * @param headTokenId <b>(14)</b> The token ID of the head of the linked list from token relations map for the account.,
         * @param headNftId <b>(15)</b> The NftID of the head of the linked list from unique tokens map for the account.,
         * @param headNftSerialNumber <b>(16)</b> The serial number of the head NftID of the linked list from unique tokens map for the account.,
         * @param numberOwnedNftsSupplier <b>(17)</b> The number of NFTs owned by the account wrapped in a supplier.,
         * @param maxAutoAssociations <b>(18)</b> The maximum number of tokens that can be auto-associated with the account.,
         * @param usedAutoAssociations <b>(19)</b> The number of used auto-association slots.,
         * @param numberAssociationsSupplier <b>(20)</b> The number of tokens associated with the account wrapped in a supplier. This number is used for
         *  *                           fee calculation during renewal of the account.,
         * @param smartContract <b>(21)</b> A boolean marking if the account is a smart contract.,
         * @param numberPositiveBalancesSupplier <b>(22)</b> The number of tokens with a positive balance associated with the account wrapped in a supplier.
         *  *                               If the account has positive balance in a token, it can not be deleted.,
         * @param ethereumNonce <b>(23)</b> The nonce of the account, used for Ethereum interoperability.,
         * @param stakeAtStartOfLastRewardedPeriod <b>(24)</b> The amount of hbars staked to the account at the start of the last rewarded period.,
         * @param autoRenewAccountId <b>(25)</b> (Optional) The id of an auto-renew account, in the same shard and realm as the account, that
         *                           has signed a transaction allowing the network to use its balance to automatically extend the account's
         *                           expiration time when it passes.,
         * @param autoRenewSeconds <b>(26)</b> The number of seconds the network should automatically extend the account's expiration by, if the
         *                         account has a valid auto-renew account, and is not deleted upon expiration.
         *                         If this is not provided in an allowed range on account creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD.
         *                         The default values for the minimum period and maximum period are 30 days and 90 days, respectively.,
         * @param contractKvPairsNumber <b>(27)</b> If this account is a smart-contract, number of key-value pairs stored on the contract.
         *                              This is used to determine the storage rent for the contract.,
         * @param cryptoAllowancesSupplier <b>(28)</b> (Optional) List of crypto allowances approved by the account in a supplier.
         *  *                         It contains account number for which the allowance is approved to and
         *  *                         the amount approved for that account.,
         * @param approveForAllNftAllowancesSupplier <b>(29)</b> (Optional) List of non-fungible token allowances approved for all by the account in a supplier.
         *  *                                   It contains account number approved for spending all serial numbers for the given
         *  *                                   NFT token number using approved_for_all flag.
         *  *                                   Allowances for a specific serial number is stored in the NFT itself in state.,
         * @param tokenAllowancesSupplier <b>(30)</b> (Optional) List of fungible token allowances approved by the account in a supplier.
         *  *                        It contains account number for which the allowance is approved to and  the token number.
         *  *                        It also contains and the amount approved for that account.,
         * @param numberTreasuryTitles <b>(31)</b> The number of tokens for which this account is treasury,
         * @param expiredAndPendingRemoval <b>(32)</b> A flag indicating if the account is expired and pending removal.
         *                                 Only the entity expiration system task toggles this flag when it reaches this account
         *                                 and finds it expired. Before setting the flag the system task checks if the account has
         *                                 an auto-renew account with balance. This is done to prevent a zero-balance account with a funded
         *                                 auto-renew account from being treated as expired in the interval between its expiration
         *                                 and the time the system task actually auto-renews it.,
         * @param firstContractStorageKey <b>(33)</b> The first key in the doubly-linked list of this contract's storage mappings;
         *                                It will be null if if the account is not a contract or the contract has no storage mappings.,
         * @param headPendingAirdropId <b>(34)</b> A pending airdrop ID at the head of the linked list for this account
         *                             from the account airdrops map.<br/>
         *                             The account airdrops are connected by including the "next" and "previous"
         *                             `PendingAirdropID` in each `AccountAirdrop` message.
         *                             <p>
         *                             This value SHALL NOT be empty if this account is "sender" for any
         *                             pending airdrop, and SHALL be empty otherwise.
         * @param numberPendingAirdrops <b>(35)</b> The number of pending airdrops owned by the account. This number is used to collect rent
         *                              for the account.
         * @param numberHooksInUse <b>(36)</b> The number of hooks currently in use on this account.
         * @param firstHookId <b>(37)</b> If the account has more than zero hooks in use, the id of the first hook in its
         *                    doubly-linked list of hooks.
         * @param numberLambdaStorageSlots <b>(38)</b> The number of storage slots in use by this account's lambdas.
         */
        @SuppressWarnings("java:S107")
        public Builder(
                AccountID accountId,
                Bytes alias,
                Key key,
                long expirationSecond,
                Supplier<Long> tinybarBalanceSupplier,
                String memo,
                boolean deleted,
                long stakedToMe,
                long stakePeriodStart,
                OneOf<Account.StakedIdOneOfType> stakedId,
                boolean declineReward,
                boolean receiverSigRequired,
                TokenID headTokenId,
                NftID headNftId,
                long headNftSerialNumber,
                Supplier<Long> numberOwnedNftsSupplier,
                int maxAutoAssociations,
                int usedAutoAssociations,
                Supplier<Integer> numberAssociationsSupplier,
                boolean smartContract,
                Supplier<Integer> numberPositiveBalancesSupplier,
                long ethereumNonce,
                long stakeAtStartOfLastRewardedPeriod,
                AccountID autoRenewAccountId,
                long autoRenewSeconds,
                int contractKvPairsNumber,
                Supplier<List<AccountCryptoAllowance>> cryptoAllowancesSupplier,
                Supplier<List<AccountApprovalForAllAllowance>> approveForAllNftAllowancesSupplier,
                Supplier<List<AccountFungibleTokenAllowance>> tokenAllowancesSupplier,
                int numberTreasuryTitles,
                boolean expiredAndPendingRemoval,
                Bytes firstContractStorageKey,
                PendingAirdropId headPendingAirdropId,
                long numberPendingAirdrops,
                long numberHooksInUse,
                long firstHookId,
                long numberLambdaStorageSlots) {
            this.accountId = accountId;
            this.alias = alias != null ? alias : Bytes.EMPTY;
            this.key = key;
            this.expirationSecond = expirationSecond;
            this.tinybarBalanceSupplier = tinybarBalanceSupplier;
            this.memo = memo != null ? memo : "";
            this.deleted = deleted;
            this.stakedToMe = stakedToMe;
            this.stakePeriodStart = stakePeriodStart;
            this.stakedId = stakedId;
            this.declineReward = declineReward;
            this.receiverSigRequired = receiverSigRequired;
            this.headTokenId = headTokenId;
            this.headNftId = headNftId;
            this.headNftSerialNumber = headNftSerialNumber;
            this.numberOwnedNftsSupplier = numberOwnedNftsSupplier;
            this.maxAutoAssociations = maxAutoAssociations;
            this.usedAutoAssociations = usedAutoAssociations;
            this.numberAssociationsSupplier = numberAssociationsSupplier;
            this.smartContract = smartContract;
            this.numberPositiveBalancesSupplier = numberPositiveBalancesSupplier;
            this.ethereumNonce = ethereumNonce;
            this.stakeAtStartOfLastRewardedPeriod = stakeAtStartOfLastRewardedPeriod;
            this.autoRenewAccountId = autoRenewAccountId;
            this.autoRenewSeconds = autoRenewSeconds;
            this.contractKvPairsNumber = contractKvPairsNumber;
            this.cryptoAllowancesSupplier =
                    cryptoAllowancesSupplier == null ? Collections::emptyList : cryptoAllowancesSupplier;
            this.approveForAllNftAllowancesSupplier = approveForAllNftAllowancesSupplier == null
                    ? Collections::emptyList
                    : approveForAllNftAllowancesSupplier;
            this.tokenAllowancesSupplier =
                    tokenAllowancesSupplier == null ? Collections::emptyList : tokenAllowancesSupplier;
            this.numberTreasuryTitles = numberTreasuryTitles;
            this.expiredAndPendingRemoval = expiredAndPendingRemoval;
            this.firstContractStorageKey = firstContractStorageKey != null ? firstContractStorageKey : Bytes.EMPTY;
            this.headPendingAirdropId = headPendingAirdropId;
            this.numberPendingAirdrops = numberPendingAirdrops;
            this.numberHooksInUse = numberHooksInUse;
            this.firstHookId = firstHookId;
            this.numberLambdaStorageSlots = numberLambdaStorageSlots;
        }

        /**
         * Build a new model record with data set on builder
         *
         * @return new model record with data set
         */
        public Account build() {
            return new Account(
                    accountId,
                    alias,
                    key,
                    expirationSecond,
                    tinybarBalanceSupplier,
                    memo,
                    deleted,
                    stakedToMe,
                    stakePeriodStart,
                    stakedId,
                    declineReward,
                    receiverSigRequired,
                    headTokenId,
                    headNftId,
                    headNftSerialNumber,
                    numberOwnedNftsSupplier,
                    maxAutoAssociations,
                    usedAutoAssociations,
                    numberAssociationsSupplier,
                    smartContract,
                    numberPositiveBalancesSupplier,
                    ethereumNonce,
                    stakeAtStartOfLastRewardedPeriod,
                    autoRenewAccountId,
                    autoRenewSeconds,
                    contractKvPairsNumber,
                    cryptoAllowancesSupplier,
                    approveForAllNftAllowancesSupplier,
                    tokenAllowancesSupplier,
                    numberTreasuryTitles,
                    expiredAndPendingRemoval,
                    firstContractStorageKey,
                    headPendingAirdropId,
                    numberPendingAirdrops,
                    numberHooksInUse,
                    firstHookId,
                    numberLambdaStorageSlots);
        }

        /**
         * <b>(1)</b> The unique entity id of the account.
         *
         * @param accountId value to set
         * @return builder to continue building with
         */
        public Builder accountId(@Nullable AccountID accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * <b>(1)</b> The unique entity id of the account.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder accountId(AccountID.Builder builder) {
            this.accountId = builder.build();
            return this;
        }

        /**
         * <b>(2)</b> The alias to use for this account, if any.
         *
         * @param alias value to set
         * @return builder to continue building with
         */
        public Builder alias(@Nonnull Bytes alias) {
            this.alias = alias;
            return this;
        }

        /**
         * <b>(3)</b> (Optional) The key to be used to sign transactions from the account, if any.
         * This key will not be set for hollow accounts until the account is finalized.
         * This key should be set on all the accounts, except for immutable accounts (0.0.800 and 0.0.801).
         *
         * @param key value to set
         * @return builder to continue building with
         */
        public Builder key(@Nullable Key key) {
            this.key = key;
            return this;
        }

        /**
         * <b>(3)</b> (Optional) The key to be used to sign transactions from the account, if any.
         * This key will not be set for hollow accounts until the account is finalized.
         * This key should be set on all the accounts, except for immutable accounts (0.0.800 and 0.0.801).
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder key(Key.Builder builder) {
            this.key = builder.build();
            return this;
        }

        /**
         * <b>(4)</b> The expiration time of the account, in seconds since the epoch.
         *
         * @param expirationSecond value to set
         * @return builder to continue building with
         */
        public Builder expirationSecond(long expirationSecond) {
            this.expirationSecond = expirationSecond;
            return this;
        }

        /**
         * <b>(5)</b> The balance of the account, in tiny-bars.
         *
         * @param tinybarBalance value to set
         * @return builder to continue building with
         */
        public Builder tinybarBalance(long tinybarBalance) {
            this.tinybarBalanceSupplier = () -> tinybarBalance;
            return this;
        }

        /**
         * <b>(5)</b> The balance of the account, in tiny-bars.
         *
         * @param tinybarBalanceSupplier value to set
         * @return builder to continue building with
         */
        public Builder tinybarBalance(Supplier<Long> tinybarBalanceSupplier) {
            this.tinybarBalanceSupplier = tinybarBalanceSupplier;
            return this;
        }

        /**
         * <b>(6)</b> An optional description of the account with UTF-8 encoding up to 100 bytes.
         *
         * @param memo value to set
         * @return builder to continue building with
         */
        public Builder memo(@Nonnull String memo) {
            this.memo = memo;
            return this;
        }

        /**
         * <b>(7)</b> A boolean marking if the account has been deleted.
         *
         * @param deleted value to set
         * @return builder to continue building with
         */
        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * <b>(8)</b> The amount of hbars staked to the account.
         *
         * @param stakedToMe value to set
         * @return builder to continue building with
         */
        public Builder stakedToMe(long stakedToMe) {
            this.stakedToMe = stakedToMe;
            return this;
        }

        /**
         * <b>(9)</b> If this account stakes to another account, its value will be -1. It will
         * be set to the time when the account starts staking to a node.
         *
         * @param stakePeriodStart value to set
         * @return builder to continue building with
         */
        public Builder stakePeriodStart(long stakePeriodStart) {
            this.stakePeriodStart = stakePeriodStart;
            return this;
        }

        /**
         * <b>(10)</b> ID of the new account to which this account is staking. If set to the sentinel <code>0.0.0</code> AccountID,
         * this field removes this account's staked account ID.
         *
         * @param stakedAccountId value to set
         * @return builder to continue building with
         */
        public Builder stakedAccountId(@Nullable AccountID stakedAccountId) {
            this.stakedId = new OneOf<>(Account.StakedIdOneOfType.STAKED_ACCOUNT_ID, stakedAccountId);
            return this;
        }

        /**
         * <b>(10)</b> ID of the new account to which this account is staking. If set to the sentinel <code>0.0.0</code> AccountID,
         * this field removes this account's staked account ID.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder stakedAccountId(AccountID.Builder builder) {
            this.stakedId = new OneOf<>(Account.StakedIdOneOfType.STAKED_ACCOUNT_ID, builder.build());
            return this;
        }

        /**
         * <b>(11)</b> ID of the new node this account is staked to. If set to the sentinel <code>-1</code>, this field
         * removes this account's staked node ID.
         *
         * @param stakedNodeId value to set
         * @return builder to continue building with
         */
        public Builder stakedNodeId(long stakedNodeId) {
            this.stakedId = new OneOf<>(Account.StakedIdOneOfType.STAKED_NODE_ID, stakedNodeId);
            return this;
        }

        /**
         * <b>(12)</b> A boolean marking if the account declines rewards.
         *
         * @param declineReward value to set
         * @return builder to continue building with
         */
        public Builder declineReward(boolean declineReward) {
            this.declineReward = declineReward;
            return this;
        }

        /**
         * <b>(12)</b> A boolean marking if the account requires a receiver signature.
         *
         * @param receiverSigRequired value to set
         * @return builder to continue building with
         */
        public Builder receiverSigRequired(boolean receiverSigRequired) {
            this.receiverSigRequired = receiverSigRequired;
            return this;
        }

        /**
         * <b>(13)</b> The token ID of the head of the linked list from token relations map for the account.
         *
         * @param headTokenId value to set
         * @return builder to continue building with
         */
        public Builder headTokenId(@Nullable TokenID headTokenId) {
            this.headTokenId = headTokenId;
            return this;
        }

        /**
         * <b>(13)</b> The token ID of the head of the linked list from token relations map for the account.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder headTokenId(TokenID.Builder builder) {
            this.headTokenId = builder.build();
            return this;
        }

        /**
         * <b>(14)</b> The NftID of the head of the linked list from unique tokens map for the account.
         *
         * @param headNftId value to set
         * @return builder to continue building with
         */
        public Builder headNftId(@Nullable NftID headNftId) {
            this.headNftId = headNftId;
            return this;
        }

        /**
         * <b>(14)</b> The NftID of the head of the linked list from unique tokens map for the account.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder headNftId(NftID.Builder builder) {
            this.headNftId = builder.build();
            return this;
        }

        /**
         * <b>(15)</b> The serial number of the head NftID of the linked list from unique tokens map for the account.
         *
         * @param headNftSerialNumber value to set
         * @return builder to continue building with
         */
        public Builder headNftSerialNumber(long headNftSerialNumber) {
            this.headNftSerialNumber = headNftSerialNumber;
            return this;
        }

        /**
         * <b>(16)</b> The number of NFTs owned by the account.
         *
         * @param numberOwnedNfts value to set
         * @return builder to continue building with
         */
        public Builder numberOwnedNfts(long numberOwnedNfts) {
            this.numberOwnedNftsSupplier = () -> numberOwnedNfts;
            return this;
        }

        /**
         * <b>(16)</b> The number of NFTs owned by the account.
         *
         * @param numberOwnedNftsSupplier value to set
         * @return builder to continue building with
         */
        public Builder numberOwnedNfts(Supplier<Long> numberOwnedNftsSupplier) {
            this.numberOwnedNftsSupplier = numberOwnedNftsSupplier;
            return this;
        }

        /**
         * <b>(17)</b> The maximum number of tokens that can be auto-associated with the account.
         *
         * @param maxAutoAssociations value to set
         * @return builder to continue building with
         */
        public Builder maxAutoAssociations(int maxAutoAssociations) {
            this.maxAutoAssociations = maxAutoAssociations;
            return this;
        }

        /**
         * <b>(18)</b> The number of used auto-association slots.
         *
         * @param usedAutoAssociations value to set
         * @return builder to continue building with
         */
        public Builder usedAutoAssociations(int usedAutoAssociations) {
            this.usedAutoAssociations = usedAutoAssociations;
            return this;
        }

        /**
         * <b>(19)</b> The number of tokens associated with the account. This number is used for
         * fee calculation during renewal of the account.
         *
         * @param numberAssociations value to set
         * @return builder to continue building with
         */
        public Builder numberAssociations(int numberAssociations) {
            this.numberAssociationsSupplier = () -> numberAssociations;
            return this;
        }

        /**
         * <b>(19)</b> The number of tokens associated with the account. This number is used for
         * fee calculation during renewal of the account.
         *
         * @param numberAssociationsSupplier value to set
         * @return builder to continue building with
         */
        public Builder numberAssociations(Supplier<Integer> numberAssociationsSupplier) {
            this.numberAssociationsSupplier = numberAssociationsSupplier;
            return this;
        }

        /**
         * <b>(20)</b> A boolean marking if the account is a smart contract.
         *
         * @param smartContract value to set
         * @return builder to continue building with
         */
        public Builder smartContract(boolean smartContract) {
            this.smartContract = smartContract;
            return this;
        }

        /**
         * <b>(21)</b> The number of tokens with a positive balance associated with the account.
         * If the account has positive balance in a token, it can not be deleted.
         *
         * @param numberPositiveBalances value to set
         * @return builder to continue building with
         */
        public Builder numberPositiveBalances(int numberPositiveBalances) {
            this.numberPositiveBalancesSupplier = () -> numberPositiveBalances;
            return this;
        }

        /**
         * <b>(21)</b> The number of tokens with a positive balance associated with the account.
         * If the account has positive balance in a token, it can not be deleted.
         *
         * @param numberPositiveBalancesSupplier value to set
         * @return builder to continue building with
         */
        public Builder numberPositiveBalances(Supplier<Integer> numberPositiveBalancesSupplier) {
            this.numberPositiveBalancesSupplier = numberPositiveBalancesSupplier;
            return this;
        }

        /**
         * <b>(22)</b> The nonce of the account, used for Ethereum interoperability.
         *
         * @param ethereumNonce value to set
         * @return builder to continue building with
         */
        public Builder ethereumNonce(long ethereumNonce) {
            this.ethereumNonce = ethereumNonce;
            return this;
        }

        /**
         * <b>(23)</b> The amount of hbars staked to the account at the start of the last rewarded period.
         *
         * @param stakeAtStartOfLastRewardedPeriod value to set
         * @return builder to continue building with
         */
        public Builder stakeAtStartOfLastRewardedPeriod(long stakeAtStartOfLastRewardedPeriod) {
            this.stakeAtStartOfLastRewardedPeriod = stakeAtStartOfLastRewardedPeriod;
            return this;
        }

        /**
         * <b>(24)</b> (Optional) The id of an auto-renew account, in the same shard and realm as the account, that
         * has signed a transaction allowing the network to use its balance to automatically extend the account's
         * expiration time when it passes.
         *
         * @param autoRenewAccountId value to set
         * @return builder to continue building with
         */
        public Builder autoRenewAccountId(@Nullable AccountID autoRenewAccountId) {
            this.autoRenewAccountId = autoRenewAccountId;
            return this;
        }

        /**
         * <b>(24)</b> (Optional) The id of an auto-renew account, in the same shard and realm as the account, that
         * has signed a transaction allowing the network to use its balance to automatically extend the account's
         * expiration time when it passes.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder autoRenewAccountId(AccountID.Builder builder) {
            this.autoRenewAccountId = builder.build();
            return this;
        }

        /**
         * <b>(25)</b> The number of seconds the network should automatically extend the account's expiration by, if the
         * account has a valid auto-renew account, and is not deleted upon expiration.
         * If this is not provided in an allowed range on account creation, the transaction will fail with INVALID_AUTO_RENEWAL_PERIOD.
         * The default values for the minimum period and maximum period are 30 days and 90 days, respectively.
         *
         * @param autoRenewSeconds value to set
         * @return builder to continue building with
         */
        public Builder autoRenewSeconds(long autoRenewSeconds) {
            this.autoRenewSeconds = autoRenewSeconds;
            return this;
        }

        /**
         * <b>(26)</b> If this account is a smart-contract, number of key-value pairs stored on the contract.
         * This is used to determine the storage rent for the contract.
         *
         * @param contractKvPairsNumber value to set
         * @return builder to continue building with
         */
        public Builder contractKvPairsNumber(int contractKvPairsNumber) {
            this.contractKvPairsNumber = contractKvPairsNumber;
            return this;
        }

        /**
         * <b>(27</b> (Optional) List of crypto allowances approved by the account.
         * It contains account number for which the allowance is approved to and
         * the amount approved for that account.
         *
         * @param cryptoAllowances value to set
         * @return builder to continue building with
         */
        public Builder cryptoAllowances(@Nonnull List<AccountCryptoAllowance> cryptoAllowances) {
            this.cryptoAllowancesSupplier = () -> cryptoAllowances;
            return this;
        }

        /**
         * <b>(27)</b> (Optional) List of crypto allowances approved by the account.
         * It contains account number for which the allowance is approved to and
         * the amount approved for that account.
         *
         * @param cryptoAllowancesSupplier value to set
         * @return builder to continue building with
         */
        public Builder cryptoAllowances(@Nonnull Supplier<List<AccountCryptoAllowance>> cryptoAllowancesSupplier) {
            this.cryptoAllowancesSupplier = cryptoAllowancesSupplier;
            return this;
        }

        /**
         * <b>(28)</b> (Optional) List of non-fungible token allowances approved for all by the account.
         * It contains account number approved for spending all serial numbers for the given
         * NFT token number using approved_for_all flag.
         * Allowances for a specific serial number is stored in the NFT itself in state.
         *
         * @param approveForAllNftAllowances value to set
         * @return builder to continue building with
         */
        public Builder approveForAllNftAllowances(
                @Nonnull List<AccountApprovalForAllAllowance> approveForAllNftAllowances) {
            this.approveForAllNftAllowancesSupplier = () -> approveForAllNftAllowances;
            return this;
        }

        /**
         * <b>(28)</b> (Optional) List of non-fungible token allowances approved for all by the account.
         * It contains account number approved for spending all serial numbers for the given
         * NFT token number using approved_for_all flag.
         * Allowances for a specific serial number is stored in the NFT itself in state.
         *
         * @param approveForAllNftAllowancesSupplier value to set
         * @return builder to continue building with
         */
        public Builder approveForAllNftAllowances(
                @Nonnull Supplier<List<AccountApprovalForAllAllowance>> approveForAllNftAllowancesSupplier) {
            this.approveForAllNftAllowancesSupplier = approveForAllNftAllowancesSupplier;
            return this;
        }

        /**
         * <b>(29)</b> (Optional) List of fungible token allowances approved by the account.
         * It contains account number for which the allowance is approved to and  the token number.
         * It also contains and the amount approved for that account.
         *
         * @param tokenAllowances value to set
         * @return builder to continue building with
         */
        public Builder tokenAllowances(@Nonnull List<AccountFungibleTokenAllowance> tokenAllowances) {
            this.tokenAllowancesSupplier = () -> tokenAllowances;
            return this;
        }

        /**
         * <b>(29)</b> (Optional) List of fungible token allowances approved by the account.
         * It contains account number for which the allowance is approved to and  the token number.
         * It also contains and the amount approved for that account.
         *
         * @param tokenAllowancesSupplier value to set
         * @return builder to continue building with
         */
        public Builder tokenAllowances(@Nonnull Supplier<List<AccountFungibleTokenAllowance>> tokenAllowancesSupplier) {
            this.tokenAllowancesSupplier = tokenAllowancesSupplier;
            return this;
        }

        /**
         * <b>(30)</b> The number of tokens for which this account is treasury
         *
         * @param numberTreasuryTitles value to set
         * @return builder to continue building with
         */
        public Builder numberTreasuryTitles(int numberTreasuryTitles) {
            this.numberTreasuryTitles = numberTreasuryTitles;
            return this;
        }

        /**
         * <b>(31)</b> A flag indicating if the account is expired and pending removal.
         * Only the entity expiration system task toggles this flag when it reaches this account
         * and finds it expired. Before setting the flag the system task checks if the account has
         * an auto-renew account with balance. This is done to prevent a zero-balance account with a funded
         * auto-renew account from being treated as expired in the interval between its expiration
         * and the time the system task actually auto-renews it.
         *
         * @param expiredAndPendingRemoval value to set
         * @return builder to continue building with
         */
        public Builder expiredAndPendingRemoval(boolean expiredAndPendingRemoval) {
            this.expiredAndPendingRemoval = expiredAndPendingRemoval;
            return this;
        }

        /**
         * <b>(32)</b> The first key in the doubly-linked list of this contract's storage mappings;
         * It will be null if if the account is not a contract or the contract has no storage mappings.
         *
         * @param firstContractStorageKey value to set
         * @return builder to continue building with
         */
        public Builder firstContractStorageKey(@Nonnull Bytes firstContractStorageKey) {
            this.firstContractStorageKey = firstContractStorageKey;
            return this;
        }

        /**
         * <b>(33)</b> A pending airdrop ID at the head of the linked list for this account
         * from the account airdrops map.<br/>
         * The account airdrops are connected by including the "next" and "previous"
         * `PendingAirdropID` in each `AccountAirdrop` message.
         * <p>
         * This value SHALL NOT be empty if this account is "sender" for any
         * pending airdrop, and SHALL be empty otherwise.
         *
         * @param headPendingAirdropId value to set
         * @return builder to continue building with
         */
        public Builder headPendingAirdropId(@Nullable PendingAirdropId headPendingAirdropId) {
            this.headPendingAirdropId = headPendingAirdropId;
            return this;
        }

        /**
         * <b>(34)</b> A pending airdrop ID at the head of the linked list for this account
         * from the account airdrops map.<br/>
         * The account airdrops are connected by including the "next" and "previous"
         * `PendingAirdropID` in each `AccountAirdrop` message.
         * <p>
         * This value SHALL NOT be empty if this account is "sender" for any
         * pending airdrop, and SHALL be empty otherwise.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder headPendingAirdropId(PendingAirdropId.Builder builder) {
            this.headPendingAirdropId = builder.build();
            return this;
        }

        /**
         * <b>(35)</b> The number of pending airdrops owned by the account. This number is used to collect rent
         * for the account.
         *
         * @param numberPendingAirdrops value to set
         * @return builder to continue building with
         */
        public Builder numberPendingAirdrops(long numberPendingAirdrops) {
            this.numberPendingAirdrops = numberPendingAirdrops;
            return this;
        }

        /**
         * <b>(36)</b> The number of hooks currently in use on this account.
         *
         * @param numberHooksInUse value to set
         * @return builder to continue building with
         */
        public Builder numberHooksInUse(long numberHooksInUse) {
            this.numberHooksInUse = numberHooksInUse;
            return this;
        }

        /**
         * <b>(37)</b> If the account has more than zero hooks in use, the id of the first hook in its doubly-linked list of hooks.
         *
         * @param firstHookId value to set
         * @return builder to continue building with
         */
        public Builder firstHookId(long firstHookId) {
            this.firstHookId = firstHookId;
            return this;
        }

        /**
         * <b>(38)</b> The number of storage slots in use by this account's lambdas.
         *
         * @param numberLambdaStorageSlots value to set
         * @return builder to continue building with
         */
        public Builder numberLambdaStorageSlots(long numberLambdaStorageSlots) {
            this.numberLambdaStorageSlots = numberLambdaStorageSlots;
            return this;
        }
    }
}
