// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.ledger;

import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class BalanceChange {

    public static final AccountID DEFAULT_PAYER = AccountID.getDefaultInstance();
    public static final boolean DEFAULT_ALLOWANCE_APPROVAL = false;

    static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

    private long originalUnits;
    private long newBalance;
    private int expectedDecimals = -1;
    private Id token;
    private TokenID tokenId = null;
    private NftId nftId = null;
    private Id account;
    private AccountID accountId;
    private ByteString alias;
    private long allowanceUnits;
    private long aggregatedUnits;
    private ByteString counterPartyAlias;
    private AccountID payerID = null;
    private AccountID counterPartyAccountId = null;
    private boolean isApprovedAllowance = false;
    private boolean isForCustomFee = false;

    // This is used to indicate if the balance change is for a custom fee that is a fallback fee
    // This is used to enforce receiver signature requirements for fallback fees
    private boolean includesFallbackFee = false;

    private ResponseCodeEnum codeForInsufficientBalance;

    public static BalanceChange changingHbar(final AccountAmount aa, final AccountID payerID) {
        return new BalanceChange(null, aa, INSUFFICIENT_ACCOUNT_BALANCE, payerID);
    }

    public static BalanceChange changingFtUnits(
            final Id token, final TokenID tokenId, final AccountAmount aa, final AccountID payerID) {
        final var tokenChange = new BalanceChange(token, aa, INSUFFICIENT_TOKEN_BALANCE, payerID);
        tokenChange.tokenId = tokenId;
        return tokenChange;
    }

    public static BalanceChange hbarCustomFeeAdjust(final Id id, final long amount) {
        return new BalanceChange(
                id, amount, DEFAULT_PAYER, DEFAULT_ALLOWANCE_APPROVAL, true, INSUFFICIENT_ACCOUNT_BALANCE);
    }

    public static BalanceChange changingNftOwnership(
            final Id token, final TokenID tokenId, final NftTransfer nftTransfer, final AccountID payerID) {
        final var nftChange = new BalanceChange(
                token,
                nftTransfer.getSenderAccountID(),
                nftTransfer.getReceiverAccountID(),
                nftTransfer.getSerialNumber(),
                SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        nftChange.tokenId = tokenId;
        nftChange.isApprovedAllowance = nftTransfer.getIsApproval();
        if (nftTransfer.getIsApproval()) {
            nftChange.allowanceUnits = -1;
        }
        nftChange.payerID = payerID;
        return nftChange;
    }

    /* HTS constructor */
    private BalanceChange(
            final Id token, final AccountAmount aa, final ResponseCodeEnum code, final AccountID payerID) {
        this.token = token;
        this.accountId = aa.getAccountID();
        this.alias = accountId.getAlias();
        this.account = Id.fromGrpcAccount(accountId);
        this.isApprovedAllowance = aa.getIsApproval();
        this.originalUnits = aa.getAmount();
        this.codeForInsufficientBalance = code;
        this.payerID = payerID;
        this.aggregatedUnits = aa.getAmount();
        // Only set allowanceUnits if it is an allowance transfer and the account is the sender.
        if (isApprovedAllowance && originalUnits < 0) {
            this.allowanceUnits = originalUnits;
        }
    }

    /* NFT constructor */
    private BalanceChange(
            final Id token,
            final AccountID sender,
            final AccountID receiver,
            final long serialNo,
            final ResponseCodeEnum code) {
        this.token = token;
        this.nftId = new NftId(token.shard(), token.realm(), token.num(), serialNo);
        this.accountId = sender;
        this.counterPartyAccountId = receiver;
        this.counterPartyAlias = receiver.getAlias();
        this.account = Id.fromGrpcAccount(accountId);
        this.alias = accountId.getAlias();
        this.codeForInsufficientBalance = code;
        this.aggregatedUnits = serialNo;
    }

    /* ℏ constructor */
    private BalanceChange(
            final Id account,
            final long amount,
            final AccountID payerID,
            final boolean isApprovedAllowance,
            final boolean isForCustomFee,
            final ResponseCodeEnum code) {
        this.token = null;
        this.account = account;
        this.accountId = account.asGrpcAccount();
        this.alias = accountId.getAlias();
        this.originalUnits = amount;
        this.isApprovedAllowance = isApprovedAllowance;
        this.isForCustomFee = isForCustomFee;
        this.payerID = payerID;
        this.codeForInsufficientBalance = code;
        this.aggregatedUnits = amount;
        // Only set allowanceUnits if it is an allowance transfer and the account is the sender.
        if (isApprovedAllowance && amount < 0) {
            this.allowanceUnits = amount;
        }
    }

    public void setNewBalance(final long newBalance) {
        this.newBalance = newBalance;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public Id getAccount() {
        return account;
    }

    public void setExpectedDecimals(final int expectedDecimals) {
        this.expectedDecimals = expectedDecimals;
    }

    public boolean isForToken() {
        return isForFungibleToken() || isForNft();
    }

    public int getExpectedDecimals() {
        return expectedDecimals;
    }

    public Id getToken() {
        return token;
    }

    public Id account() {
        return account;
    }

    public boolean hasAlias() {
        return isAlias(accountId) || hasNonEmptyCounterPartyAlias();
    }

    public void replaceNonEmptyAliasWith(final EntityNum createdId) {
        if (isAlias(accountId)) {
            accountId = createdId.scopedAccountWith();
            account = Id.fromGrpcAccount(accountId);
        } else if (hasNonEmptyCounterPartyAlias()) {
            counterPartyAccountId = createdId.scopedAccountWith();
        }
    }

    public NftId nftId() {
        return nftId;
    }

    public boolean isForCustomFee() {
        return this.isForCustomFee;
    }

    /**
     * Boolean flag to indicate if the change is for a custom fee that includes a fallback fee.
     *
     * @return true if the change is for a custom fee that includes a fallback fee
     */
    public boolean includesFallbackFee() {
        return includesFallbackFee;
    }

    /**
     * Sets the flag to indicate if the change is for a custom fee that includes a fallback fee.
     * This is used to enforce the receiver signature requirement in a crypto transfer of an NFT
     * with a fallback royalty fee.
     */
    public void setIncludesFallbackFee() {
        this.includesFallbackFee = true;
    }

    public TokenID tokenId() {
        return (tokenId != null) ? tokenId : NO_TOKEN_FOR_HBAR_ADJUST;
    }

    public AccountID accountId() {
        return accountId;
    }

    public AccountID counterPartyAccountId() {
        return counterPartyAccountId;
    }

    public ByteString counterPartyAlias() {
        return counterPartyAlias;
    }

    public long getAggregatedUnits() {
        return this.aggregatedUnits;
    }

    public long serialNo() {
        return aggregatedUnits;
    }

    public long originalUnits() {
        return originalUnits;
    }

    /**
     * If this balance change is an hbar or fungible token debit, or an NFT ownership change;
     * <i>and</i> it is not already an approved change, converts this to an approved change.
     *
     * <p>We need this so that when a  TransferPrecompile is running without access to the
     * top-level {@link SignatureMap}, inside a {@code ContractCall} that previously relied on
     * top-level signatures, it can keep working by "setting up" the {@code ContractCall} with
     * appropriate allowances that we will use automatically.
     */
    public void switchToApproved() {
        if (isApprovedAllowance) {
            return;
        }
        if (token == null || nftId == null) {
            if (originalUnits < 0L) {
                isApprovedAllowance = true;
                allowanceUnits = originalUnits;
            }
        } else {
            isApprovedAllowance = true;
            allowanceUnits = -1;
        }
    }

    public ResponseCodeEnum codeForInsufficientBalance() {
        return codeForInsufficientBalance;
    }

    /**
     * allowanceUnits are always non-positive. If negative that accountId has some allowanceUnits to
     * be taken off from its allowanceMap with the respective payer. It will be -1 for nft ownership
     * changes.
     *
     * @return true if negative allowanceUnits
     */
    public boolean isApprovedAllowance() {
        return this.allowanceUnits < 0;
    }

    public long getAllowanceUnits() {
        return this.allowanceUnits;
    }

    /**
     * Since a change can have either an unknown alias or a counterPartyAlias (but not both),
     * returns any non-empty unknown alias in the change.
     *
     * @return non-empty alias
     */
    public ByteString getNonEmptyAliasIfPresent() {
        if (isAlias(accountId)) return alias;
        else if (hasNonEmptyCounterPartyAlias()) return counterPartyAlias;
        else return null;
    }

    public boolean hasNonEmptyCounterPartyAlias() {
        return counterPartyAccountId != null && isAlias(counterPartyAccountId);
    }

    public AccountID getPayerID() {
        return payerID;
    }

    public boolean hasExpectedDecimals() {
        return expectedDecimals != -1;
    }

    public boolean isForNft() {
        return token != null && counterPartyAccountId != null;
    }

    public boolean isForFungibleToken() {
        return token != null && counterPartyAccountId == null;
    }

    public boolean isForHbar() {
        return token == null;
    }

    public boolean affectsAccount(final AccountID accountId) {
        return accountId.equals(this.accountId);
    }

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
