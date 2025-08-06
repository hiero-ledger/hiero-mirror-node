// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalseOrRevert;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.DeleteWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransferList.Builder;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class SyntheticTxnFactory {
    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID = EntityIdUtils.contractIdFromEvmAddress(
            Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());

    public static final String AUTO_MEMO = "auto-created account";
    private static final String LAZY_MEMO = "lazy-created account";
    private static final long THREE_MONTHS_IN_SECONDS = 7776000L;

    public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
        final var builder = TokenMintTransactionBody.newBuilder();

        builder.setToken(mintWrapper.tokenType());
        if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllMetadata(mintWrapper.metadata());
        } else {
            builder.setAmount(mintWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenMint(builder);
    }

    public TransactionBody.Builder createHollowAccount(
            final ByteString alias, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder
                .setKey(asKeyUnchecked(EMPTY_KEY))
                .setAlias(alias)
                .setMemo(LAZY_MEMO)
                .setMaxAutomaticTokenAssociations(maxAutoAssociations);
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    public TransactionBody.Builder createAccount(
            final ByteString alias, final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(key).setAlias(alias).setMemo(AUTO_MEMO);

        if (maxAutoAssociations > 0) {
            baseBuilder.setMaxAutomaticTokenAssociations(maxAutoAssociations);
        }
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance) {
        return CryptoCreateTransactionBody.newBuilder()
                .setInitialBalance(balance)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS));
    }

    public TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }

    public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
        final var builder = TokenDissociateTransactionBody.newBuilder();

        builder.setAccount(dissociation.accountId());
        builder.addAllTokens(dissociation.tokenIds());

        return TransactionBody.newBuilder().setTokenDissociate(builder);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createFungibleApproval(
            @NonNull final ApproveWrapper approveWrapper, @NonNull Id ownerId) {
        return createNonfungibleApproval(approveWrapper, ownerId, null);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createNonfungibleApproval(
            final ApproveWrapper approveWrapper, @Nullable final Id ownerId, @Nullable final Id operatorId) {
        final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();
        validateApproveAmount(approveWrapper.amount());
        if (approveWrapper.isFungible()) {
            var tokenAllowance = TokenAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setOwner(Objects.requireNonNull(ownerId).asGrpcAccount())
                    .setSpender(approveWrapper.spender())
                    .setAmount(approveWrapper.amount().longValueExact());
            builder.addTokenAllowances(tokenAllowance.build());
        } else {
            final var op = NftAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setSpender(approveWrapper.spender())
                    .addSerialNumbers(approveWrapper.serialNumber().longValueExact());
            if (ownerId != null) {
                op.setOwner(ownerId.asGrpcAccount());
                if (!ownerId.equals(operatorId)) {
                    op.setDelegatingSpender(Objects.requireNonNull(operatorId).asGrpcAccount());
                }
            }
            builder.addNftAllowances(op.build());
        }
        return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createDeleteAllowance(final ApproveWrapper approveWrapper, final Id owner) {
        final var builder = CryptoDeleteAllowanceTransactionBody.newBuilder();
        builder.addAllNftAllowances(List.of(NftRemoveAllowance.newBuilder()
                        .setOwner(owner.asGrpcAccount())
                        .setTokenId(approveWrapper.tokenId())
                        .addAllSerialNumbers(
                                List.of(approveWrapper.serialNumber().longValueExact()))
                        .build()))
                .build();
        return TransactionBody.newBuilder().setCryptoDeleteAllowance(builder);
    }

    public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
        final var builder = TokenBurnTransactionBody.newBuilder();

        builder.setToken(burnWrapper.tokenType());
        if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(burnWrapper.serialNos());
        } else {
            builder.setAmount(burnWrapper.amount());
        }
        return TransactionBody.newBuilder().setTokenBurn(builder);
    }

    public TransactionBody.Builder createDelete(final DeleteWrapper deleteWrapper) {
        final var builder = TokenDeleteTransactionBody.newBuilder();
        builder.setToken(deleteWrapper.tokenID());
        return TransactionBody.newBuilder().setTokenDeletion(builder);
    }

    public TransactionBody.Builder createWipe(final WipeWrapper wipeWrapper) {
        final var builder = TokenWipeAccountTransactionBody.newBuilder();

        builder.setToken(wipeWrapper.token());
        builder.setAccount(wipeWrapper.account());
        if (wipeWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(wipeWrapper.serialNumbers());
        } else {
            builder.setAmount(wipeWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenWipe(builder);
    }

    public TransactionBody.Builder createRevokeKyc(final GrantRevokeKycWrapper<TokenID, AccountID> wrapper) {
        final var builder = TokenRevokeKycTransactionBody.newBuilder();

        builder.setToken(wrapper.token());
        builder.setAccount(wrapper.account());

        return TransactionBody.newBuilder().setTokenRevokeKyc(builder);
    }

    public TransactionBody.Builder createGrantKyc(
            final GrantRevokeKycWrapper<TokenID, AccountID> grantRevokeKycWrapper) {
        final var builder = TokenGrantKycTransactionBody.newBuilder();

        builder.setToken(grantRevokeKycWrapper.token());
        builder.setAccount(grantRevokeKycWrapper.account());

        return TransactionBody.newBuilder().setTokenGrantKyc(builder);
    }

    public TransactionBody.Builder createApproveAllowanceForAllNFT(
            @NonNull final SetApprovalForAllWrapper setApprovalForAllWrapper, @NonNull Id ownerId) {

        final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();

        builder.addNftAllowances(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(setApprovalForAllWrapper.approved()))
                .setTokenId(setApprovalForAllWrapper.tokenId())
                .setOwner(Objects.requireNonNull(ownerId).asGrpcAccount())
                .setSpender(setApprovalForAllWrapper.to())
                .build());

        return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
    }

    public TransactionBody.Builder createUnpause(final UnpauseWrapper unpauseWrapper) {
        final var builder = TokenUnpauseTransactionBody.newBuilder();
        builder.setToken(unpauseWrapper.token());
        return TransactionBody.newBuilder().setTokenUnpause(builder);
    }

    public TransactionBody.Builder createFreeze(final TokenFreezeUnfreezeWrapper<TokenID, AccountID> freezeWrapper) {
        final var builder = TokenFreezeAccountTransactionBody.newBuilder();
        builder.setToken(freezeWrapper.token());
        builder.setAccount(freezeWrapper.account());
        return TransactionBody.newBuilder().setTokenFreeze(builder);
    }

    public TransactionBody.Builder createUnfreeze(
            final TokenFreezeUnfreezeWrapper<TokenID, AccountID> unFreezeWrapper) {
        final var builder = TokenUnfreezeAccountTransactionBody.newBuilder();
        builder.setToken(unFreezeWrapper.token());
        builder.setAccount(unFreezeWrapper.account());
        return TransactionBody.newBuilder().setTokenUnfreeze(builder);
    }

    public TransactionBody.Builder createTokenUpdateKeys(final TokenUpdateKeysWrapper updateWrapper) {
        final var builder = constructUpdateTokenBuilder(updateWrapper.tokenID());
        return checkTokenKeysTypeAndBuild(updateWrapper.tokenKeys(), builder);
    }

    private TokenUpdateTransactionBody.Builder constructUpdateTokenBuilder(final TokenID tokenID) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(tokenID);
        return builder;
    }

    private TransactionBody.Builder checkTokenKeysTypeAndBuild(
            final List<TokenKeyWrapper> tokenKeys, final TokenUpdateTransactionBody.Builder builder) {
        tokenKeys.forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                builder.setAdminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                builder.setKycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                builder.setFreezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                builder.setWipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                builder.setSupplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                builder.setFeeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                builder.setPauseKey(key);
            }
        });

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }

    public TransactionBody.Builder createPause(final PauseWrapper pauseWrapper) {
        final var builder = TokenPauseTransactionBody.newBuilder();
        builder.setToken(pauseWrapper.token());
        return TransactionBody.newBuilder().setTokenPause(builder);
    }

    public TransactionBody.Builder createTokenCreate(TokenCreateWrapper tokenCreateWrapper) {
        final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();
        txnBodyBuilder.setName(tokenCreateWrapper.getName());
        txnBodyBuilder.setSymbol(tokenCreateWrapper.getSymbol());
        txnBodyBuilder.setDecimals(tokenCreateWrapper.getDecimals().intValue());
        txnBodyBuilder.setTokenType(tokenCreateWrapper.isFungible() ? TokenType.FUNGIBLE_COMMON : NON_FUNGIBLE_UNIQUE);
        txnBodyBuilder.setSupplyType(
                tokenCreateWrapper.isSupplyTypeFinite() ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
        txnBodyBuilder.setMaxSupply(tokenCreateWrapper.getMaxSupply());
        txnBodyBuilder.setInitialSupply(tokenCreateWrapper.getInitSupply().longValueExact());
        if (tokenCreateWrapper.getTreasury() != null) {
            txnBodyBuilder.setTreasury(tokenCreateWrapper.getTreasury());
        }
        txnBodyBuilder.setFreezeDefault(tokenCreateWrapper.isFreezeDefault());
        txnBodyBuilder.setMemo(tokenCreateWrapper.getMemo());
        if (tokenCreateWrapper.getExpiry().second() != 0) {
            txnBodyBuilder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(tokenCreateWrapper.getExpiry().second())
                    .build());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewAccount() != null) {
            txnBodyBuilder.setAutoRenewAccount(tokenCreateWrapper.getExpiry().autoRenewAccount());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewPeriod() != 0) {
            txnBodyBuilder.setAutoRenewPeriod(Duration.newBuilder()
                    .setSeconds(tokenCreateWrapper.getExpiry().autoRenewPeriod()));
        }

        setTokenKeys(tokenCreateWrapper, txnBodyBuilder);

        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFixedFees().stream()
                .map(TokenCreateWrapper.FixedFeeWrapper::asGrpc)
                .toList());
        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFractionalFees().stream()
                .map(TokenCreateWrapper.FractionalFeeWrapper::asGrpc)
                .toList());
        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getRoyaltyFees().stream()
                .map(TokenCreateWrapper.RoyaltyFeeWrapper::asGrpc)
                .toList());
        return TransactionBody.newBuilder().setTokenCreation(txnBodyBuilder);
    }

    private void setTokenKeys(
            TokenCreateWrapper tokenCreateWrapper, TokenCreateTransactionBody.Builder txnBodyBuilder) {
        tokenCreateWrapper.getTokenKeys().forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                txnBodyBuilder.setAdminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                txnBodyBuilder.setKycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                txnBodyBuilder.setFreezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                txnBodyBuilder.setWipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                txnBodyBuilder.setSupplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                txnBodyBuilder.setFeeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                txnBodyBuilder.setPauseKey(key);
            }
        });
    }

    public TransactionBody.Builder createTransactionCall(final long gas, final Bytes functionParameters) {
        final var builder = ContractCallTransactionBody.newBuilder();

        builder.setContractID(HTS_PRECOMPILE_MIRROR_ID);
        builder.setGas(gas);
        builder.setFunctionParameters(ByteString.copyFrom(functionParameters.toArray()));

        return TransactionBody.newBuilder().setContractCall(builder);
    }

    /**
     * Given a list of {@link TokenTransferWrapper}s, where each wrapper gives changes scoped to a
     * particular {@link TokenID}, returns a synthetic {@code CryptoTransfer} whose {@link
     * CryptoTransferTransactionBody} consolidates the wrappers.
     *
     * <p>If two wrappers both refer to the same token, their transfer lists are merged as specified
     * in the {@link SyntheticTxnFactory#mergeTokenTransfers(TokenTransferList.Builder,
     * TokenTransferList.Builder)} helper method.
     *
     * @param wrappers the wrappers to consolidate in a synthetic transaction
     * @return the synthetic transaction
     */
    public TransactionBody.Builder createCryptoTransfer(final List<TokenTransferWrapper> wrappers) {
        final var opBuilder = CryptoTransferTransactionBody.newBuilder();
        if (wrappers.size() == 1) {
            opBuilder.addTokenTransfers(wrappers.get(0).asGrpcBuilder());
        } else if (wrappers.size() > 1) {
            final List<TokenTransferList.Builder> builders = new ArrayList<>();
            final Map<TokenID, Builder> listBuilders = new HashMap<>();
            for (final TokenTransferWrapper wrapper : wrappers) {
                final var builder = wrapper.asGrpcBuilder();
                final var merged =
                        listBuilders.merge(builder.getToken(), builder, SyntheticTxnFactory::mergeTokenTransfers);
                /* If merge() returns a builder other than the one we just created, it is already in the list */
                if (merged == builder) {
                    builders.add(builder);
                }
            }
            builders.forEach(opBuilder::addTokenTransfers);
        }
        return TransactionBody.newBuilder().setCryptoTransfer(opBuilder);
    }

    /**
     * Given a {@link TransferWrapper},
     *
     * <p>returns a synthetic {@code CryptoTransfer} whose {@link CryptoTransferTransactionBody}
     * consolidates the wrappers which embodies hbar transfers between accounts.
     *
     * @param wrapper the wrappers to consolidate in a synthetic transaction
     * @return the synthetic transaction
     */
    public TransactionBody createCryptoTransferForHbar(final TransferWrapper wrapper) {
        final var opBuilder = CryptoTransferTransactionBody.newBuilder();
        if (!wrapper.hbarTransfers().isEmpty()) {
            opBuilder.setTransfers(wrapper.asGrpcBuilder());
        }
        return TransactionBody.newBuilder().setCryptoTransfer(opBuilder).build();
    }

    /**
     * Merges the fungible and non-fungible exchanges from one token transfer list into another. (Of
     * course, at most one of these merges can be sensible; a token cannot be both fungible _and_
     * non-fungible.)
     *
     * <p>Fungible exchanges are "merged" by summing up all the amount fields for each unique
     * account id that appears in either list. NFT exchanges are "merged" by checking that each
     * exchange from either list appears at most once.
     *
     * @param to the builder to merge source exchanges into
     * @param from a source of fungible exchanges and NFT exchanges
     * @return the consolidated target builder
     */
    static TokenTransferList.Builder mergeTokenTransfers(
            final TokenTransferList.Builder to, final TokenTransferList.Builder from) {
        mergeFungible(from, to);
        mergeNonFungible(from, to);
        return to;
    }

    private static void mergeFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
        for (int i = 0, n = from.getTransfersCount(); i < n; i++) {
            final var transfer = from.getTransfers(i);
            final var targetId = transfer.getAccountID();
            var merged = false;
            for (int j = 0, m = to.getTransfersCount(); j < m; j++) {
                final var transferBuilder = to.getTransfersBuilder(j);
                if (targetId.equals(transferBuilder.getAccountID())) {
                    final var prevAmount = transferBuilder.getAmount();
                    transferBuilder.setAmount(prevAmount + transfer.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                to.addTransfers(transfer);
            }
        }
    }

    private static void mergeNonFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
        for (int i = 0, n = from.getNftTransfersCount(); i < n; i++) {
            final var fromExchange = from.getNftTransfersBuilder(i);
            var alreadyPresent = false;
            for (int j = 0, m = to.getNftTransfersCount(); j < m; j++) {
                final var toExchange = to.getNftTransfersBuilder(j);
                if (areSameBuilder(fromExchange, toExchange)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                to.addNftTransfers(fromExchange);
            }
        }
    }

    static boolean areSameBuilder(final NftTransfer.Builder a, final NftTransfer.Builder b) {
        return a.getSerialNumber() == b.getSerialNumber()
                && a.getSenderAccountID().equals(b.getSenderAccountID())
                && a.getReceiverAccountID().equals(b.getReceiverAccountID());
    }

    public TransactionBody.Builder createTokenUpdate(final TokenUpdateWrapper updateWrapper) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(updateWrapper.tokenID());

        if (updateWrapper.name() != null) {
            builder.setName(updateWrapper.name());
        }
        if (updateWrapper.symbol() != null) {
            builder.setSymbol(updateWrapper.symbol());
        }
        if (updateWrapper.memo() != null) {
            builder.setMemo(StringValue.of(updateWrapper.memo()));
        }
        if (updateWrapper.treasury() != null) {
            builder.setTreasury(updateWrapper.treasury());
        }

        if (updateWrapper.expiry().second() != 0) {
            builder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(updateWrapper.expiry().second())
                    .build());
        }
        if (updateWrapper.expiry().autoRenewAccount() != null) {
            builder.setAutoRenewAccount(updateWrapper.expiry().autoRenewAccount());
        }
        if (updateWrapper.expiry().autoRenewPeriod() != 0) {
            builder.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(updateWrapper.expiry().autoRenewPeriod()));
        }

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }

    public TransactionBody.Builder createTokenUpdateExpiryInfo(final TokenUpdateExpiryInfoWrapper expiryInfoWrapper) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        builder.setToken(expiryInfoWrapper.tokenID());

        if (expiryInfoWrapper.expiry().second() != 0) {
            builder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(expiryInfoWrapper.expiry().second())
                    .build());
        }
        if (expiryInfoWrapper.expiry().autoRenewAccount() != null) {
            builder.setAutoRenewAccount(expiryInfoWrapper.expiry().autoRenewAccount());
        }
        if (expiryInfoWrapper.expiry().autoRenewPeriod() != 0) {
            builder.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(expiryInfoWrapper.expiry().autoRenewPeriod()));
        }

        return TransactionBody.newBuilder().setTokenUpdate(builder);
    }

    private void validateApproveAmount(final BigInteger amount) {
        validateFalseOrRevert(amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, CONTRACT_REVERT_EXECUTED);
    }
}
