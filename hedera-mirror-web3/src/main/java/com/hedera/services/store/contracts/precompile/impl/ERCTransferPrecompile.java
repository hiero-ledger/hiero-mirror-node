// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BOOL;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.addSignedAdjustment;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.ERCTransferParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

/**
 * Copied Precompile from hedera-services. Differences with the original:
 * 1. Stateless logic
 * 2. Use abstraction for the state by introducing {@link Store} interface.
 * 3. Post body logic relies on `CryptoTransferTransactionBody` rather than `CryptoTransferWrapper`.
 * 4. Reworked Log methods to add item for any transfer.
 * 5. Provided implementation for getFunctionSelectors() from {@link Precompile} interface.
 */
public class ERCTransferPrecompile extends TransferPrecompile {

    private static final Function ERC_TRANSFER_FUNCTION = new Function("transfer(address,uint256)", BOOL);
    private static final Bytes ERC_TRANSFER_SELECTOR = Bytes.wrap(ERC_TRANSFER_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function ERC_TRANSFER_FROM_FUNCTION = new Function("transferFrom(address,address,uint256)");
    private static final Bytes ERC_TRANSFER_FROM_SELECTOR = Bytes.wrap(ERC_TRANSFER_FROM_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_FROM_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TRANSFER_FROM_FUNCTION =
            new Function("transferFrom(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_SELECTOR = Bytes.wrap(HAPI_TRANSFER_FROM_FUNCTION.selector());
    private static final Function HAPI_TRANSFER_FROM_NFT_FUNCTION =
            new Function("transferFromNFT(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_NFT_SELECTOR = Bytes.wrap(HAPI_TRANSFER_FROM_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TRANSFER_FROM_DECODER =
            TypeFactory.create("(bytes32,bytes32,bytes32,uint256)");

    private final EncodingFacade encoder;

    public ERCTransferPrecompile(
            PrecompilePricingUtils pricingUtils,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            TransferLogic transferLogic,
            ContextOptionValidator contextOptionValidator,
            AutoCreationLogic autoCreationLogic,
            SyntheticTxnFactory syntheticTxnFactory,
            EntityAddressSequencer entityAddressSequencer,
            EncodingFacade encoder,
            Predicate<Address> systemAccountDetector) {
        super(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer,
                systemAccountDetector);
        this.encoder = encoder;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        if (bodyParams instanceof ERCTransferParams transferParams) {
            final var senderAddress = transferParams.senderAddress();
            final var tokenID = transferParams.tokenID();
            final var exists = transferParams.exists();
            final var transferOp =
                    switch (transferParams.functionId()) {
                        case AbiConstants.ABI_ID_ERC_TRANSFER ->
                            decodeERCTransfer(input, tokenID, senderAddress, aliasResolver);
                        case AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                                AbiConstants.ABI_ID_TRANSFER_FROM,
                                AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> {
                            final var operatorID = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
                            yield decodeERCTransferFrom(
                                    input, tokenID, transferParams.tokenAccessor(), operatorID, aliasResolver, exists);
                        }
                        default -> null;
                    };
            Objects.requireNonNull(transferOp, "Unable to decode function input");

            return syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
        }
        return TransactionBody.newBuilder();
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var cryptoTransfer = transactionBody.getCryptoTransfer();
        final var transfer = cryptoTransfer.getTokenTransfersList().get(0);
        final var tokenAddress = EntityIdUtils.asTypedEvmAddress(transfer.getToken());
        final var token = store.getToken(tokenAddress, OnMissing.THROW);
        final var tokenID = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
        final var isFungible = TokenType.FUNGIBLE_COMMON.equals(token.getType());
        if (!isFungible) {
            final var nftTransfer = transfer.getNftTransfers(0);
            store.getUniqueToken(NftId.fromGrpc(tokenID, nftTransfer.getSerialNumber()), OnMissing.THROW);
        }
        try {
            super.run(frame, transactionBody);
        } catch (InvalidTransactionException e) {
            throw new InvalidTransactionException(e.getResponseCode(), true);
        }

        if (isFungible) {
            frame.addLogs(getLogForFungibleTransfer(tokenAddress, cryptoTransfer));
        } else {
            frame.addLogs(getLogForNftExchange(tokenAddress, cryptoTransfer));
        }
        TokenID ercTokenID = null;
        final var functionId = frame.getInputData().slice(24).getInt(0);
        if (AbiConstants.ABI_ID_ERC_TRANSFER_FROM == functionId || AbiConstants.ABI_ID_ERC_TRANSFER == functionId) {
            ercTokenID = tokenID;
        }
        return new TokenTransferResult(isFungible, ercTokenID);
    }

    public static CryptoTransferWrapper decodeERCTransfer(
            final Bytes input,
            final TokenID tokenID,
            final Address senderAddress,
            final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_SELECTOR, ERC_TRANSFER_DECODER);

        final var recipient = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var amount = (BigInteger) decodedArguments.get(1);
        final var caller = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustment(fungibleTransfers, tokenID, recipient, amount.longValueExact(), false);
        addSignedAdjustment(fungibleTransfers, tokenID, caller, -amount.longValueExact(), false);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeERCTransferFrom(
            final Bytes input,
            final TokenID tokenID,
            final TokenAccessor tokenAccessor,
            final AccountID operatorID,
            final UnaryOperator<byte[]> aliasResolver,
            final Predicate<Address> exists) {

        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final var offset = tokenID == null ? 1 : 0;
        Tuple decodedArguments;
        final TokenID token;
        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_FROM_SELECTOR, ERC_TRANSFER_FROM_DECODER);
            token = tokenID;
        } else {
            decodedArguments = decodeHapiArguments(input);
            token = convertAddressBytesToTokenID(decodedArguments.get(0));
        }

        final var from = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var to = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset + 1), aliasResolver, exists);
        final var isFungible =
                TokenType.FUNGIBLE_COMMON.equals(tokenAccessor.typeOf(EntityIdUtils.asTypedEvmAddress(token)));
        if (isFungible) {
            final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
            final var amount = (BigInteger) decodedArguments.get(offset + 2);

            addSignedAdjustment(fungibleTransfers, token, to, amount.longValueExact(), false);

            addSignedAdjustment(fungibleTransfers, token, from, -amount.longValueExact(), true);

            final var tokenTransferWrappers =
                    Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
            return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        } else {
            final List<NftExchange> nonFungibleTransfers = new ArrayList<>();
            final var serialNo = ((BigInteger) decodedArguments.get(offset + 2)).longValueExact();
            if (operatorID.equals(from)) {
                nonFungibleTransfers.add(new NftExchange(serialNo, token, from, to));
            } else {
                nonFungibleTransfers.add(NftExchange.fromApproval(serialNo, token, from, to));
            }

            final var tokenTransferWrappers =
                    Collections.singletonList(new TokenTransferWrapper(nonFungibleTransfers, NO_FUNGIBLE_TRANSFERS));
            return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        }
    }

    private static Tuple decodeHapiArguments(Bytes input) {
        final var functionBytes = input.slice(0, 4);
        if (HAPI_TRANSFER_FROM_SELECTOR.equals(functionBytes)) {
            return decodeFunctionCall(input, HAPI_TRANSFER_FROM_SELECTOR, HAPI_TRANSFER_FROM_DECODER);
        } else {
            return decodeFunctionCall(input, HAPI_TRANSFER_FROM_NFT_SELECTOR, HAPI_TRANSFER_FROM_DECODER);
        }
    }

    private List<Log> getLogForFungibleTransfer(final Address logger, final CryptoTransferTransactionBody transferOp) {
        final var transferLists = transferOp.getTokenTransfersList();
        List<Log> logs = new ArrayList<>();
        for (final var transfers : transferLists) {
            for (final var fungibleTransfer : transfers.getTransfersList()) {
                logs.add(EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(AbiConstants.TRANSFER_EVENT)
                        .forIndexedArgument(EntityIdUtils.asTypedEvmAddress(fungibleTransfer.getAccountID()))
                        .forDataItem(String.valueOf((fungibleTransfer.getAmount())))
                        .build());
            }
        }

        return logs;
    }

    private List<Log> getLogForNftExchange(final Address logger, final CryptoTransferTransactionBody transferOp) {

        final var transferList = transferOp.getTokenTransfersList();
        List<Log> logs = new ArrayList<>();
        for (final var transfers : transferList) {
            for (final var nftTransfer : transfers.getNftTransfersList()) {
                logs.add(EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(AbiConstants.TRANSFER_EVENT)
                        .forIndexedArgument(EntityIdUtils.asTypedEvmAddress(nftTransfer.getSenderAccountID()))
                        .forIndexedArgument(EntityIdUtils.asTypedEvmAddress(nftTransfer.getReceiverAccountID()))
                        .forIndexedArgument(nftTransfer.getSerialNumber())
                        .build());
            }
        }
        return logs;
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var transferRunResult = (TokenTransferResult) runResult;
        if (transferRunResult.tokenID() != null) {
            return transferRunResult.isFungible() ? encoder.encodeEcFungibleTransfer(true) : Bytes.EMPTY;
        } else {
            return super.getSuccessResultFor(runResult);
        }
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(
                AbiConstants.ABI_ID_ERC_TRANSFER,
                AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                AbiConstants.ABI_ID_TRANSFER_FROM,
                AbiConstants.ABI_ID_TRANSFER_FROM_NFT);
    }
}
