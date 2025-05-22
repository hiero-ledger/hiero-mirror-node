// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalseOrRevert;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindFungibleTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindHBarTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindNftExchangesFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeAccountIds;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.generateAccountIDWithAliasCalculatedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static java.math.BigInteger.ZERO;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TransferParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Copied Precompile from hedera-services. Differences with the original:
 * 1. Stateless logic
 * 2. Use abstraction for the state by introducing {@link Store} interface.
 * 3. Post body logic relies on `CryptoTransferTransactionBody` rather than `CryptoTransferWrapper`.
 * 4. Reworked `extrapolateDetailsFromSyntheticTxn()` method to match the stateless logic.
 * 5. Provided implementation for getFunctionSelectors() from {@link Precompile} interface.
 */
public class TransferPrecompile extends AbstractWritePrecompile {
    private static final Function CRYPTO_TRANSFER_FUNCTION =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", INT);
    private static final Function CRYPTO_TRANSFER_FUNCTION_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,"
                    + "bool)[])[])",
            INT);
    private static final Bytes CRYPTO_TRANSFER_SELECTOR = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION.selector());
    private static final Bytes CRYPTO_TRANSFER_SELECTOR_V2 = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION_V2.selector());
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER =
            TypeFactory.create("((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER_V2 = TypeFactory.create(
            "(((bytes32,int64,bool)[]),(bytes32,(bytes32,int64,bool)[],(bytes32,bytes32,int64,bool)[])[])");
    private static final Function TRANSFER_TOKENS_FUNCTION =
            new Function("transferTokens(address,address[],int64[])", INT);
    private static final Bytes TRANSFER_TOKENS_SELECTOR = Bytes.wrap(TRANSFER_TOKENS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[],int64[])");
    private static final Function TRANSFER_TOKEN_FUNCTION =
            new Function("transferToken(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_TOKEN_SELECTOR = Bytes.wrap(TRANSFER_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final Function TRANSFER_NFTS_FUNCTION =
            new Function("transferNFTs(address,address[],address[],int64[])", INT);
    private static final Bytes TRANSFER_NFTS_SELECTOR = Bytes.wrap(TRANSFER_NFTS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFTS_DECODER =
            TypeFactory.create("(bytes32,bytes32[],bytes32[],int64[])");
    private static final Function TRANSFER_NFT_FUNCTION =
            new Function("transferNFT(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_NFT_SELECTOR = Bytes.wrap(TRANSFER_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFT_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final TransferLogic transferLogic;
    private final ContextOptionValidator contextOptionValidator;
    private final AutoCreationLogic autoCreationLogic;
    private final EntityAddressSequencer entityAddressSequencer;
    private final Predicate<Address> systemAccountDetector;

    public TransferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityAddressSequencer entityAddressSequencer,
            final Predicate<Address> systemAccountDetector) {
        super(pricingUtils, syntheticTxnFactory);
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.transferLogic = transferLogic;
        this.contextOptionValidator = contextOptionValidator;
        this.autoCreationLogic = autoCreationLogic;
        this.entityAddressSequencer = entityAddressSequencer;
        this.systemAccountDetector = systemAccountDetector;
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is the latest version and supersedes public static
     * CryptoTransferWrapper decodeCryptoTransfer(). The selector for this function is derived from:
     * cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])
     * The first parameter describes hbar transfers and the second describes token transfers
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransferV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, Predicate<Address> exists) {
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR_V2, CRYPTO_TRANSFER_DECODER_V2);
        List<HbarTransfer> hbarTransfers = new ArrayList<>();
        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        final Tuple[] hbarTransferTuples = ((Tuple) decodedTuples.get(0)).get(0);
        final var tokenTransferTuples = decodedTuples.get(1);

        hbarTransfers = decodeHbarTransfers(aliasResolver, hbarTransfers, hbarTransferTuples);

        decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tokenTransferTuples, exists);

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static List<HbarTransfer> decodeHbarTransfers(
            final UnaryOperator<byte[]> aliasResolver,
            List<HbarTransfer> hbarTransfers,
            final Tuple[] hbarTransferTuples) {
        if (hbarTransferTuples.length > 0) {
            hbarTransfers = bindHBarTransfersFrom(hbarTransferTuples, aliasResolver);
        }
        return hbarTransfers;
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeCryptoTransferV2(). The selector for this function is derived from:
     * cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransfer(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, Predicate<Address> exists) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);

        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        for (final var tuple : decodedTuples) {
            decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tuple, exists);
        }

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void decodeTokenTransfer(
            final UnaryOperator<byte[]> aliasResolver,
            final List<TokenTransferWrapper> tokenTransferWrappers,
            final Tuple[] tokenTransferTuples,
            final Predicate<Address> exists) {
        for (final var tupleNested : tokenTransferTuples) {
            final var tokenType = convertAddressBytesToTokenID(tupleNested.get(0));

            var nftExchanges = NO_NFT_EXCHANGES;
            var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

            final var abiAdjustments = (Tuple[]) tupleNested.get(1);
            if (abiAdjustments.length > 0) {
                fungibleTransfers = bindFungibleTransfersFrom(tokenType, abiAdjustments, aliasResolver, exists);
            }
            final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
            if (abiNftExchanges.length > 0) {
                nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges, aliasResolver);
            }

            tokenTransferWrappers.add(new TokenTransferWrapper(nftExchanges, fungibleTransfers));
        }
    }

    public static CryptoTransferWrapper decodeTransferToken(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var amount = (long) decodedArguments.get(3);

        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                NO_NFT_EXCHANGES, List.of(new FungibleTokenTransfer(amount, false, tokenID, sender, receiver))));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferTokens(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

        final var tokenType = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountIDs = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var amounts = (long[]) decodedArguments.get(2);

        final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustments(fungibleTransfers, accountIDs, tokenType, amounts, aliasResolver);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFTs(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var senders = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var receivers = decodeAccountIds(decodedArguments.get(2), aliasResolver);
        final var serialNumbers = ((long[]) decodedArguments.get(3));

        final List<NftExchange> nftExchanges = new ArrayList<>();
        addNftExchanges(nftExchanges, senders, receivers, serialNumbers, tokenID, aliasResolver);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFT(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var serialNumber = (long) decodedArguments.get(3);

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                List.of(new NftExchange(serialNumber, tokenID, sender, receiver)), NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void addNftExchanges(
            final List<NftExchange> nftExchanges,
            final List<AccountID> senders,
            final List<AccountID> receivers,
            final long[] serialNumbers,
            final TokenID tokenID,
            final UnaryOperator<byte[]> aliasResolver) {
        for (var i = 0; i < senders.size(); i++) {
            var receiver = receivers.get(i);
            final var aliasAddress = resolveAlias(aliasResolver, receiver);
            if (aliasAddress != null && !isMirror(aliasAddress) && !receiver.hasAlias()) {
                receiver = generateAccountIDWithAliasCalculatedFrom(receiver);
            }
            final var nftExchange = new NftExchange(serialNumbers[i], tokenID, senders.get(i), receiver);
            nftExchanges.add(nftExchange);
        }
    }

    public static void addSignedAdjustments(
            final List<FungibleTokenTransfer> fungibleTransfers,
            final List<AccountID> accountIDs,
            final TokenID tokenType,
            final long[] amounts,
            final UnaryOperator<byte[]> aliasResolver) {
        for (int i = 0; i < accountIDs.size(); i++) {
            final var amount = amounts[i];

            var accountID = accountIDs.get(i);
            final var aliasAddress = resolveAlias(aliasResolver, accountID);
            if (amount > 0 && aliasAddress != null && !isMirror(aliasAddress) && !accountID.hasAlias()) {
                accountID = generateAccountIDWithAliasCalculatedFrom(accountID);
            }

            DecodingFacade.addSignedAdjustment(fungibleTransfers, tokenType, accountID, amount, false);
        }
    }

    private static byte[] resolveAlias(UnaryOperator<byte[]> aliasResolver, AccountID accountID) {
        return aliasResolver.apply(DomainUtils.toEvmAddress(accountID));
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        if (bodyParams instanceof TransferParams transferParams) {
            final var transferOp =
                    switch (transferParams.functionId()) {
                        case ABI_ID_CRYPTO_TRANSFER ->
                            decodeCryptoTransfer(input, aliasResolver, transferParams.exists());
                        case ABI_ID_CRYPTO_TRANSFER_V2 ->
                            decodeCryptoTransferV2(input, aliasResolver, transferParams.exists());
                        case ABI_ID_TRANSFER_TOKENS -> decodeTransferTokens(input, aliasResolver);
                        case ABI_ID_TRANSFER_TOKEN -> decodeTransferToken(input, aliasResolver);
                        case ABI_ID_TRANSFER_NFTS -> decodeTransferNFTs(input, aliasResolver);
                        case ABI_ID_TRANSFER_NFT -> decodeTransferNFT(input, aliasResolver);
                        default -> null;
                    };
            Objects.requireNonNull(transferOp, "Unable to decode function input");

            final var transactionBody = syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
            if (!transferOp.transferWrapper().hbarTransfers().isEmpty()) {
                transactionBody.mergeFrom(
                        syntheticTxnFactory.createCryptoTransferForHbar(transferOp.transferWrapper()));
            }

            return transactionBody;
        }
        return TransactionBody.newBuilder();
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var store = updater.getStore();
        final var hederaTokenStore = initializeHederaTokenStore(store);
        final var impliedValidity = extrapolateValidityDetailsFromSyntheticTxn(transactionBody);
        if (impliedValidity != OK) {
            throw new InvalidTransactionException(impliedValidity, true);
        }

        final var impliedTransfers = extrapolateImpliedTransferDetailsFromSyntheticTxn(
                transactionBody,
                EntityIdUtils.accountIdFromEvmAddress(((PrecompileContext)
                                frame.getMessageFrameStack().getLast().getContextVariable(PRECOMPILE_CONTEXT))
                        .getSenderAddress()));
        final var changes = impliedTransfers.getAllBalanceChanges();

        final Map<ByteString, EntityNum> completedLazyCreates = new HashMap<>();
        for (int i = 0, n = changes.size(); i < n; i++) {
            final var change = changes.get(i);
            if (change.hasAlias()) {
                replaceAliasWithId(change, changes, completedLazyCreates, store, entityAddressSequencer);
            }

            final var units = change.getAggregatedUnits();
            final var isCredit = units > 0;

            // Checks whether the balance modification targets the receiver account (i.e. credit operation).
            if (isCredit && !change.isForCustomFee()) {
                revertIfReceiverIsSystemAccount(change);
            }
        }

        transferLogic.doZeroSum(changes, store, entityAddressSequencer, hederaTokenStore);
        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(
                ABI_ID_CRYPTO_TRANSFER,
                ABI_ID_CRYPTO_TRANSFER_V2,
                ABI_ID_TRANSFER_TOKENS,
                ABI_ID_TRANSFER_TOKEN,
                ABI_ID_TRANSFER_NFTS,
                ABI_ID_TRANSFER_NFT);
    }

    private HederaTokenStore initializeHederaTokenStore(Store store) {
        return new HederaTokenStore(contextOptionValidator, mirrorNodeEvmProperties, store);
    }

    @Override
    public long getMinimumFeeInTinybars(
            final Timestamp consensusTime, final TransactionBody transactionBody, final AccountID sender) {
        long accumulatedCost = 0;
        final var impliedTransfers = extrapolateImpliedTransferDetailsFromSyntheticTxn(transactionBody, sender);
        final boolean customFees = !impliedTransfers.getAllBalanceChanges().isEmpty();
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final long ftTxCost = pricingUtils.getMinimumPriceInTinybars(
                        customFees
                                ? PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE_CUSTOM_FEES
                                : PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
                        consensusTime)
                / 2;
        // NFTs are atomic, one line can do it.
        final long nonFungibleTxCost = pricingUtils.getMinimumPriceInTinybars(
                customFees
                        ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES
                        : PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
                consensusTime);
        final var cryptoTransfer = transactionBody.getCryptoTransfer();
        for (final var transfer : cryptoTransfer.getTokenTransfersList()) {
            // Fungible transfers are divided by 2, because we have 2 separate items for sender and receiver.
            accumulatedCost += (transfer.getTransfersCount() / 2) * ftTxCost;
            accumulatedCost += transfer.getNftTransfersCount() * nonFungibleTxCost;
        }

        // add the cost for transferring hbars
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final long hbarTxCost =
                pricingUtils.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.TRANSFER_HBAR, consensusTime)
                        / 2;
        accumulatedCost += cryptoTransfer.getTransfers().getAccountAmountsCount() * hbarTxCost;
        return accumulatedCost;
    }

    private void replaceAliasWithId(
            final BalanceChange change,
            final List<BalanceChange> changes,
            final Map<ByteString, EntityNum> completedLazyCreates,
            Store store,
            EntityAddressSequencer entityAddressSequencer) {
        final var receiverAlias = change.getNonEmptyAliasIfPresent();
        if (completedLazyCreates.containsKey(receiverAlias)) {
            change.replaceNonEmptyAliasWith(completedLazyCreates.get(receiverAlias));
        } else {
            final var lazyCreateResult = autoCreationLogic.create(
                    change,
                    Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build(),
                    store,
                    entityAddressSequencer,
                    changes);
            validateTrue(lazyCreateResult.getLeft() == OK, lazyCreateResult.getLeft());
            completedLazyCreates.put(
                    receiverAlias,
                    EntityNum.fromAccountId(
                            change.counterPartyAccountId() == null
                                    ? change.accountId()
                                    : change.counterPartyAccountId()));
        }
    }

    protected ImpliedTransfers extrapolateImpliedTransferDetailsFromSyntheticTxn(
            final TransactionBody transactionBody, final AccountID sender) {
        final var explicitChanges = constructBalanceChanges(transactionBody, sender);
        return new ImpliedTransfers(explicitChanges);
    }

    private ResponseCodeEnum extrapolateValidityDetailsFromSyntheticTxn(final TransactionBody transactionBody) {
        final var op = transactionBody.getCryptoTransfer();
        return validityWithCurrentProps(op);
    }

    private ResponseCodeEnum validityWithCurrentProps(CryptoTransferTransactionBody transferOp) {
        final var hbarAdjustWrapper = transferOp.getTransfers();
        final var tokenAdjustsList = transferOp.getTokenTransfersList();
        final var hbarAdjusts = hbarAdjustWrapper.getAccountAmountsList();

        if (hasRepeatedAccount(hbarAdjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(hbarAdjusts)) {
            return INVALID_ACCOUNT_AMOUNTS;
        }

        return validateTokenTransferSemantics(tokenAdjustsList);
    }

    ResponseCodeEnum validateTokenTransferSemantics(List<TokenTransferList> tokenTransfersList) {
        if (tokenTransfersList.isEmpty()) {
            return OK;
        }
        ResponseCodeEnum validity;
        final Set<TokenID> uniqueTokens = new HashSet<>();
        final Set<Long> uniqueSerialNos = new HashSet<>();
        for (var tokenTransfers : tokenTransfersList) {
            validity = validateScopedTransferSemantics(uniqueTokens, tokenTransfers, uniqueSerialNos);
            if (validity != OK) {
                return validity;
            }
        }
        if (uniqueTokens.size() < tokenTransfersList.size()) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }
        return OK;
    }

    private ResponseCodeEnum validateScopedTransferSemantics(
            final Set<TokenID> uniqueTokens, final TokenTransferList tokenTransfers, final Set<Long> uniqueSerialNos) {
        if (!tokenTransfers.hasToken()) {
            return INVALID_TOKEN_ID;
        }
        uniqueTokens.add(tokenTransfers.getToken());
        final var ownershipChanges = tokenTransfers.getNftTransfersList();
        uniqueSerialNos.clear();
        for (final var ownershipChange : ownershipChanges) {
            if (ownershipChange.getSenderAccountID().equals(ownershipChange.getReceiverAccountID())) {
                return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            }
            if (!uniqueSerialNos.add(ownershipChange.getSerialNumber())) {
                return INVALID_ACCOUNT_AMOUNTS;
            }
        }

        final var adjusts = tokenTransfers.getTransfersList();
        for (var adjust : adjusts) {
            if (!adjust.hasAccountID()) {
                return INVALID_ACCOUNT_ID;
            }
        }
        if (hasRepeatedAccount(adjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(adjusts)) {
            return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
        }
        return OK;
    }

    boolean isNetZeroAdjustment(List<AccountAmount> adjusts) {
        var net = ZERO;
        for (var adjust : adjusts) {
            net = net.add(BigInteger.valueOf(adjust.getAmount()));
        }
        return net.equals(ZERO);
    }

    boolean hasRepeatedAccount(List<AccountAmount> adjusts) {
        final int n = adjusts.size();
        if (n < 2) {
            return false;
        }
        Set<AccountID> allowanceAAs = new HashSet<>();
        Set<AccountID> normalAAs = new HashSet<>();
        for (var i = 0; i < n; i++) {
            final var adjust = adjusts.get(i);
            if (adjust.getIsApproval()) {
                if (!allowanceAAs.contains(adjust.getAccountID())) {
                    allowanceAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            } else {
                if (!normalAAs.contains(adjust.getAccountID())) {
                    normalAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private List<BalanceChange> constructBalanceChanges(
            final TransactionBody transactionBody, final AccountID accountId) {
        final List<BalanceChange> allChanges = new ArrayList<>();
        final var transferOp = transactionBody.getCryptoTransfer();
        for (final TokenTransferList tokenTransferList : transferOp.getTokenTransfersList()) {
            final var token = tokenTransferList.getToken();
            tokenTransferList
                    .getTransfersList()
                    .forEach(aa -> allChanges.add(
                            BalanceChange.changingFtUnits(Id.fromGrpcToken(token), token, aa, accountId)));
            tokenTransferList
                    .getNftTransfersList()
                    .forEach(nft -> allChanges.add(
                            BalanceChange.changingNftOwnership(Id.fromGrpcToken(token), token, nft, accountId)));
        }

        for (final var hbarTransfer : transferOp.getTransfers().getAccountAmountsList()) {
            allChanges.add(BalanceChange.changingHbar(hbarTransfer, accountId));
        }

        return allChanges;
    }

    private void revertIfReceiverIsSystemAccount(final BalanceChange change) {
        final var accountAddress = change.counterPartyAccountId() != null
                ? EntityIdUtils.asTypedEvmAddress(change.counterPartyAccountId())
                : change.getAccount().asEvmAddress();

        validateFalseOrRevert(systemAccountDetector.test(accountAddress), INVALID_RECEIVING_NODE_ACCOUNT);
    }
}
