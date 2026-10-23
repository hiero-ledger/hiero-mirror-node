// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.RecordItemBuilder.DEFAULT_GAS_USED;

import com.hederahashgraph.api.proto.java.ClprChannel;
import com.hederahashgraph.api.proto.java.ClprChannelStatus;
import com.hederahashgraph.api.proto.java.ClprConnector;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.StreamMessage;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.repository.ClprChannelPendingCommitmentRepository;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorPendingCommitmentRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.test.performance.PerformanceProperties.SubType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.util.Version;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class RecordFileParserIntegrationTest extends ImporterIntegrationTest {

    private static final Version HAPI_0_70 = new Version(0, 70, 0);
    private static final ContractID HOOK_CONTRACT_ID =
            ContractID.newBuilder().setContractNum(RecordItem.HOOK_CONTRACT_NUM).build();
    private static final ContractID HTS_PRECOMPILE_CONTRACT_ID =
            ContractID.newBuilder().setContractNum(0x167).build();

    private final ClprChannelPendingCommitmentRepository clprChannelPendingCommitmentRepository;
    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorPendingCommitmentRepository clprConnectorPendingCommitmentRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMessageRepository clprMessageRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final ReactiveRedisOperations<String, StreamMessage> reactiveRedisOperations;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordFileParser recordFileParser;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void parse() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(recordFile2);

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 5 * transactions);
        assertThat(entityRepository.count()).isZero(); // Partial entities ignored
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(List.of(recordFile1, recordFile2));

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 5 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseSingleThenList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();
        var recordFile3 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(List.of(recordFile2, recordFile3));

        // then
        assertRecordFile(recordFile1, recordFile2, recordFile3);
        assertThat(cryptoTransferRepository.count()).isEqualTo(3 * 5 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(3 * transactions);
    }

    @Test
    void parseSingleFileWithNonceZeroContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).subType(SubType.CONTRACT_CALL));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithEmptyParentConsensusTimestampTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .entities(entities)
                        .subType(SubType.CONTRACT_CALL)
                        .nonce(5)
                        .isScheduled(false));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWitHavingOneTopLevelAndOneNotTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .entities(entities)
                        .subType(SubType.CONTRACT_CALL)
                        .nonce(5)
                        .isScheduled(false)
                        .parentConsensusTimestamp(
                                Timestamp.newBuilder().setSeconds(1403434L).build()));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithPositiveNonceAndScheduledTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .entities(entities)
                        .subType(SubType.CONTRACT_CALL)
                        .nonce(8)
                        .isScheduled(true));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithOneTopLevelAndOneNotTopLevelContractCall() {
        // given
        int transactions = 2;
        int entities = 1;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .entities(entities)
                        .subType(SubType.CONTRACT_CALL)
                        .nonce(5)
                        .isScheduled(false)
                        .parentConsensusTimestamp(
                                Timestamp.newBuilder().setSeconds(1403434L).build()));
        var recordFile = recordFileTemplate.build();
        var transactionRecord = recordFile.getItems().get(1).getTransactionRecord();
        var result = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();
        logsBloom.or(DomainUtils.toBytes(result.getBloom()));

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(entities * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithSystemFileUpdateTopLevelItems() {
        // given
        int transactions = 3;
        var recordFile = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .template(() -> recordItemBuilder
                                .fileUpdate()
                                .record(r -> r.getTransactionIDBuilder()
                                        .setNonce(7)
                                        .setScheduled(false)
                                        .setAccountID(com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                                                .setAccountNum(50)))))
                .build();

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isZero();
        assertThat(updatedRecordFile.getLogsBloom()).isEmpty();
    }

    @Test
    void parseWithLogIndexValidation() {
        // given
        int transactions = 10;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).type(TransactionType.CONTRACTCALL));
        var recordFile = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile);

        // then
        assertThat(recordFileRepository.findAll()).hasSize(1);
        final var contractLogs = contractLogRepository.findAll();
        assertThat(contractLogs).hasSize(transactions * 2);
        final var contractLogsList = new LinkedList<ContractLog>();
        contractLogs.forEach(contractLogsList::add);
        contractLogsList.sort(
                Comparator.comparing(ContractLog::getConsensusTimestamp).thenComparing(ContractLog::getIndex));

        final var index = new AtomicInteger();
        contractLogsList.forEach(cl -> {
            assertThat(cl.getIndex()).isEqualTo(index.getAndIncrement());
        });
    }

    @Test
    void topicMessage() {
        int count = 3;
        var topicMessage = recordItemBuilder.consensusSubmitMessage();
        var topicId = EntityId.of(topicMessage
                .build()
                .getTransactionBody()
                .getConsensusSubmitMessage()
                .getTopicID());
        var recordFile = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(count).template(() -> topicMessage))
                .build();

        var receive = reactiveRedisOperations
                .listenToChannel("topic." + topicId.getId())
                .map(Message::getMessage);
        StepVerifier.create(receive)
                .thenAwait(java.time.Duration.ofSeconds(1L))
                .then(() -> recordFileParser.parse(recordFile))
                .thenAwait(java.time.Duration.ofMillis(500L))
                .expectNextCount(count)
                .thenCancel()
                .verify(java.time.Duration.ofMillis(2000L));
        assertRecordFile(recordFile);
    }

    @Test
    void clprChannelLifecycle() {
        // given -- register, complete, submit a bundle, then close, correlated by channel_id and
        // ownership_commitment = keccak256(channel_id || public_key); exercises the in-batch upsert-merge logic
        var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var publicKey = DomainUtils.fromBytes(domainBuilder.bytes(64));
        var ownershipCommitment = DomainUtils.fromBytes(
                SignatureUtils.keccak256(DomainUtils.toBytes(channelId), DomainUtils.toBytes(publicKey)));

        var register = recordItemBuilder
                .clprRegisterChannel()
                .transactionBody(b -> b.setOwnershipCommitment(ownershipCommitment))
                .recordItem(r -> r.clprChannelPendingCommitment(ownershipCommitment))
                .build();

        var clprChannelActive = ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setChainId("hedera:mainnet")
                .setStatus(ClprChannelStatus.ACTIVE)
                .setNextMessageId(1)
                .setAckedMessageId(0)
                .setSentRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setReceivedMessageId(0)
                .setReceivedRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setOwnershipCommitment(ownershipCommitment)
                .setVerifierFingerprint(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .build();
        var complete = recordItemBuilder
                .clprCompleteChannel()
                .transactionBody(b -> b.setChannelId(channelId).setPublicKey(publicKey))
                .recordItem(r -> r.clprChannel(clprChannelActive))
                .build();

        var clprChannelAfterBundle =
                clprChannelActive.toBuilder().setNextMessageId(2).build();
        var submitBundle = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprChannel(clprChannelAfterBundle))
                .build();

        var clprChannelClosing = clprChannelAfterBundle.toBuilder()
                .setStatus(ClprChannelStatus.CLOSING)
                .build();
        var closeChannel = recordItemBuilder
                .clprCloseChannel()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprChannel(clprChannelClosing))
                .build();

        var recordFile = recordFileWithItems(List.of(register, complete, submitBundle, closeChannel), null);

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);

        var pendingCommitment = clprChannelPendingCommitmentRepository
                .findById(DomainUtils.toBytes(ownershipCommitment))
                .orElseThrow();
        assertThat(pendingCommitment.getCompletedTimestamp()).isNotNull();
        assertThat(pendingCommitment.getDeleted()).isFalse();

        var channel =
                clprChannelRepository.findById(DomainUtils.toBytes(channelId)).orElseThrow();
        assertThat(channel.getStatus()).isEqualTo(org.hiero.mirror.common.domain.clpr.ClprChannelStatus.CLOSING);
        assertThat(channel.getNextMessageId()).isEqualTo(2L);
        // chainId was only set at completion time; merging the later submitBundle/close updates must carry it
        // forward rather than null it out
        assertThat(channel.getChainId()).isEqualTo("hedera:mainnet");

        assertThat(clprMessageRepository.count()).isOne();
    }

    @Test
    void clprConnectorLifecycle() {
        // given -- register, complete, then deregister, correlated by connector_id and
        // commitment = keccak256(connector_id || public_key); exercises the in-batch upsert-merge logic
        var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var publicKey = DomainUtils.fromBytes(domainBuilder.bytes(64));
        var commitment = DomainUtils.fromBytes(
                SignatureUtils.keccak256(DomainUtils.toBytes(connectorId), DomainUtils.toBytes(publicKey)));

        var register = recordItemBuilder
                .clprRegisterConnector()
                .transactionBody(b -> b.setCommitment(commitment))
                .recordItem(r -> r.clprConnectorPendingCommitment(commitment))
                .build();

        var clprConnectorValue = ClprConnector.newBuilder()
                .setConnectorId(connectorId)
                .setChannelId(channelId)
                .setConnectorContract(domainBuilder.entityNum(4000L).toContractID())
                .build();
        var complete = recordItemBuilder
                .clprCompleteConnector()
                .transactionBody(b ->
                        b.setChannelId(channelId).setConnectorId(connectorId).setPublicKey(publicKey))
                .recordItem(r -> r.clprConnector(clprConnectorValue))
                .build();

        var stakeRecipient = domainBuilder.entityNum(5000L).toAccountID();
        var deregister = recordItemBuilder
                .clprDeregisterConnector()
                .transactionBody(b ->
                        b.setChannelId(channelId).setConnectorId(connectorId).setStakeRecipient(stakeRecipient))
                .build();

        var recordFile = recordFileWithItems(List.of(register, complete, deregister), null);

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);

        var pendingCommitment = clprConnectorPendingCommitmentRepository
                .findById(DomainUtils.toBytes(commitment))
                .orElseThrow();
        assertThat(pendingCommitment.getCompletedTimestamp()).isNotNull();

        var connector = clprConnectorRepository
                .findById(
                        new AbstractClprConnector.Id(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .orElseThrow();
        assertThat(connector.getDeleted()).isTrue();
        // connectorContractId was only set at completion time; merging the later deregister update must carry it
        // forward rather than null it out
        assertThat(connector.getConnectorContractId()).isNotNull();
    }

    @Test
    void clprConnectorSendMessageThenSubmitBundlePatch() {
        // given -- register + complete establish the connector, a ContractCall invoking the CLPR sendMessage system
        // contract bumps in_flight_message_count, then a SubmitBundle reply slashes and decrements it; exercises the
        // cross-handler (ContractCall + ClprSubmitBundle) same-batch merge onto one connector row
        var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var publicKey = DomainUtils.fromBytes(domainBuilder.bytes(64));
        var commitment = DomainUtils.fromBytes(
                SignatureUtils.keccak256(DomainUtils.toBytes(connectorId), DomainUtils.toBytes(publicKey)));

        var register = recordItemBuilder
                .clprRegisterConnector()
                .transactionBody(b -> b.setCommitment(commitment))
                .recordItem(r -> r.clprConnectorPendingCommitment(commitment))
                .build();

        var clprConnectorComplete = ClprConnector.newBuilder()
                .setConnectorId(connectorId)
                .setChannelId(channelId)
                .setConnectorContract(domainBuilder.entityNum(4100L).toContractID())
                .setLockedStake(100_000_000L)
                .build();
        var complete = recordItemBuilder
                .clprCompleteConnector()
                .transactionBody(b ->
                        b.setChannelId(channelId).setConnectorId(connectorId).setPublicKey(publicKey))
                .recordItem(r -> r.clprConnector(clprConnectorComplete))
                .build();

        // Each ClprConnector state change is the connector's full current state, not a delta, so later snapshots
        // are derived from the prior one rather than built from scratch.
        var clprConnectorAfterSend =
                clprConnectorComplete.toBuilder().setInFlightMessageCount(1).build();
        var sendMessage = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.clprConnectors(List.of(clprConnectorAfterSend)))
                .build();

        var clprConnectorAfterBundle = clprConnectorAfterSend.toBuilder()
                .setInFlightMessageCount(0)
                .setSlashCount(1)
                .setLockedStake(90_000_000L)
                .build();
        var submitBundle = recordItemBuilder
                .clprSubmitBundle()
                .recordItem(r -> r.clprConnectors(List.of(clprConnectorAfterBundle)))
                .build();

        var recordFile = recordFileWithItems(List.of(register, complete, sendMessage, submitBundle), null);

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);

        var connector = clprConnectorRepository
                .findById(
                        new AbstractClprConnector.Id(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .orElseThrow();
        assertThat(connector.getInFlightMessageCount()).isZero();
        assertThat(connector.getSlashCount()).isOne();
        assertThat(connector.getLockedStake()).isEqualTo(90_000_000L);
        // connectorContractId was only set at completion time; merging the sendMessage/submitBundle patches must
        // carry it forward rather than null it out
        assertThat(connector.getConnectorContractId()).isNotNull();
    }

    @Test
    void clprUpdateLedgerConfiguration() {
        // given -- protocol_version/chain_id differ between the submitted body and the state change to confirm the
        // persisted row comes from the state change, not the submitted body
        var realConfiguration = ClprLedgerConfiguration.newBuilder()
                .setProtocolVersion(1)
                .setChainId("hedera:mainnet")
                .setServiceAddress(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                .setInitialTrustAnchor(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setInitialTrustAnchorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .build();
        var updateLedgerConfiguration = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .transactionBody(
                        b -> b.getConfigurationBuilder().setProtocolVersion(999).setChainId("submitted-garbage"))
                .recordItem(r -> r.clprLedgerConfiguration(realConfiguration))
                .build();

        var recordFile = recordFileWithItems(List.of(updateLedgerConfiguration), null);

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);

        var persisted = clprLedgerConfigurationRepository
                .findById(updateLedgerConfiguration.getConsensusTimestamp())
                .orElseThrow();
        assertThat(persisted.getProtocolVersion()).isEqualTo(1L);
        assertThat(persisted.getChainId()).isEqualTo("hedera:mainnet");
    }

    @Test
    void parseHookDrivenTransferWithEmptyFileBloom() {
        final var items = hookDrivenTransferItems();
        final var recordFile = recordFileWithItems(items, null);

        recordFileParser.parse(recordFile);

        assertRecordFile(recordFile);
        final var updatedRecordFile =
                recordFileRepository.findById(recordFile.getConsensusEnd()).orElseThrow();
        final var hookContractResult = contractResultRepository
                .findById(items.get(1).getConsensusTimestamp())
                .orElseThrow();

        assertThat(contractLogRepository.findAll()).isNotEmpty();
        assertThat(updatedRecordFile.getLogsBloom())
                .hasSize(LogsBloomFilter.BYTE_SIZE)
                .isNotEqualTo(new byte[LogsBloomFilter.BYTE_SIZE])
                .isEqualTo(hookContractResult.getBloom());
    }

    @Test
    void parseBatchLastFileHasNoTopLevelContractTransactions() {
        final var firstFileItems = htsPrecompileChildUnderTopLevelContractCall();
        final var firstFile = recordFileWithItems(firstFileItems, null);
        final var lastFile = recordFileBuilder
                .recordFile()
                .previous(firstFile)
                .recordItem(TransactionType.CRYPTOTRANSFER)
                .build();

        recordFileParser.parse(List.of(firstFile, lastFile));

        assertThat(recordFileRepository.findAll()).hasSize(2);
        assertThat(contractLogRepository.findAll()).isNotEmpty();
        final var lastPersisted =
                recordFileRepository.findById(lastFile.getConsensusEnd()).orElseThrow();
        assertThat(lastPersisted.getLogsBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);
    }

    @Test
    @EnabledIfV1
    void rollback() {
        // when
        var recordFileTemplate = recordFileBuilder.recordFile().recordItem(TransactionType.CRYPTOTRANSFER);
        var recordFile1 = recordFileTemplate.build();
        var items = recordFile1.getItems();
        recordFileParser.parse(recordFile1);

        // then
        assertRecordFile(recordFile1);

        // when
        var recordFile2 = recordFileTemplate.build();
        recordFile2.setItems(items); // Re-processing same transactions should result in duplicate keys
        Assertions.assertThrows(ParserException.class, () -> recordFileParser.parse(recordFile2));

        // then
        assertRecordFile(recordFile1);
        assertThat(retryRecorder.getRetries(ParserException.class)).isEqualTo(2);
    }

    private List<RecordItem> hookDrivenTransferItems() {
        final var topLevel = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(HAPI_0_70))
                .build();
        final var hookCall = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ID)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ID))
                        .setParentConsensusTimestamp(
                                topLevel.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.previous(topLevel).hapiVersion(HAPI_0_70))
                .build();
        return List.of(topLevel, hookCall, htsPrecompileChild(topLevel, hookCall, 2));
    }

    private List<RecordItem> htsPrecompileChildUnderTopLevelContractCall() {
        final var topLevel = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_0_70))
                .build();
        return List.of(topLevel, htsPrecompileChild(topLevel, topLevel, 1));
    }

    private RecordItem htsPrecompileChild(final RecordItem topLevel, final RecordItem previous, final int nonce) {
        final var tokenTransfers = TokenTransferList.newBuilder()
                .setToken(recordItemBuilder.tokenId())
                .addTransfers(recordItemBuilder.accountAmount(recordItemBuilder.accountId(), -100))
                .addTransfers(recordItemBuilder.accountAmount(recordItemBuilder.accountId(), 100));
        return recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ID)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(nonce))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ID))
                        .setParentConsensusTimestamp(
                                topLevel.getTransactionRecord().getConsensusTimestamp())
                        .addTokenTransferLists(tokenTransfers))
                .recordItem(r -> r.previous(previous).hapiVersion(HAPI_0_70))
                .build();
    }

    private RecordFile recordFileWithItems(final List<RecordItem> items, final RecordFile previous) {
        return domainBuilder
                .recordFile()
                .customize(r -> {
                    r.items(items)
                            .consensusStart(items.getFirst().getConsensusTimestamp())
                            .consensusEnd(items.getLast().getConsensusTimestamp())
                            .count((long) items.size())
                            .sidecarCount(0)
                            .sidecars(List.of());
                    if (previous != null) {
                        r.index(previous.getIndex() + 1).previousHash(previous.getHash());
                    }
                })
                .get();
    }

    private void assertRecordFile(RecordFile... recordFiles) {
        assertThat(recordFileRepository.findAll())
                .hasSize(recordFiles.length)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("bytes", "items", "logsBloom", "sidecars")
                .containsExactlyInAnyOrder(recordFiles)
                .allSatisfy(rf -> {
                    assertThat(rf.getLoadStart()).isPositive();
                    assertThat(rf.getLoadEnd()).isPositive();
                    assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
                });
    }
}
