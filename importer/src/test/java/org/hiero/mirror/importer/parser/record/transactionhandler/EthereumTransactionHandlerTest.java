// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static org.hiero.mirror.common.util.CommonUtils.nextBytes;
import static org.hiero.mirror.importer.util.Utility.HALT_ON_ERROR_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.converter.HexToByteArrayConverter;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.hiero.mirror.importer.service.ContractBytecodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.data.util.Version;

final class EthereumTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final ByteString ETHEREUM_HASH = DomainUtils.fromBytes(nextBytes(32));

    private static final Version HAPI_VERSION_0_46_0 = new Version(0, 46, 0);

    @Mock
    private ContractBytecodeService contractBytecodeService;

    @Mock(strictness = LENIENT)
    private EthereumTransactionParser ethereumTransactionParser;

    @AfterEach
    void cleanup() {
        entityProperties.getPersist().setTrackNonce(true);
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        doReturn(domainBuilder.ethereumTransaction(true).get())
                .when(ethereumTransactionParser)
                .decode(any());
        return new EthereumTransactionHandler(
                contractBytecodeService, entityListener, entityProperties, ethereumTransactionParser);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setEthereumTransaction(EthereumTransactionBody.newBuilder().build());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setConsensusTimestamp(MODIFIED_TIMESTAMP)
                .setReceipt(getTransactionReceipt(ResponseCodeEnum.SUCCESS))
                .setContractCallResult(recordItemBuilder.contractFunctionResult(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void getType() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.ETHEREUMTRANSACTION);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void getEntityId(boolean create) {
        var recordItem = recordItemBuilder.ethereumTransaction(create).build();
        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
        var expectedId = EntityId.of(functionResult.getContractID());

        assertThat(transactionHandler.getEntity(recordItem)).isEqualTo(expectedId);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            'abab', , 'abab'
            , 'abab', 'abab'
            'abab', 'efef', 'abab'
            , , ''
            """)
    void updateContractResult(
            @ConvertWith(HexToByteArrayConverter.class) byte[] callData,
            @ConvertWith(HexToByteArrayConverter.class) byte[] callDataInFile,
            @ConvertWith(HexToByteArrayConverter.class) byte[] expectedFunctionParameters) {
        // given
        var contractResult = new ContractResult();
        boolean callDataInlined = callData != null;
        var callDataId = callDataInFile != null ? domainBuilder.entityId() : null;
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(callDataInlined)
                .customize(e -> e.callData(callData).callDataId(callDataId))
                .get();
        var recordItem = recordItemBuilder
                .ethereumTransaction()
                .recordItem(r -> r.blockstream(true).ethereumTransaction(ethereumTransaction))
                .build();
        boolean expectCallDataFromFile = !callDataInlined && callDataId != null;
        if (expectCallDataFromFile) {
            doReturn(callDataInFile).when(contractBytecodeService).get(callDataId);
        }

        // when
        transactionHandler.updateContractResult(contractResult, recordItem);

        // then
        verify(contractBytecodeService, times(expectCallDataFromFile ? 1 : 0)).get(any(EntityId.class));
        assertThat(contractResult)
                .returns(new BigInteger(ethereumTransaction.getValue()).longValue(), ContractResult::getAmount)
                .returns(expectedFunctionParameters, ContractResult::getFunctionParameters)
                .returns(ethereumTransaction.getGasLimit(), ContractResult::getGasLimit);
    }

    @Test
    void updateContractResultWithNullFromCallDataId() {
        // given
        var contractResult = new ContractResult();
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();
        var recordItem = recordItemBuilder
                .ethereumTransaction()
                .recordItem(r -> r.blockstream(true).ethereumTransaction(ethereumTransaction))
                .build();
        doReturn(null).when(contractBytecodeService).get(ethereumTransaction.getCallDataId());

        // when
        transactionHandler.updateContractResult(contractResult, recordItem);

        // then
        verify(contractBytecodeService).get(ethereumTransaction.getCallDataId());
        assertThat(contractResult)
                .returns(new BigInteger(ethereumTransaction.getValue()).longValue(), ContractResult::getAmount)
                .returns(EMPTY_BYTE_ARRAY, ContractResult::getFunctionParameters)
                .returns(ethereumTransaction.getGasLimit(), ContractResult::getGasLimit);
    }

    @Test
    void updateContractResultNullEthereumTransaction() {
        // given
        var contractResult = new ContractResult();
        var recordItem = recordItemBuilder
                .ethereumTransaction()
                .recordItem(r -> r.blockstream(true))
                .build();

        // when
        transactionHandler.updateContractResult(contractResult, recordItem);

        // then
        assertThat(contractResult)
                .returns(null, ContractResult::getAmount)
                .returns(EMPTY_BYTE_ARRAY, ContractResult::getFunctionParameters)
                .returns(0L, ContractResult::getGasLimit);
    }

    @ParameterizedTest
    @MethodSource("provideUpdateTransactionArguments")
    void updateTransaction(boolean create, boolean setPriorHapiVersion, long expectedNonce) {
        var fileId = EntityId.of(999L);
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        var gasLimit = ethereumTransaction.getGasLimit();
        var expectedValue = new BigInteger(ethereumTransaction.getValue())
                .divide(WEIBARS_TO_TINYBARS_BIGINT)
                .toByteArray();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var builder = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.setEthereumHash(ETHEREUM_HASH))
                .transactionBody(b -> b.setCallData(FileID.newBuilder().setFileNum(fileId.getNum())));
        if (setPriorHapiVersion) {
            builder.record(x -> {
                        // This version has no signer nonce so create it with a new builder to remove the field
                        var contractFunctionResult = ContractFunctionResult.newBuilder()
                                .setSenderId(recordItemBuilder.accountId())
                                .build();
                        if (create) {
                            x.setContractCreateResult(contractFunctionResult);
                        } else {
                            x.setContractCallResult(contractFunctionResult);
                        }
                    })
                    .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_46_0));
        }

        var recordItem = builder.build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        var body = recordItem.getTransactionBody().getEthereumTransaction();
        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction)
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(recordItem.getConsensusTimestamp(), EthereumTransaction::getConsensusTimestamp)
                .returns(DomainUtils.toBytes(body.getEthereumData()), EthereumTransaction::getData)
                .returns(gasLimit, EthereumTransaction::getGasLimit)
                .returns(DomainUtils.toBytes(ETHEREUM_HASH), EthereumTransaction::getHash)
                .returns(body.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(recordItem.getPayerAccountId(), EthereumTransaction::getPayerAccountId)
                .returns(expectedValue, EthereumTransaction::getValue);

        assertThat(recordItem.getEthereumTransaction()).isSameAs(ethereumTransaction);

        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
        var senderId = EntityId.of(functionResult.getSenderId());
        verify(entityListener)
                .onEntity(argThat(e -> e.getId() == senderId.getId()
                        && e.getTimestampRange() == null
                        && e.getEthereumNonce() == expectedNonce));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        verify(ethereumTransactionParser, never()).getHash(any(), any(), anyLong(), any(), anyBoolean());
    }

    @Test
    void updateTransactionEmptyHash() {
        // given
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        var gasLimit = ethereumTransaction.getGasLimit();
        var expectedValue = new BigInteger(ethereumTransaction.getValue())
                .divide(WEIBARS_TO_TINYBARS_BIGINT)
                .toByteArray();
        var recordItem = recordItemBuilder
                .ethereumTransaction(false)
                .record(r ->
                        r.clearEthereumHash().getContractCallResultBuilder().clearSignerNonce())
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_46_0))
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        ethereumTransaction.setConsensusTimestamp(consensusTimestamp);
        var body = recordItem.getTransactionBody().getEthereumTransaction();
        byte[] hash = domainBuilder.bytes(32);
        byte[] transactionBytes = DomainUtils.toBytes(body.getEthereumData());
        doReturn(hash)
                .when(ethereumTransactionParser)
                .getHash(
                        ethereumTransaction.getCallData(),
                        ethereumTransaction.getCallDataId(),
                        consensusTimestamp,
                        transactionBytes,
                        true);
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction)
                .returns(null, EthereumTransaction::getCallDataId)
                .returns(consensusTimestamp, EthereumTransaction::getConsensusTimestamp)
                .returns(transactionBytes, EthereumTransaction::getData)
                .returns(gasLimit, EthereumTransaction::getGasLimit)
                .returns(hash, EthereumTransaction::getHash)
                .returns(body.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(recordItem.getPayerAccountId(), EthereumTransaction::getPayerAccountId)
                .returns(expectedValue, EthereumTransaction::getValue);

        assertThat(recordItem.getEthereumTransaction()).isSameAs(ethereumTransaction);

        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), false);
        var senderId = EntityId.of(functionResult.getSenderId());
        verify(entityListener)
                .onEntity(argThat(e -> e.getId() == senderId.getId()
                        && e.getTimestampRange() == null
                        && e.getEthereumNonce() == ethereumTransaction.getNonce() + 1));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        verify(ethereumTransactionParser)
                .getHash(
                        ethereumTransaction.getCallData(),
                        ethereumTransaction.getCallDataId(),
                        consensusTimestamp,
                        transactionBytes,
                        true);
    }

    @Test
    void updateTransactionMissingSender() {
        boolean create = true;
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.getContractCreateResultBuilder().clearSenderId())
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(any());
        verify(entityListener, never()).onEntity(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void updateTransactionNullSignerNonce(boolean create) {
        var recordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> {
                    var contractFunctionResult =
                            recordItemBuilder.contractFunctionResult().clearSignerNonce();
                    if (create) {
                        x.setContractCreateResult(contractFunctionResult);
                    } else {
                        x.setContractCallResult(contractFunctionResult);
                    }
                })
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener, never()).onEntity(any());
    }

    @Test
    void updateTransactionEmptyWeiBars() {
        boolean create = true;
        var emptyBytes = new byte[] {};
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        ethereumTransaction.setGasLimit(null);
        ethereumTransaction.setGasPrice(emptyBytes);
        ethereumTransaction.setMaxFeePerGas(emptyBytes);
        ethereumTransaction.setMaxPriorityFeePerGas(emptyBytes);
        ethereumTransaction.setValue(emptyBytes);
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction)
                .returns(null, EthereumTransaction::getGasLimit)
                .returns(emptyBytes, EthereumTransaction::getGasPrice)
                .returns(emptyBytes, EthereumTransaction::getMaxFeePerGas)
                .returns(emptyBytes, EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(emptyBytes, EthereumTransaction::getValue);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void updateTransactionSkipNonceOnFailure(boolean hasInitCodeInBody) {
        var ethereumTransaction =
                domainBuilder.ethereumTransaction(hasInitCodeInBody).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder
                .ethereumTransaction(!hasInitCodeInBody)
                .record(x -> x.clearContractCreateResult().clearContractCallResult())
                .status(ResponseCodeEnum.WRONG_NONCE)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(any());
        verify(entityListener, never()).onEntity(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ParameterizedTest
    @MethodSource("provideNonceOnFailureArguments")
    void updateTransactionUpdateNonceOnFailure(
            boolean setPriorHapiVersion, ResponseCodeEnum status, Long expectedNonce) {
        boolean create = true;
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var builder = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.setEthereumHash(ETHEREUM_HASH))
                .status(status);
        if (setPriorHapiVersion) {
            builder.record(x ->
                            // This HAPI version has no signer nonce so create it with a new builder to remove the field
                            x.setContractCreateResult(ContractFunctionResult.newBuilder()
                                    .setSenderId(recordItemBuilder.accountId())
                                    .build()))
                    .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_46_0));
        }

        var recordItem = builder.build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);
        if (expectedNonce == null) {
            // If the HAPI version is prior to 0.46.0 and the status is not
            // SUCCESS, MAX_CHILD_RECORDS_EXCEEDED, or CONTRACT_REVERT_EXECUTED
            // then the nonce should not be updated
            verify(entityListener).onEthereumTransaction(ethereumTransaction);
            verify(entityListener, never()).onEntity(any());
        } else {
            var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
            var senderId = EntityId.of(functionResult.getSenderId());
            verify(entityListener).onEthereumTransaction(ethereumTransaction);
            verify(entityListener)
                    .onEntity(argThat(e -> e.getId() == senderId.getId()
                            && e.getTimestampRange() == null
                            && e.getEthereumNonce().longValue() == expectedNonce));
            assertThat(recordItem.getEntityTransactions())
                    .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
        }
    }

    @Test
    void updateTransactionEntityNonceNotUpdated() {
        boolean create = true;
        entityProperties.getPersist().setTrackNonce(false);
        var fileId = EntityId.of(999L);

        var recordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.setEthereumHash(ETHEREUM_HASH))
                .transactionBody(b -> b.setCallData(FileID.newBuilder().setFileNum(fileId.getNum())))
                .build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener, never()).onEntity(any());
    }

    @Test
    void updateTransactionInvalidWithHaltOnError() {
        var haltOnError = System.getProperty(HALT_ON_ERROR_PROPERTY, "false");
        System.setProperty(HALT_ON_ERROR_PROPERTY, "true");
        var recordItem = recordItemBuilder.ethereumTransaction(true).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();
        var expectedEntityTransactions = super.getExpectedEntityTransactions(recordItem, transaction);

        doThrow(InvalidDatasetException.class).when(ethereumTransactionParser).decode(any());

        assertThatThrownBy(() -> transactionHandler.updateTransaction(transaction, recordItem))
                .isInstanceOf(ParserException.class);
        verify(entityListener, never()).onEntity(any());
        verify(entityListener, never()).onEthereumTransaction(any());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
        System.setProperty(HALT_ON_ERROR_PROPERTY, haltOnError);
    }

    @Test
    void updateTransactionInvalidWithoutHaltOnError() {
        var recordItem = recordItemBuilder.ethereumTransaction(true).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();
        var expectedEntityTransactions = super.getExpectedEntityTransactions(recordItem, transaction);

        doThrow(InvalidDatasetException.class).when(ethereumTransactionParser).decode(any());

        assertDoesNotThrow(() -> transactionHandler.updateTransaction(transaction, recordItem));
        verify(entityListener, never()).onEntity(any());
        verify(entityListener, never()).onEthereumTransaction(any());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        entityProperties.getPersist().setEthereumTransactions(false);
        var recordItem = recordItemBuilder.ethereumTransaction(true).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp()))
                .get();
        var expectedEntityTransactions = super.getExpectedEntityTransactions(recordItem, transaction);

        transactionHandler.updateTransaction(transaction, recordItem);

        // verify parse and listener are never called
        verify(entityListener, never()).onEntity(any());
        verify(entityListener, never()).onEthereumTransaction(any());
        verify(ethereumTransactionParser, never()).decode(any());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @SuppressWarnings("java:S2699")
    @Disabled("Since this handler persists data for unsuccessful transactions & has tests for that")
    @Override
    @Test
    void updateTransactionUnsuccessful() {
        throw new UnsupportedOperationException("Missing method implementation");
    }

    private ContractFunctionResult getContractFunctionResult(TransactionRecord txnRecord, boolean create) {
        return create ? txnRecord.getContractCreateResult() : txnRecord.getContractCallResult();
    }

    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var txnRecord = recordItem.getTransactionRecord();
        return getExpectedEntityTransactions(
                recordItem,
                transaction,
                EntityId.of(txnRecord.getContractCallResult().getSenderId()),
                EntityId.of(txnRecord.getContractCreateResult().getSenderId()),
                EntityId.of(
                        recordItem.getTransactionBody().getEthereumTransaction().getCallData()));
    }

    private static Stream<Arguments> provideNonceOnFailureArguments() {
        long signerNonce = 10L;
        long incrementedNonce = 1235L;
        return Stream.of(
                Arguments.of(true, ResponseCodeEnum.SUCCESS, incrementedNonce),
                Arguments.of(false, ResponseCodeEnum.SUCCESS, signerNonce),
                Arguments.of(true, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, incrementedNonce),
                Arguments.of(false, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, signerNonce),
                Arguments.of(true, ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED, incrementedNonce),
                Arguments.of(false, ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED, signerNonce),
                Arguments.of(true, ResponseCodeEnum.WRONG_NONCE, null),
                Arguments.of(false, ResponseCodeEnum.WRONG_NONCE, signerNonce));
    }

    private static Stream<Arguments> provideUpdateTransactionArguments() {
        long signerNonce = 10L;
        long incrementedNonce = 1235L;
        return Stream.of(
                Arguments.of(true, true, incrementedNonce),
                Arguments.of(true, false, signerNonce),
                Arguments.of(false, true, incrementedNonce),
                Arguments.of(false, false, signerNonce));
    }
}
