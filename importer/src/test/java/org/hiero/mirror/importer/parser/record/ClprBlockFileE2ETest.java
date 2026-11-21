// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSING;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.CONTROL_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.MESSAGE_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.MESSAGE_REPLY_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessageReplyStatus.CONNECTOR_NOT_FOUND_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprConnectorMapUpdate;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hederahashgraph.api.proto.java.ClprConfigUpdate;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprControlMessage;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessagePayload;
import com.hederahashgraph.api.proto.java.ClprMessageReply;
import com.hederahashgraph.api.proto.java.ClprMessageReplyStatus;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprConnectorEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.parser.domain.BlockFileBuilder;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

/**
 * Verifies CLPR lifecycle, message, and control-message persistence through synthetic block-file transformation
 * and record-file parsing
 */
@RequiredArgsConstructor
final class ClprBlockFileE2ETest extends ImporterIntegrationTest {

    private static final Version HAPI_VERSION = new Version(0, 80, 0);

    private final BlockFileBuilder blockFileBuilder;
    private final BlockFileTransformer blockFileTransformer;
    private final BlockTransactionBuilder blockTransactionBuilder;
    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMessageRepository clprMessageRepository;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void clprChannelAndConnectorLifecycle(int commitIndex) {
        // OpenChannel -> RegisterConnector -> sendMessage -> SubmitBundle. Every transaction passes through
        // BlockFileTransformer and RecordFileParser.
        final var channelId = recordItemBuilder.bytes(32);
        final var connectorId = recordItemBuilder.bytes(32);
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();

        // Block 1: the channel is opened directly, active immediately.
        final var activeChannel = activeChannelValue(channelId, "hiero:testing", 1);
        final var openChannel = recordItemBuilder
                .clprOpenChannel()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(
                                new ClprChannelEntry(channelId, clprChannelMapUpdate(channelId, activeChannel)))))
                .customize(this::finalizeRecordItem)
                .build();

        // Block 2: the connector is registered directly, active immediately.
        final var activeConnector = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .setConnectorContract(recordItemBuilder.contractId())
                .setLockedStake(100_000_000L)
                .build();
        final var registerConnector = recordItemBuilder
                .clprRegisterConnector()
                .transactionBody(b -> b.setChannelId(channelId)
                        .setConnectorId(connectorId)
                        .setConnectorContract(activeConnector.getConnectorContract())
                        .setLockedStake(100_000_000L))
                .recordItem(r -> r.clprContextBuilder()
                        .clprConnectors(
                                List.of(new ClprConnectorEntry(connectorKey, clprConnectorMapUpdate(activeConnector)))))
                .customize(this::finalizeRecordItem)
                .build();

        // Block 3: the CLPR system-contract side effects belong to an ordinary ContractCall.
        final var sentRunningHash = recordItemBuilder.bytes(32);
        final var sender = recordItemBuilder.bytes(20);
        final var targetApplication = recordItemBuilder.bytes(20);
        final var message = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(connectorId)
                                .setMessageData(ByteString.copyFromUtf8("hello"))
                                .setSender(sender)
                                .setTargetApplication(targetApplication)))
                .setRunningHashAfterProcessing(sentRunningHash)
                .build();
        final var afterSend = activeChannel.toBuilder()
                .setNextMessageId(2)
                .setSentRunningHash(sentRunningHash)
                .build();
        final var afterSendConnector =
                activeConnector.toBuilder().setInFlightMessageCount(1).build();
        final var sendMessage = recordItemBuilder
                .contractCall()
                .customize(this::finalizeRecordItem)
                .build();
        final var sendMessageBlock = blockTransactionBuilder
                .contractCall(sendMessage, false)
                .stateChanges(changes -> changes.add(StateChanges.newBuilder()
                        .addStateChanges(channelStateChange(channelId, afterSend))
                        .addStateChanges(connectorStateChange(connectorKey, afterSendConnector))
                        .addStateChanges(messageStateChange(channelId, 1L, message))
                        .build()))
                .build();

        // Block 4: the bundle produces a reply and transitions the channel to CLOSING.
        final var replyRunningHash = recordItemBuilder.bytes(32);
        final var receivedRunningHash = recordItemBuilder.bytes(32);
        final var reply = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessageReply(ClprMessageReply.newBuilder()
                                .setMessageId(1)
                                .setStatus(ClprMessageReplyStatus.CONNECTOR_NOT_FOUND)
                                .setMessageReplyData(ByteString.EMPTY)))
                .setRunningHashAfterProcessing(replyRunningHash)
                .build();
        final var closingChannel = afterSend.toBuilder()
                .setStatus(CLOSING)
                .setNextMessageId(3)
                .setReceivedMessageId(1)
                .setReceivedRunningHash(receivedRunningHash)
                .setSentRunningHash(replyRunningHash)
                .build();
        final var submitBundle = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(
                                new ClprChannelEntry(channelId, clprChannelMapUpdate(channelId, closingChannel))))
                        .clprMessages(List.of(new ClprMessageEntry(messageKey(channelId, 2L), reply)))
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of()))
                .customize(this::finalizeRecordItem)
                .build();
        // Each transaction remains in its own record file. Vary only the database commit boundary
        // to exercise partial-update merging both within a batch and across committed batches.
        parseFiles(
                transformFiles(List.of(
                        List.of(blockTransactionBuilder
                                .clprOpenChannel(openChannel)
                                .build()),
                        List.of(blockTransactionBuilder
                                .clprRegisterConnector(registerConnector)
                                .build()),
                        List.of(sendMessageBlock),
                        List.of(blockTransactionBuilder
                                .clprSubmitBundle(submitBundle)
                                .build()))),
                commitIndex);

        assertThat(clprConnectorRepository.findById(
                        new AbstractClprConnector.Id(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId))))
                .get()
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(registerConnector.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(100_000_000L, ClprConnector::getLockedStake)
                .returns(1L, ClprConnector::getInFlightMessageCount);
        // The connector is created ACTIVE at registration, then the ContractCall changes its in-flight message
        // count via a full (non-partial) state change, which must historize the row regardless of commit
        // boundaries.
        final var connectorHistory = findHistory(ClprConnector.class);
        assertThat(connectorHistory)
                .singleElement()
                .returns(registerConnector.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(0L, ClprConnector::getInFlightMessageCount)
                .returns(
                        Range.closedOpen(
                                registerConnector.getConsensusTimestamp(), sendMessage.getConsensusTimestamp()),
                        ClprConnector::getTimestampRange);

        final var messages = allMessages();
        assertThat(messages).hasSize(2);
        final var persistedMessage = messages.stream()
                .filter(m -> m.getMessageId() == 1L)
                .findFirst()
                .orElseThrow();
        assertThat(persistedMessage.getMessageType()).isEqualTo(MESSAGE_FIELD_NUMBER);
        assertThat(persistedMessage.getConnectorId()).containsExactly(DomainUtils.toBytes(connectorId));
        assertThat(persistedMessage.getRunningHash()).containsExactly(DomainUtils.toBytes(sentRunningHash));
        assertThat(persistedMessage.getSender()).containsExactly(DomainUtils.toBytes(sender));
        assertThat(persistedMessage.getTargetApplication()).containsExactly(DomainUtils.toBytes(targetApplication));
        assertThat(persistedMessage.getMessageData()).containsExactly("hello".getBytes(StandardCharsets.UTF_8));

        final var persistedReply = messages.stream()
                .filter(m -> m.getMessageId() == 2L)
                .findFirst()
                .orElseThrow();
        assertThat(persistedReply)
                .returns(MESSAGE_REPLY_FIELD_NUMBER, ClprMessage::getMessageType)
                .returns(1L, ClprMessage::getReplyToMessageId)
                .returns(CONNECTOR_NOT_FOUND_VALUE, ClprMessage::getReplyStatus)
                .returns(null, ClprMessage::getConnectorId);
        assertThat(persistedReply.getRunningHash()).containsExactly(DomainUtils.toBytes(replyRunningHash));
        assertThat(persistedReply.getMessageData()).isEmpty();

        final var channel =
                clprChannelRepository.findById(DomainUtils.toBytes(channelId)).orElseThrow();
        assertThat(channel)
                .returns(CLOSING.getNumber(), ClprChannel::getStatus)
                .returns(3L, ClprChannel::getNextMessageId)
                .returns(1L, ClprChannel::getReceivedMessageId)
                .returns("hiero:testing", ClprChannel::getChainId);
        assertThat(channel.getReceivedRunningHash()).containsExactly(DomainUtils.toBytes(receivedRunningHash));
        assertThat(channel.getSentRunningHash()).containsExactly(DomainUtils.toBytes(replyRunningHash));
        assertThat(findHistory(ClprChannel.class))
                .extracting(ClprChannel::getTimestampRange)
                .contains(
                        Range.closedOpen(openChannel.getConsensusTimestamp(), sendMessage.getConsensusTimestamp()),
                        Range.closedOpen(sendMessage.getConsensusTimestamp(), submitBundle.getConsensusTimestamp()));

        assertThat(clprMessageRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void clprCloseChannelAdminSweep(int commitIndex) {
        // An admin sweep of a legacy pending ownership commitment from a network that predates ClprOpenChannel: no
        // Channel row or clpr_channel_value state change exists for it, and no such commitment can exist in this
        // mirror node's data, so the transaction is a no-op.
        final var sweep = recordItemBuilder
                .clprCloseChannel()
                .transactionBody(b ->
                        b.setChannelId(recordItemBuilder.bytes(32)).setOwnershipCommitment(recordItemBuilder.bytes(32)))
                .recordItem(r -> r.clprContextBuilder().clprChannels(List.of()))
                .customize(this::finalizeRecordItem)
                .build();
        final var recordFiles = transformFiles(
                List.of(List.of(blockTransactionBuilder.clprCloseChannel(sweep).build())));
        assertThat(recordFiles.getLast().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getClprContext()).isNotNull();
            assertThat(item.getClprContext().getClprChannels()).isEmpty();
        });
        parseFiles(recordFiles, commitIndex);

        assertThat(clprChannelRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprControlMessageConfigPropagation(int commitIndex) {
        final var channel = createClprMessageChannel("eip155:298", 7);
        final var connectorId = recordItemBuilder.bytes(32);
        final var serviceAddress = recordItemBuilder.bytes(20);
        final var anchor = recordItemBuilder.bytes(64);
        final var throttles = ClprThrottles.newBuilder()
                .setMaxGasPerMessage(1_000_000L)
                .setMaxMessagePayloadBytes(65_536)
                .setMaxMessagesPerBundle(6)
                .setMaxQueueDepth(1_000)
                .setMaxSyncBytes(1_048_576L)
                .build();
        final var configuration = com.hederahashgraph.api.proto.java.ClprLedgerConfiguration.newBuilder()
                .setChainId("eip155:298")
                .setProtocolVersion(1)
                .setServiceAddress(serviceAddress)
                .setInitialTrustAnchor(anchor)
                .setInitialTrustAnchorId(anchor)
                .setThrottles(throttles)
                .build();

        final var configUpdate = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .transactionBody(b -> b.setConfiguration(configuration))
                .recordItem(r -> r.clprContextBuilder()
                        .clprLedgerConfigurationUpdate(SingletonUpdateChange.newBuilder()
                                .setClprLedgerConfigurationValue(configuration)
                                .build()))
                .customize(this::finalizeRecordItem)
                .build();

        // The configuration timestamp is part of the CONTROL payload, and must not be replaced with the bundle's
        // consensus timestamp when decoding the payload.
        final var controlConfiguration = configuration.toBuilder()
                .setTimestamp(configUpdate.getTransactionRecord().getConsensusTimestamp())
                .build();
        final var controlRunningHash = recordItemBuilder.bytes(32);
        final var controlValue = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setControl(ClprControlMessage.newBuilder()
                                .setConfigUpdate(ClprConfigUpdate.newBuilder().setConfiguration(controlConfiguration))))
                .setRunningHashAfterProcessing(controlRunningHash)
                .build();
        final var messageEntries = new ArrayList<ClprMessageEntry>();
        messageEntries.add(new ClprMessageEntry(messageKey(channel.channelId(), 7L), controlValue));
        for (var id = 8L; id <= 13L; id++) {
            final var data = ByteString.copyFromUtf8("msg-" + (id - 2));
            final var value = ClprMessageValue.newBuilder()
                    .setPayload(ClprMessagePayload.newBuilder()
                            .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                    .setConnectorId(connectorId)
                                    .setMessageData(data)
                                    .setSender(recordItemBuilder.bytes(20))
                                    .setTargetApplication(ByteString.copyFrom(new byte[20]))))
                    .setRunningHashAfterProcessing(recordItemBuilder.bytes(32))
                    .build();
            messageEntries.add(new ClprMessageEntry(messageKey(channel.channelId(), id), value));
        }
        final var afterBundle = channel.value().toBuilder().setNextMessageId(14).build();
        final var bundle = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channel.channelId()))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                channel.channelId(), clprChannelMapUpdate(channel.channelId(), afterBundle))))
                        .clprMessages(messageEntries)
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of()))
                .customize(this::finalizeRecordItem)
                .build();

        parseFiles(
                transformClprFiles(
                        channel,
                        List.of(
                                blockTransactionBuilder
                                        .clprUpdateLedgerConfiguration(configUpdate)
                                        .build(),
                                blockTransactionBuilder.clprSubmitBundle(bundle).build())),
                commitIndex);

        final var persistedConfiguration = clprLedgerConfigurationRepository
                .findById(configUpdate.getConsensusTimestamp())
                .orElseThrow();
        assertThat(persistedConfiguration)
                .returns(configUpdate.getConsensusTimestamp(), ClprLedgerConfiguration::getConsensusTimestamp)
                .returns("eip155:298", ClprLedgerConfiguration::getChainId)
                .returns(1L, ClprLedgerConfiguration::getProtocolVersion)
                .satisfies(c ->
                        assertThat(c.getThrottle().getMaxMessagesPerBundle()).isEqualTo(6L));
        assertThat(persistedConfiguration.getServiceAddress()).containsExactly(DomainUtils.toBytes(serviceAddress));
        assertThat(persistedConfiguration.getInitialTrustAnchor()).containsExactly(DomainUtils.toBytes(anchor));

        final var messages = allMessages();
        assertThat(messages).hasSize(7);
        final var control = messages.stream()
                .filter(m -> m.getMessageId() == 7L)
                .findFirst()
                .orElseThrow();
        assertThat(control)
                .returns(CONTROL_FIELD_NUMBER, ClprMessage::getMessageType)
                .returns(bundle.getConsensusTimestamp(), ClprMessage::getConsensusTimestamp)
                .returns(null, ClprMessage::getConnectorId)
                .returns(null, ClprMessage::getMessageData);
        assertThat(control.getRunningHash()).containsExactly(DomainUtils.toBytes(controlRunningHash));
        assertThat(control.getControlMessage()).isNotNull();
        final var propagated = control.getControlMessage().getConfigUpdate().getConfiguration();
        assertThat(propagated)
                .returns(configUpdate.getConsensusTimestamp(), c -> c.getTimestamp())
                .returns("eip155:298", c -> c.getChainId())
                .returns(1L, c -> c.getProtocolVersion())
                .satisfies(c ->
                        assertThat(c.getThrottle().getMaxMessagesPerBundle()).isEqualTo(6L));
        assertThat(propagated.getServiceAddress()).containsExactly(DomainUtils.toBytes(serviceAddress));
        assertThat(propagated.getInitialTrustAnchor()).containsExactly(DomainUtils.toBytes(anchor));
        assertThat(propagated.getInitialTrustAnchorId()).containsExactly(DomainUtils.toBytes(anchor));

        for (var id = 8L; id <= 13L; id++) {
            final var messageId = id;
            final var dataMessage = messages.stream()
                    .filter(m -> m.getMessageId() == messageId)
                    .findFirst()
                    .orElseThrow();
            assertThat(dataMessage.getMessageType()).isEqualTo(MESSAGE_FIELD_NUMBER);
            assertThat(dataMessage.getConnectorId()).containsExactly(DomainUtils.toBytes(connectorId));
            assertThat(dataMessage.getMessageData())
                    .containsExactly(ByteString.copyFromUtf8("msg-" + (id - 2)).toByteArray());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromContractCall(int commitIndex) {
        final var channel = createClprMessageChannel("hiero:testing", 1);
        final var call = recordItemBuilder
                .contractCall()
                .record(record -> record.setConsensusTimestamp(timestampAfter(channel.consensusTimestamp(), 1)))
                .customize(this::finalizeRecordItem)
                .build();
        final var runningHash = recordItemBuilder.bytes(32);
        final var update = channel.value().toBuilder()
                .setNextMessageId(2)
                .setSentRunningHash(runningHash)
                .build();
        final var transaction = blockTransactionBuilder
                .contractCall(call, false)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(
                        channel.channelId(), update, List.of(messageValue("contract-call", runningHash)))))
                .build();

        parseClprCall(channel, List.of(transaction), 1, commitIndex);
        assertClprMessages(channel.channelId(), 2, runningHash, call.getConsensusTimestamp(), List.of("contract-call"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromEthereumTransaction(int commitIndex) {
        final var channel = createClprMessageChannel("hiero:testing", 1);
        final var ethereum = recordItemBuilder
                .ethereumTransaction()
                .record(record -> record.setConsensusTimestamp(timestampAfter(channel.consensusTimestamp(), 1)))
                .customize(this::finalizeRecordItem)
                .build();
        final var runningHash = recordItemBuilder.bytes(32);
        final var update = channel.value().toBuilder()
                .setNextMessageId(2)
                .setSentRunningHash(runningHash)
                .build();
        final var transaction = blockTransactionBuilder
                .ethereum(ethereum)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(
                        channel.channelId(), update, List.of(messageValue("ethereum", runningHash)))))
                .build();

        parseClprCall(channel, List.of(transaction), 1, commitIndex);
        assertClprMessages(channel.channelId(), 2, runningHash, ethereum.getConsensusTimestamp(), List.of("ethereum"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromHookExecution(int commitIndex) {
        final var channel = createClprMessageChannel("hiero:testing", 1);
        final var parentTimestamp = timestampAfter(channel.consensusTimestamp(), 1);
        final var hookTimestamp = timestampAfter(channel.consensusTimestamp(), 2);
        final var transfer = recordItemBuilder
                .cryptoTransfer()
                .record(record -> record.setConsensusTimestamp(parentTimestamp))
                .customize(this::finalizeRecordItem)
                .build();
        final var hook = recordItemBuilder
                .contractCall(hookContractId())
                .transactionBodyWrapper(body -> body.setTransactionID(
                        body.getTransactionID().toBuilder().setNonce(1)))
                .record(record -> record.setConsensusTimestamp(hookTimestamp)
                        .setParentConsensusTimestamp(parentTimestamp)
                        .setTransactionID(record.getTransactionID().toBuilder().setNonce(1)))
                .customize(this::finalizeRecordItem)
                .build();
        final var runningHash = recordItemBuilder.bytes(32);
        final var updatedChannel = channel.value().toBuilder()
                .setNextMessageId(2)
                .setSentRunningHash(runningHash)
                .build();

        // Consensus puts the committed state changes on the triggering transaction. The hook is a
        // synthetic ContractCall child with an executedHookId, and must not persist them again.
        final var parent = blockTransactionBuilder
                .cryptoTransfer(transfer)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(
                        channel.channelId(), updatedChannel, List.of(messageValue("hook", runningHash)))))
                .build();
        final var hookCall = hookExecution(hook, parent);
        assertThat(hookCall.getParent()).isSameAs(parent);

        parseClprCall(channel, List.of(parent, hookCall), 1, commitIndex);
        assertClprMessages(channel.channelId(), 2, runningHash, transfer.getConsensusTimestamp(), List.of("hook"));
    }

    /**
     * Exercises an importer-only synthetic block shape. HIP-1195 currently prohibits hook executions inside
     * atomic batches on consensus nodes, so this is a parser resilience test rather than a valid network scenario.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromAtomicBatchWithHookContractCallAndEthereum(int commitIndex) {
        final var channel = createClprMessageChannel("hiero:testing", 1);
        final var batchTimestamp = timestampAfter(channel.consensusTimestamp(), 1);
        final var transferTimestamp = timestampAfter(channel.consensusTimestamp(), 2);
        final var hookTimestamp = timestampAfter(channel.consensusTimestamp(), 3);
        final var contractTimestamp = timestampAfter(channel.consensusTimestamp(), 4);
        final var ethereumTimestamp = timestampAfter(channel.consensusTimestamp(), 5);

        final var transfer = recordItemBuilder
                .cryptoTransfer()
                .record(record ->
                        record.setConsensusTimestamp(transferTimestamp).setParentConsensusTimestamp(batchTimestamp))
                .customize(this::finalizeRecordItem)
                .build();
        final var hook = recordItemBuilder
                .contractCall(hookContractId())
                .transactionBodyWrapper(body -> body.setTransactionID(
                        body.getTransactionID().toBuilder().setNonce(1)))
                .record(record -> record.setConsensusTimestamp(hookTimestamp)
                        .setParentConsensusTimestamp(transferTimestamp)
                        .setTransactionID(record.getTransactionID().toBuilder().setNonce(1)))
                .customize(this::finalizeRecordItem)
                .build();
        final var call = recordItemBuilder
                .contractCall()
                .record(record ->
                        record.setConsensusTimestamp(contractTimestamp).setParentConsensusTimestamp(batchTimestamp))
                .customize(this::finalizeRecordItem)
                .build();
        final var ethereum = recordItemBuilder
                .ethereumTransaction()
                .record(record ->
                        record.setConsensusTimestamp(ethereumTimestamp).setParentConsensusTimestamp(batchTimestamp))
                .customize(this::finalizeRecordItem)
                .build();
        // The synthetic hook is a child of the transfer, not a separate atomic-batch inner transaction.
        final var batch = recordItemBuilder
                .atomicBatch(List.of(transfer.getTransaction(), call.getTransaction(), ethereum.getTransaction()))
                .record(record -> record.setConsensusTimestamp(batchTimestamp))
                .customize(this::finalizeRecordItem)
                .build();

        final var runningHash = recordItemBuilder.bytes(32);
        final var updatedChannel = channel.value().toBuilder()
                .setNextMessageId(4)
                .setSentRunningHash(runningHash)
                .build();
        final var messageValues = List.of(
                messageValue("hook", recordItemBuilder.bytes(32)),
                messageValue("contract-call", recordItemBuilder.bytes(32)),
                messageValue("ethereum", runningHash));

        // The atomic batch's state changes represent the final, committed state of all inner
        // executions. Each child shares this context but must not process its messages again.
        final var batchBlock = blockTransactionBuilder
                .atomicBatch(batch)
                .stateChanges(changes ->
                        changes.add(clprSendMessageChanges(channel.channelId(), updatedChannel, messageValues)))
                .build();
        final var transferBlock = blockTransactionBuilder
                .cryptoTransfer(transfer)
                .previous(batchBlock)
                .build();
        final var hookBlock = hookExecution(hook, transferBlock);
        final var callBlock = blockTransactionBuilder
                .contractCall(call, false)
                .previous(hookBlock)
                .build();
        final var ethereumBlock =
                blockTransactionBuilder.ethereum(ethereum).previous(callBlock).build();
        assertThat(transferBlock.getParent()).isSameAs(batchBlock);
        assertThat(hookBlock.getParent()).isSameAs(transferBlock);
        assertThat(callBlock.getParent()).isSameAs(batchBlock);
        assertThat(ethereumBlock.getParent()).isSameAs(batchBlock);

        parseClprCall(channel, List.of(batchBlock, transferBlock, hookBlock, callBlock, ethereumBlock), 3, commitIndex);
        assertClprMessages(
                channel.channelId(),
                4,
                runningHash,
                batch.getConsensusTimestamp(),
                List.of("hook", "contract-call", "ethereum"));
    }

    private com.hederahashgraph.api.proto.java.ClprChannel activeChannelValue(
            ByteString channelId, String chainId, long nextMessageId) {
        return com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setChainId(chainId)
                .setStatus(ACTIVE)
                .setNextMessageId(nextMessageId)
                .setReceivedRunningHash(ByteString.copyFrom(new byte[32]))
                .setSentRunningHash(ByteString.copyFrom(new byte[32]))
                .setVerifierContract(recordItemBuilder.contractId())
                .setVerifierFingerprint(recordItemBuilder.bytes(32))
                .build();
    }

    private List<ClprMessage> allMessages() {
        final var messages = new ArrayList<ClprMessage>();
        clprMessageRepository.findAll().forEach(messages::add);
        return messages;
    }

    private void assertClprMessages(
            ByteString channelId,
            long nextMessageId,
            ByteString runningHash,
            long expectedTimestamp,
            List<String> expectedData) {
        final var messages = allMessages();
        assertThat(messages).hasSize(expectedData.size());
        for (var i = 0; i < expectedData.size(); i++) {
            final var messageId = i + 1L;
            final var data = expectedData.get(i);
            final var message = messages.stream()
                    .filter(item -> item.getMessageId() == messageId)
                    .findFirst()
                    .orElseThrow();
            assertThat(message.getMessageType()).isEqualTo(MESSAGE_FIELD_NUMBER);
            assertThat(message.getConsensusTimestamp()).isEqualTo(expectedTimestamp);
            assertThat(message.getChannelId()).containsExactly(DomainUtils.toBytes(channelId));
            assertThat(message.getConnectorId()).hasSize(32);
            assertThat(message.getSender()).hasSize(20);
            assertThat(message.getTargetApplication()).hasSize(20);
            assertThat(message.getRunningHash()).hasSize(32);
            assertThat(message.getMessageData())
                    .containsExactly(ByteString.copyFromUtf8(data).toByteArray());
        }
        final var persisted =
                clprChannelRepository.findById(DomainUtils.toBytes(channelId)).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ACTIVE.getNumber());
        assertThat(persisted.getNextMessageId()).isEqualTo(nextMessageId);
        assertThat(persisted.getSentRunningHash()).containsExactly(DomainUtils.toBytes(runningHash));
    }

    private StateChange channelStateChange(ByteString channelId, com.hederahashgraph.api.proto.java.ClprChannel value) {
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_CLPR_CHANNELS_VALUE)
                .setMapUpdate(clprChannelMapUpdate(channelId, value))
                .build();
    }

    private StateChanges clprSendMessageChanges(
            ByteString channelId,
            com.hederahashgraph.api.proto.java.ClprChannel updatedChannel,
            List<ClprMessageValue> messages) {
        final var changes = StateChanges.newBuilder().addStateChanges(channelStateChange(channelId, updatedChannel));
        for (var i = 0; i < messages.size(); i++) {
            changes.addStateChanges(messageStateChange(channelId, i + 1L, messages.get(i)));
        }
        return changes.build();
    }

    private StateChange connectorStateChange(
            ClprConnectorKey key, com.hederahashgraph.api.proto.java.ClprConnector value) {
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_CLPR_CONNECTORS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setClprConnectorKey(key))
                        .setValue(MapChangeValue.newBuilder().setClprConnectorValue(value)))
                .build();
    }

    private ClprMessageChannel createClprMessageChannel(String chainId, long nextMessageId) {
        final var channelId = recordItemBuilder.bytes(32);
        final var value = activeChannelValue(channelId, chainId, nextMessageId);
        final var openChannel = recordItemBuilder
                .clprOpenChannel()
                .transactionBody(body -> body.setChannelId(channelId))
                .recordItem(item -> item.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(channelId, clprChannelMapUpdate(channelId, value)))))
                .customize(this::finalizeRecordItem)
                .build();
        final var setupFiles = List.of(
                List.of(blockTransactionBuilder.clprOpenChannel(openChannel).build()));
        return new ClprMessageChannel(channelId, value, setupFiles, openChannel.getConsensusTimestamp());
    }

    private void finalizeRecordItem(RecordItemBuilder.Builder<?> builder) {
        builder.contractTransactionPredicate(null)
                .recordItem(r -> r.congestionPricingMultiplier(
                                recordItemBuilder.accountId().getAccountNum())
                        .blockstream(true)
                        .hapiVersion(HAPI_VERSION));
    }

    private ContractID hookContractId() {
        return ContractID.newBuilder()
                .setContractNum(RecordItem.HOOK_CONTRACT_NUM)
                .build();
    }

    private BlockTransaction hookExecution(RecordItem recordItem, BlockTransaction previous) {
        final var owner = previous.getTransactionBody()
                .getCryptoTransfer()
                .getTransfers()
                .getAccountAmounts(0)
                .getAccountID();
        final var hookId = HookId.newBuilder()
                .setEntityId(HookEntityId.newBuilder().setAccountId(owner))
                .setHookId(7)
                .build();
        final var transaction = blockTransactionBuilder
                .contractCall(recordItem, false)
                .previous(previous)
                .transactionOutputs(outputs -> {
                    final var output = outputs.get(TransactionOutput.TransactionCase.CONTRACT_CALL);
                    final var contractCall = output.getContractCall();
                    final var evmResult =
                            contractCall.getEvmTransactionResult().toBuilder().setExecutedHookId(hookId);
                    outputs.put(
                            TransactionOutput.TransactionCase.CONTRACT_CALL,
                            output.toBuilder()
                                    .setContractCall(contractCall.toBuilder().setEvmTransactionResult(evmResult))
                                    .build());
                })
                .build();
        final var output = transaction
                .getTransactionOutput(TransactionOutput.TransactionCase.CONTRACT_CALL)
                .orElseThrow();
        assertThat(output.getContractCall()
                        .getEvmTransactionResult()
                        .getExecutedHookId()
                        .getHookId())
                .isEqualTo(7L);
        return transaction;
    }

    private ClprMessageKey messageKey(ByteString channelId, long messageId) {
        return ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(messageId)
                .build();
    }

    private StateChange messageStateChange(ByteString channelId, long messageId, ClprMessageValue value) {
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_CLPR_MESSAGE_QUEUE_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setClprMessageKey(messageKey(channelId, messageId)))
                        .setValue(MapChangeValue.newBuilder().setClprMessageValue(value)))
                .build();
    }

    private ClprMessageValue messageValue(String data, ByteString runningHash) {
        return ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(recordItemBuilder.bytes(32))
                                .setMessageData(ByteString.copyFromUtf8(data))
                                .setSender(recordItemBuilder.bytes(20))
                                .setTargetApplication(recordItemBuilder.bytes(20))))
                .setRunningHashAfterProcessing(runningHash)
                .build();
    }

    private List<RecordFile> transformFiles(List<List<BlockTransaction>> transactionsByFile) {
        final var recordFiles = new ArrayList<RecordFile>(transactionsByFile.size());
        String previousHash = null;
        for (final var transactions : transactionsByFile) {
            final var builder = blockFileBuilder.items(transactions);
            if (previousHash != null) {
                builder.previousHash(previousHash);
            }
            final var blockFile = builder.build();
            recordFiles.add(blockFileTransformer.transform(blockFile));
            previousHash = blockFile.getHash();
        }
        return recordFiles;
    }

    private List<RecordFile> transformClprFiles(ClprMessageChannel channel, List<BlockTransaction> transactions) {
        final var transactionsByFile = new ArrayList<>(channel.setupFiles());
        transactionsByFile.add(transactions);
        return transformFiles(transactionsByFile);
    }

    private void parseFiles(List<RecordFile> recordFiles, int commitIndex) {
        assertThat(commitIndex).isBetween(0, recordFiles.size());
        if (commitIndex == 0) {
            // Preserve the original per-file transaction behavior as the baseline.
            recordFiles.forEach(recordFileParser::parse);
            return;
        }

        // parse(List) commits a batch of files together. Change where that batch ends while
        // keeping the same records and file boundaries. The remaining files form a second batch.
        recordFileParser.parse(recordFiles.subList(0, commitIndex));
        if (commitIndex < recordFiles.size()) {
            recordFileParser.parse(recordFiles.subList(commitIndex, recordFiles.size()));
        }
    }

    private void parseClprCall(
            ClprMessageChannel channel, List<BlockTransaction> transactions, int expectedMessages, int commitIndex) {
        final var recordFiles = transformClprFiles(channel, transactions);
        final var recordFile = recordFiles.getLast();
        assertThat(recordFile.getItems()).hasSize(transactions.size());
        final var root = recordFile.getItems().getFirst();
        assertThat(root.getClprContext()).isNotNull();
        assertThat(root.getClprContext().getClprMessages()).hasSize(expectedMessages);
        assertThat(root.getClprContext().getClprChannels()).hasSize(1);
        for (var i = 1; i < recordFile.getItems().size(); i++) {
            assertThat(recordFile.getItems().get(i).getClprContext()).isNull();
        }
        parseFiles(recordFiles, commitIndex);
        assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channel.channelId())))
                .isPresent();
    }

    private Timestamp timestampAfter(long consensusTimestamp, long nanoseconds) {
        final var time = consensusTimestamp + nanoseconds;
        return Timestamp.newBuilder()
                .setSeconds(Math.floorDiv(time, 1_000_000_000L))
                .setNanos((int) Math.floorMod(time, 1_000_000_000L))
                .build();
    }

    private record ClprMessageChannel(
            ByteString channelId,
            com.hederahashgraph.api.proto.java.ClprChannel value,
            List<List<BlockTransaction>> setupFiles,
            long consensusTimestamp) {}
}
