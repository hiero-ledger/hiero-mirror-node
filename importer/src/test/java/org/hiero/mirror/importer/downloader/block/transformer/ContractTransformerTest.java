// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionReceipt.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ContractTransformerTest extends AbstractTransformerTest {

    private static final ContractID HTS_PRECOMPILE_ADDRESS =
            ContractID.newBuilder().setContractNum(0x167).build();

    @SuppressWarnings("deprecation")
    private static final Consumer<TransactionRecord.Builder> ETHEREUM_RECORD_CUSTOMIZER = b -> {
        // calculated in parser
        b.clearEthereumHash();
        contractResultBuilder(b)
                .ifPresent(builder -> builder.clearCreatedContractIDs().clearContractNonces());
    };

    @SuppressWarnings("deprecation")
    private static final Consumer<TransactionRecord.Builder> EXPLICIT_CONTRACT_RESULT_CUSTOMIZER = b -> {
        contractResultBuilder(b).ifPresent(builder -> builder.clearAmount()
                // createdContractIDs is deprecated and no longer used, plus it's hard and sometimes impossible for
                // mirrornode to reconstruct it from block stream
                .clearCreatedContractIDs()
                .clearFunctionParameters()
                .clearGas());
    };

    @Test
    void contractCall() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .customize(this::finalize)
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                // - contract nonces should only be set in top level contract create or contract call which
                //   results in new contract created in child contract create
                // - signer nonce should only be set for ethereum transactions
                .record(r ->
                        r.getContractCallResultBuilder().clearContractNonces().clearSignerNonce())
                // will add back sidecar records once the supporting logic is updated
                .sidecarRecords(List::clear)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCallResultBuilder()
                        .clearBloom()
                        .clearLogInfo()
                        .clearContractNonces()
                        .clearSignerNonce())
                .sidecarRecords(List::clear)
                .status(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallInvalidContractId() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .transactionBody(b -> b.getContractIDBuilder().setEvmAddress(recordItemBuilder.bytes(20)))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCallResultBuilder()
                        .clearBloom()
                        .clearContractID()
                        .clearContractNonces()
                        .clearLogInfo()
                        .clearSignerNonce())
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INVALID_CONTRACT_ID)
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCreate() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCreate()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCreateResultBuilder()
                        // will test contract nonces once support is added
                        .clearContractNonces()
                        .clearSignerNonce())
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCreate()
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(Builder::clearContractID)
                .sidecarRecords(List::clear)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @MethodSource("provideContractIds")
    @ParameterizedTest(name = "delete contract with {0} contract id")
    void contractDelete(String type, ContractID contractIdInBody, ContractID contractIdInReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractDelete()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> r.setContractID(contractIdInReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractDeleteUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractDelete()
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @MethodSource("provideContractIds")
    @ParameterizedTest(name = "update contract with {0} contract id")
    void contractUpdate(String type, ContractID contractIdInBody, ContractID contractIdInReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> r.setContractID(contractIdInReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractUpdateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractUpdate()
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ethereum(boolean create) {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ethereumNoUsedGas(boolean create) {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .record(r -> contractResultBuilder(r).ifPresent(ContractFunctionResult.Builder::clearGasUsed))
                .receipt(Builder::clearContractID)
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void ethereumUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction()
                .receipt(Builder::clearContractID)
                .record(r -> r.clearContractCallResult().clearContractCreateResult())
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .sidecarRecords(List::clear)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Disabled("Fix and enable when EvmTraceData is fully supported")
    @Test
    void contractCallWithHTSPrecompileAndTrackPendingFungibleAirdropAmount() {
        // given
        // both tokens are fungible
        // receiver2 has unlimited auto token association slot, so she never gets pending airdrops
        var tokenId1 = recordItemBuilder.tokenId();
        var tokenId2 = recordItemBuilder.tokenId();
        var receiver1 = recordItemBuilder.accountId();
        var receiver2 = recordItemBuilder.accountId();
        var sender = recordItemBuilder.accountId();
        var contractCallRecordItem =
                recordItemBuilder.contractCall().customize(this::finalize).build();
        var parentConsensusTimestamp =
                contractCallRecordItem.getTransactionRecord().getConsensusTimestamp();
        // use the same precompile contract call result to work around the complexity to get the matching one
        // when building contract call output for child transactions
        var childPrecompileContractCallResult = htsPrecompileContractCallResult();
        var tokenAirdrop1RecordItem = recordItemBuilder
                .tokenAirdrop()
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId1)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-2000))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(1200))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver2)
                                        .setAmount(800)))
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId2)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-900))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(900))))
                .record(r -> r.clearTokenTransferLists()
                        .setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(tokenId1)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-800))
                                // receiver2 has unlimited auto association slot, resulting in immediate transfer
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver2)
                                        .setAmount(800)))
                        // there is preexisting pending airdrop, so amount should be accumulated. However since it's
                        // cancelled afterward, there is no state changes to tell the exact amount, the best shot
                        // would be the amount to transfer in the body
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId1))
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(900, receiver1, sender, tokenId2)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        // cancels pending airdrop <receiver1, sender, tokenId1>
        var tokenCancelAirdropRecordItem = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops()
                        .addPendingAirdrops(PendingAirdropId.newBuilder()
                                .setReceiverId(receiver1)
                                .setSenderId(sender)
                                .setFungibleTokenType(tokenId1)))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(2))
                .customize(this::finalize)
                .build();
        // airdrop again to accumulate the amount for <receiver1, sender, tokenId2>
        var tokenAirdrop2RecordItem = recordItemBuilder
                .tokenAirdrop()
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId2)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-300))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(300))))
                .record(r -> r.clearTokenTransferLists()
                        .setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId2)))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var builders = List.of(
                blockTransactionBuilder.contractCall(contractCallRecordItem),
                blockTransactionBuilder.tokenAirdrop(tokenAirdrop1RecordItem),
                blockTransactionBuilder.defaultBlockItem(tokenCancelAirdropRecordItem),
                blockTransactionBuilder.tokenAirdrop(tokenAirdrop2RecordItem));
        var blockItems = new ArrayList<BlockTransaction>();
        var childTransactionOutput = callContractOutput(childPrecompileContractCallResult);
        for (var builder : builders) {
            if (blockItems.isEmpty()) {
                // the parent
                blockItems.add(builder.stateChanges(s -> {
                            var stateChangesBuilder =
                                    s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                            var mapUpdate = pendingAirdropMapUpdate(
                                    fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId2));
                            s.add(stateChangesBuilder.addStateChanges(mapUpdate).build());
                        })
                        .build());
            } else {
                blockItems.add(builder
                        // link previous to get parent's state changes
                        .previous(blockItems.getLast())
                        .stateChanges(List::clear)
                        .transactionOutputs(outputs -> outputs.putAll(childTransactionOutput))
                        .build());
            }
        }
        var blockFile = blockFileBuilder.items(blockItems).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expectedItems = List.of(
                contractCallRecordItem, tokenAirdrop1RecordItem, tokenCancelAirdropRecordItem, tokenAirdrop2RecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expectedItems);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyInAnyOrderElementsOf(expectedParentItems);
        });
    }

    @Disabled("Fix and enable when EvmTraceData is fully supported")
    @Test
    void contractCallWithHTSPrecompileAndTrackTotalSupply() {
        // given
        var tokenId1 = recordItemBuilder.tokenId(); // a token created before contract call
        var contractCallRecordItem =
                recordItemBuilder.contractCall().customize(this::finalize).build();
        var parentConsensusTimestamp =
                contractCallRecordItem.getTransactionRecord().getConsensusTimestamp();
        // use the same precompile contract call result to work around the complexity to get the matching one
        // when building contract call output for child transactions
        var childPrecompileContractCallResult = htsPrecompileContractCallResult();
        var token2CreateRecordItem = recordItemBuilder
                .tokenCreate()
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var tokenId2 =
                token2CreateRecordItem.getTransactionRecord().getReceipt().getTokenID();
        var token3CreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.setInitialSupply(1000))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(2))
                .receipt(r -> r.setNewTotalSupply(1000))
                .customize(this::finalize)
                .build();
        var tokenId3 =
                token3CreateRecordItem.getTransactionRecord().getReceipt().getTokenID();
        var token2MintRecordItem = recordItemBuilder
                .tokenMint(TokenType.FUNGIBLE_COMMON)
                .transactionBody(b -> b.setToken(tokenId2).setAmount(1000))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(1000))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var token2BurnRecordItem = recordItemBuilder
                .tokenBurn()
                .transactionBody(b -> b.setToken(tokenId2).setAmount(200).clearSerialNumbers())
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(800))
                .recordItem(r -> r.transactionIndex(4))
                .customize(this::finalize)
                .build();
        var token2WipeRecordItem = recordItemBuilder
                .tokenWipe()
                .transactionBody(b -> b.setToken(tokenId2).setAmount(300).clearSerialNumbers())
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(500))
                .recordItem(r -> r.transactionIndex(5))
                .customize(this::finalize)
                .build();
        var token1DeleteRecordItem = recordItemBuilder
                .tokenDelete()
                .transactionBody(b -> b.setToken(tokenId1))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(6))
                .customize(this::finalize)
                .build();
        var builders = List.of(
                blockTransactionBuilder.contractCall(contractCallRecordItem),
                blockTransactionBuilder.tokenCreate(token2CreateRecordItem),
                blockTransactionBuilder.tokenCreate(token3CreateRecordItem),
                blockTransactionBuilder.tokenMint(token2MintRecordItem),
                blockTransactionBuilder.tokenBurn(token2BurnRecordItem),
                blockTransactionBuilder.tokenWipe(token2WipeRecordItem),
                blockTransactionBuilder.defaultBlockItem(token1DeleteRecordItem));
        var blockItems = new ArrayList<BlockTransaction>();
        var otherStateChanges = List.of(
                tokenMapUpdate(true, tokenId1, 1555), // total supply for token1 isn't used
                tokenMapUpdate(false, tokenId2, 500), // total supply after all changes
                tokenMapUpdate(false, tokenId3, 1000));
        var childTransactionOutput = callContractOutput(childPrecompileContractCallResult);
        for (var builder : builders) {
            if (blockItems.isEmpty()) {
                // the parent
                blockItems.add(builder.stateChanges(s -> {
                            var stateChangesBuilder =
                                    s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                            s.add(stateChangesBuilder
                                    .addAllStateChanges(otherStateChanges)
                                    .build());
                        })
                        .build());
            } else {
                blockItems.add(builder
                        // link previous to get parent's state changes
                        .previous(blockItems.getLast())
                        .stateChanges(List::clear)
                        .transactionOutputs(outputs -> outputs.putAll(childTransactionOutput))
                        .build());
            }
        }
        var blockFile = blockFileBuilder.items(blockItems).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expectedItems = List.of(
                contractCallRecordItem,
                token2CreateRecordItem,
                token3CreateRecordItem,
                token2MintRecordItem,
                token2BurnRecordItem,
                token2WipeRecordItem,
                token1DeleteRecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expectedItems);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyInAnyOrderElementsOf(expectedParentItems);
        });
    }

    private static Optional<ContractFunctionResult.Builder> contractResultBuilder(
            TransactionRecord.Builder recordBuilder) {
        if (recordBuilder.hasContractCallResult()) {
            return Optional.of(recordBuilder.getContractCallResultBuilder());
        }

        if (recordBuilder.hasContractCreateResult()) {
            return Optional.of(recordBuilder.getContractCreateResultBuilder());
        }

        return Optional.empty();
    }

    private static Stream<Arguments> provideContractIds() {
        var contractId = recordItemBuilder.contractId();
        var encoded = contractId.toBuilder()
                .setEvmAddress(DomainUtils.fromBytes(DomainUtils.toEvmAddress(contractId)))
                .build();
        var create2EvmAddress = contractId.toBuilder()
                .setEvmAddress(recordItemBuilder.evmAddress().getValue())
                .build();

        return Stream.of(
                Arguments.of("plain", contractId, contractId),
                Arguments.of("encoded evm address", encoded, contractId),
                Arguments.of("create2 evm address", create2EvmAddress, contractId));
    }

    private ContractFunctionResult htsPrecompileContractCallResult() {
        return recordItemBuilder.contractFunctionResult(HTS_PRECOMPILE_ADDRESS).build();
    }

    private Map<TransactionCase, TransactionOutput> callContractOutput(ContractFunctionResult contractFunctionResult) {
        var output = TransactionOutput.newBuilder()
                //
                // .setContractCall(CallContractOutput.newBuilder().setContractCallResult(contractFunctionResult))
                .build();
        return Map.of(TransactionCase.CONTRACT_CALL, output);
    }

    private PendingAirdropRecord fungiblePendingAirdropRecord(
            long amount, AccountID receiver, AccountID sender, TokenID tokenId) {
        return PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver)
                        .setSenderId(sender)
                        .setFungibleTokenType(tokenId))
                .setPendingAirdropValue(PendingAirdropValue.newBuilder().setAmount(amount))
                .build();
    }

    private StateChange tokenMapUpdate(boolean deleted, TokenID tokenId, long totalSupply) {
        var token = Token.newBuilder()
                .setDeleted(deleted)
                .setTokenId(tokenId)
                .setTotalSupply(totalSupply)
                .build();
        return StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder().setTokenValue(token)))
                .build();
    }

    private StateChange pendingAirdropMapUpdate(PendingAirdropRecord pendingAirdropRecord) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(pendingAirdropRecord.getPendingAirdropId()))
                        .setValue(MapChangeValue.newBuilder()
                                .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                        .setPendingAirdropValue(pendingAirdropRecord.getPendingAirdropValue()))))
                .build();
    }
}
