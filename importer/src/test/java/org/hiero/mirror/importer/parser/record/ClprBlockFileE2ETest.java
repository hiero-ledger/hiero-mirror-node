// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSED;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSING;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.DRAINED;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.CONTROL_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.MESSAGE_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessagePayload.MESSAGE_REPLY_FIELD_NUMBER;
import static com.hederahashgraph.api.proto.java.ClprMessageReplyStatus.CONNECTOR_NOT_FOUND_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprConnectorMapUpdate;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_ENDPOINT_MANIFEST;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_LEDGER_CONFIGURATION;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt64Value;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapDeleteChange;
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
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
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
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
    private final @Qualifier(CACHE_CLPR_ENDPOINT_MANIFEST) CacheManager clprEndpointManifestCacheManager;
    private final ClprEndpointManifestRepository clprEndpointManifestRepository;
    private final @Qualifier(CACHE_CLPR_LEDGER_CONFIGURATION) CacheManager clprLedgerConfigurationCacheManager;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMessageRepository clprMessageRepository;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, 0
            false, 1
            false, 2
            false, 3
            false, 4
            true, 0
            true, 1
            true, 2
            true, 3
            true, 4
            """)
    void clprChannelAndConnectorLifecycle(boolean stateDiffingEnabled, int commitIndex) {
        // CreateChannel -> CreateConnector -> sendMessage -> SubmitBundle. Every transaction passes through
        // BlockFileTransformer and RecordFileParser.
        final var channelId = recordItemBuilder.bytes(32);
        final var connectorId = recordItemBuilder.bytes(32);
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();

        // Block 1: the channel is opened directly, active immediately.
        final var activeChannel = activeChannelValue(channelId, "hiero:testing");
        final var createChannel = createChannel(activeChannel);

        // Block 2: the connector is created directly, active immediately.
        final var activeConnector = connectorValue(channelId, connectorId, 100_000_000L);
        final var createConnector = createConnector(activeConnector);

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
                        .addStateChanges(channelStateChange(sendMessageChannelUpdate(afterSend, stateDiffingEnabled)))
                        .addStateChanges(connectorStateChange(
                                sendMessageConnectorUpdate(connectorKey, afterSendConnector, stateDiffingEnabled)))
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
        final var bundleUpdate = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        .setStatus(CLOSING)
                        .setNextMessageId(3)
                        .setReceivedMessageId(1)
                        .setReceivedRunningHash(receivedRunningHash)
                        .setSentRunningHash(replyRunningHash)
                        .build()
                : closingChannel;
        final var submitBundle = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                channelId, channelMapUpdate(channelId, bundleUpdate, stateDiffingEnabled))))
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
                                .clprCreateChannel(createChannel)
                                .build()),
                        List.of(blockTransactionBuilder
                                .clprCreateConnector(createConnector)
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
                .returns(createConnector.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(100_000_000L, ClprConnector::getLockedStake)
                .returns(activeConnector.getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(1L, ClprConnector::getInFlightMessageCount)
                .returns(
                        Range.atLeast(
                                stateDiffingEnabled
                                        ? createConnector.getConsensusTimestamp()
                                        : sendMessage.getConsensusTimestamp()),
                        ClprConnector::getTimestampRange);
        // Counter-only partial updates preserve the original timestamp range.
        final var connectorHistory = findHistory(ClprConnector.class);
        if (stateDiffingEnabled) {
            assertThat(connectorHistory).isEmpty();
        } else {
            assertThat(connectorHistory)
                    .singleElement()
                    .returns(createConnector.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                    .returns(0L, ClprConnector::getInFlightMessageCount)
                    .returns(
                            Range.closedOpen(
                                    createConnector.getConsensusTimestamp(), sendMessage.getConsensusTimestamp()),
                            ClprConnector::getTimestampRange);
        }

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
                .returns("hiero:testing", ClprChannel::getChainId)
                .returns(DomainUtils.toBytes(activeChannel.getSalt()), ClprChannel::getSalt)
                .returns(DomainUtils.toBytes(activeChannel.getChannelContext()), ClprChannel::getChannelContext);
        assertThat(channel.getReceivedRunningHash()).containsExactly(DomainUtils.toBytes(receivedRunningHash));
        assertThat(channel.getSentRunningHash()).containsExactly(DomainUtils.toBytes(replyRunningHash));
        if (stateDiffingEnabled) {
            assertThat(findHistory(ClprChannel.class))
                    .singleElement()
                    .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                    .returns(2L, ClprChannel::getNextMessageId)
                    .returns(0L, ClprChannel::getReceivedMessageId)
                    .returns(
                            Range.closedOpen(
                                    createChannel.getConsensusTimestamp(), submitBundle.getConsensusTimestamp()),
                            ClprChannel::getTimestampRange);
        } else {
            assertThat(findHistory(ClprChannel.class))
                    .satisfiesExactlyInAnyOrder(
                            history -> assertThat(history)
                                    .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                                    .returns(1L, ClprChannel::getNextMessageId)
                                    .returns(0L, ClprChannel::getReceivedMessageId)
                                    .returns(
                                            Range.closedOpen(
                                                    createChannel.getConsensusTimestamp(),
                                                    sendMessage.getConsensusTimestamp()),
                                            ClprChannel::getTimestampRange),
                            history -> assertThat(history)
                                    .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                                    .returns(2L, ClprChannel::getNextMessageId)
                                    .returns(0L, ClprChannel::getReceivedMessageId)
                                    .returns(
                                            Range.closedOpen(
                                                    sendMessage.getConsensusTimestamp(),
                                                    submitBundle.getConsensusTimestamp()),
                                            ClprChannel::getTimestampRange));
        }

        assertThat(clprMessageRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprControlMessageConfigPropagation(int commitIndex) {
        final var channel = createClprMessageChannel("eip155:298");
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
        messageEntries.add(new ClprMessageEntry(messageKey(channel.channelId(), 1L), controlValue));
        for (var id = 2L; id <= 7L; id++) {
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
        final var afterBundle = channel.value().toBuilder().setNextMessageId(8).build();
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
                .filter(m -> m.getMessageId() == 1L)
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

        for (var id = 2L; id <= 7L; id++) {
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
    @ValueSource(booleans = {false, true})
    void clprPartialSingletonUpdates(boolean commitBetweenUpdates) {
        // given -- initialize both singletons with full values, as in a genesis maintenance transaction.
        final var genesis = recordItemBuilder
                .clprMaintenanceEndpointManifestAndLedgerConfiguration()
                .customize(this::finalizeRecordItem)
                .build();
        final var context = genesis.getClprContext();
        final var configuration = context.getClprLedgerConfigurationUpdate().getClprLedgerConfigurationValue();
        final var manifest = context.getClprEndpointManifestUpdate().getClprEndpointManifestValue();
        final var updatedThrottles = configuration.getThrottles().toBuilder()
                .setMaxMessagesPerBundle(configuration.getThrottles().getMaxMessagesPerBundle() + 1)
                .build();

        // Diffing replaces the entire changed top-level throttles message, not just a nested field.
        final var configUpdate = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .transactionBody(
                        b -> b.setConfiguration(configuration.toBuilder().setThrottles(updatedThrottles)))
                .recordItem(r -> r.clprContextBuilder()
                        .clprLedgerConfigurationUpdate(SingletonUpdateChange.newBuilder()
                                .setPartial(true)
                                .setClprLedgerConfigurationValue(
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration.newBuilder()
                                                .setThrottles(updatedThrottles))
                                .build()))
                .customize(this::finalizeRecordItem)
                .build();

        // Exercise the diff format's explicit clearing of a repeated field, even before CN emits partial updates.
        final var manifestUpdate = recordItemBuilder
                .clprMaintenance()
                .recordItem(r -> r.clprContextBuilder()
                        .clprEndpointManifestUpdate(SingletonUpdateChange.newBuilder()
                                .setPartial(true)
                                .addClearedFields(
                                        com.hederahashgraph.api.proto.java.ClprEndpointManifest.ENDPOINTS_FIELD_NUMBER)
                                .setClprEndpointManifestValue(
                                        com.hederahashgraph.api.proto.java.ClprEndpointManifest.newBuilder()
                                                .setVersion(manifest.getVersion() + 1))
                                .build()))
                .customize(this::finalizeRecordItem)
                .build();
        final var recordFiles = transformFiles(List.of(
                List.of(blockTransactionBuilder.clprMaintenance(genesis).build()),
                List.of(blockTransactionBuilder
                        .clprUpdateLedgerConfiguration(configUpdate)
                        .build()),
                List.of(blockTransactionBuilder.clprMaintenance(manifestUpdate).build())));

        // when -- exercise both an uncommitted cached baseline and a committed baseline loaded from the database.
        if (commitBetweenUpdates) {
            for (final var recordFile : recordFiles) {
                recordFileParser.parse(recordFile);
                resetCacheManager(clprEndpointManifestCacheManager);
                resetCacheManager(clprLedgerConfigurationCacheManager);
            }
        } else {
            recordFileParser.parse(recordFiles);
        }

        // then -- each append-only row must contain the full merged value, including all omitted fields.
        assertThat(clprLedgerConfigurationRepository.findAll())
                .containsExactlyInAnyOrder(
                        expectedLedgerConfiguration(genesis.getConsensusTimestamp(), configuration),
                        expectedLedgerConfiguration(
                                configUpdate.getConsensusTimestamp(),
                                configuration.toBuilder()
                                        .setThrottles(updatedThrottles)
                                        .build()));
        final var endpoint = manifest.getEndpoints(0);
        final var expectedEndpoint = ClprEndpoint.builder()
                .accountId(DomainUtils.toBytes(endpoint.getAccountId()))
                .serviceEndpoint(new ClprServiceEndpoint(
                        endpoint.getServiceEndpoint().getIpAddress(),
                        endpoint.getServiceEndpoint().getPort()))
                .tlsCertificate(DomainUtils.toBytes(endpoint.getTlsCertificate()))
                .build();
        final var serviceAddress = DomainUtils.toBytes(manifest.getServiceAddress());
        assertThat(clprEndpointManifestRepository.findAll())
                .containsExactlyInAnyOrder(
                        ClprEndpointManifest.builder()
                                .consensusTimestamp(genesis.getConsensusTimestamp())
                                .endpoints(List.of(expectedEndpoint))
                                .serviceAddress(serviceAddress)
                                .version(manifest.getVersion())
                                .build(),
                        ClprEndpointManifest.builder()
                                .consensusTimestamp(manifestUpdate.getConsensusTimestamp())
                                .endpoints(List.of())
                                .serviceAddress(serviceAddress)
                                .version(manifest.getVersion() + 1)
                                .build());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromContractCall(int commitIndex) {
        final var channel = createClprMessageChannelWithConnector();
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
        final var messageValues =
                List.of(messageValue(channel.connector().getConnectorId(), "contract-call", runningHash));
        final var transaction = blockTransactionBuilder
                .contractCall(call, false)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(channel, update, messageValues)))
                .build();

        parseClprCall(channel, List.of(transaction), 1, commitIndex);
        assertClprMessages(channel, call.getConsensusTimestamp(), messageValues);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromEthereumTransaction(int commitIndex) {
        final var channel = createClprMessageChannelWithConnector();
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
        final var messageValues = List.of(messageValue(channel.connector().getConnectorId(), "ethereum", runningHash));
        final var transaction = blockTransactionBuilder
                .ethereum(ethereum)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(channel, update, messageValues)))
                .build();

        parseClprCall(channel, List.of(transaction), 1, commitIndex);
        assertClprMessages(channel, ethereum.getConsensusTimestamp(), messageValues);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromHookExecution(int commitIndex) {
        final var channel = createClprMessageChannelWithConnector();
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
        final var messageValues = List.of(messageValue(channel.connector().getConnectorId(), "hook", runningHash));
        final var parent = blockTransactionBuilder
                .cryptoTransfer(transfer)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(channel, updatedChannel, messageValues)))
                .build();
        final var hookCall = hookExecution(hook, parent);
        assertThat(hookCall.getParent()).isSameAs(parent);

        parseClprCall(channel, List.of(parent, hookCall), 1, commitIndex);
        assertClprMessages(channel, transfer.getConsensusTimestamp(), messageValues);
    }

    /**
     * Exercises an importer-only synthetic block shape. HIP-1195 currently prohibits hook executions inside
     * atomic batches on consensus nodes, so this is a parser resilience test rather than a valid network scenario.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void clprSendMessageFromAtomicBatchWithHookContractCallAndEthereum(int commitIndex) {
        final var channel = createClprMessageChannelWithConnector();
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
                messageValue(channel.connector().getConnectorId(), "hook", recordItemBuilder.bytes(32)),
                messageValue(channel.connector().getConnectorId(), "contract-call", recordItemBuilder.bytes(32)),
                messageValue(channel.connector().getConnectorId(), "ethereum", runningHash));

        // The atomic batch's state changes represent the final, committed state of all inner
        // executions. Each child shares this context but must not process its messages again.
        final var batchBlock = blockTransactionBuilder
                .atomicBatch(batch)
                .stateChanges(changes -> changes.add(clprSendMessageChanges(channel, updatedChannel, messageValues)))
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
        assertClprMessages(channel, batch.getConsensusTimestamp(), messageValues);
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,1", "false,2", "true,0", "true,1", "true,2"})
    void clprSendMessageFromFailedAtomicBatch(boolean includeUncommittedChanges, int commitIndex) {
        final var channel = createClprMessageChannelWithConnector();
        final var batchTimestamp = timestampAfter(channel.consensusTimestamp(), 1);
        final var call = recordItemBuilder
                .contractCall()
                .record(r -> r.setConsensusTimestamp(timestampAfter(channel.consensusTimestamp(), 2))
                        .setParentConsensusTimestamp(batchTimestamp))
                .status(ResponseCodeEnum.REVERTED_SUCCESS)
                .customize(this::finalizeRecordItem)
                .build();
        final var ethereum = recordItemBuilder
                .ethereumTransaction()
                .record(r -> r.setConsensusTimestamp(timestampAfter(channel.consensusTimestamp(), 3))
                        .setParentConsensusTimestamp(batchTimestamp))
                .status(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                .customize(this::finalizeRecordItem)
                .build();
        final var batch = recordItemBuilder
                .atomicBatch(List.of(call.getTransaction(), ethereum.getTransaction()))
                .record(r -> r.setConsensusTimestamp(batchTimestamp))
                .status(ResponseCodeEnum.INNER_TRANSACTION_FAILED)
                .customize(this::finalizeRecordItem)
                .build();
        final var batchBuilder = blockTransactionBuilder.atomicBatch(batch);
        if (includeUncommittedChanges) {
            // Deliberately synthetic input: failed batches must not persist even a nonempty CLPR context.
            final var runningHash = recordItemBuilder.bytes(32);
            final var updatedChannel = channel.value().toBuilder()
                    .setNextMessageId(2)
                    .setSentRunningHash(runningHash)
                    .build();
            batchBuilder.stateChanges(changes -> changes.add(clprSendMessageChanges(
                    channel,
                    updatedChannel,
                    List.of(messageValue(channel.connector().getConnectorId(), "reverted", runningHash)))));
        }
        final var batchBlock = batchBuilder.build();
        assertThat(batchBlock.getStateChangeContext().hasClprState()).isEqualTo(includeUncommittedChanges);
        final var callBlock = blockTransactionBuilder
                .contractCall(call, false)
                .previous(batchBlock)
                .build();
        final var ethereumBlock =
                blockTransactionBuilder.ethereum(ethereum).previous(callBlock).build();
        assertThat(callBlock.getParent()).isSameAs(batchBlock);
        assertThat(ethereumBlock.getParent()).isSameAs(batchBlock);
        final var recordFiles = transformClprFiles(channel, List.of(batchBlock, callBlock, ethereumBlock));
        assertThat(recordFiles.getLast().getItems()).hasSize(3).allSatisfy(item -> {
            assertThat(item.isSuccessful()).isFalse();
            assertThat(item.getClprContext()).isNull();
        });

        parseFiles(recordFiles, commitIndex);

        assertThat(clprMessageRepository.count()).isZero();
        assertThat(clprChannelRepository.count()).isEqualTo(1);
        assertThat(clprChannelRepository
                        .findById(DomainUtils.toBytes(channel.channelId()))
                        .orElseThrow())
                .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                .returns(1L, ClprChannel::getNextMessageId)
                .returns(0L, ClprChannel::getAckedMessageId)
                .returns(DomainUtils.toBytes(channel.value().getSentRunningHash()), ClprChannel::getSentRunningHash)
                .returns(Range.atLeast(channel.createdTimestamp()), ClprChannel::getTimestampRange);
        assertThat(clprConnectorRepository.count()).isEqualTo(1);
        assertThat(clprConnectorRepository
                        .findById(connectorId(channel.connector()))
                        .orElseThrow())
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(0L, ClprConnector::getInFlightMessageCount)
                .returns(channel.connector().getLockedStake(), ClprConnector::getLockedStake)
                .returns(Range.atLeast(channel.consensusTimestamp()), ClprConnector::getTimestampRange);
        assertThat(findHistory(ClprChannel.class)).isEmpty();
        assertThat(findHistory(ClprConnector.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, false, 0
            false, false, 1
            false, false, 2
            false, false, 3
            true, false, 0
            true, false, 1
            true, false, 2
            true, false, 3
            true, false, 4
            true, false, 5
            false, true, 0
            false, true, 1
            false, true, 2
            false, true, 3
            true, true, 0
            true, true, 1
            true, true, 2
            true, true, 3
            true, true, 4
            true, true, 5
            """)
    void clprChannelDeletionLifecycle(boolean outstandingMessages, boolean stateDiffingEnabled, int commitIndex) {
        assertChannelDeletionLifecycle(outstandingMessages, stateDiffingEnabled, false, commitIndex);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clprChannelAutomaticallyCloses(boolean stateDiffingEnabled) {
        assertChannelDeletionLifecycle(true, stateDiffingEnabled, true, 0);
    }

    private void assertChannelDeletionLifecycle(
            boolean outstandingMessages, boolean stateDiffingEnabled, boolean automaticClosure, int commitIndex) {
        final var channel = outstandingMessages
                ? createClprMessageChannelWithConnector()
                : createClprMessageChannel("hiero:testing");
        final var files = new ArrayList<>(channel.setupFiles());
        final var activeValue = channel.value().toBuilder();
        final long sendTimestamp;
        final ClprMessageValue message;
        if (outstandingMessages) {
            final var runningHash = recordItemBuilder.bytes(32);
            final var afterSend = activeValue
                    .setNextMessageId(2)
                    .setSentRunningHash(runningHash)
                    .build();
            final var call = recordItemBuilder
                    .contractCall()
                    .customize(this::finalizeRecordItem)
                    .build();
            message = messageValue(channel.connector().getConnectorId(), "outstanding", runningHash);
            files.add(List.of(blockTransactionBuilder
                    .contractCall(call, false)
                    .stateChanges(changes -> changes.add(
                            clprSendMessageChanges(channel, afterSend, List.of(message), stateDiffingEnabled)))
                    .build()));
            sendTimestamp = call.getConsensusTimestamp();
        } else {
            message = null;
            sendTimestamp = channel.createdTimestamp();
        }
        final var firstStatus = outstandingMessages ? CLOSING : DRAINED;
        final var firstValue = activeValue.setStatus(firstStatus).build();
        final var firstDelete = deleteChannel(firstValue, stateDiffingEnabled);
        files.add(List.of(blockTransactionBuilder.clprDeleteChannel(firstDelete).build()));

        // Simulate a terminal reply to the outstanding DATA message. If the peer is already drained,
        // consensus can close the channel in the same bundle without committing a DRAINED value.
        final var receivedRunningHash =
                outstandingMessages ? recordItemBuilder.bytes(32) : firstValue.getReceivedRunningHash();
        final var afterDrain = firstValue.toBuilder()
                .setStatus(automaticClosure ? CLOSED : DRAINED)
                .setAckedMessageId(firstValue.getNextMessageId() - 1)
                .setReceivedMessageId(outstandingMessages ? 1L : 0L)
                .setReceivedRunningHash(receivedRunningHash)
                .build();
        final long drainTimestamp;
        if (outstandingMessages) {
            final var channelUpdate = stateDiffingEnabled
                    ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                            .setStatus(afterDrain.getStatus())
                            .setAckedMessageId(afterDrain.getAckedMessageId())
                            .setReceivedMessageId(afterDrain.getReceivedMessageId())
                            .setReceivedRunningHash(receivedRunningHash)
                            .build()
                    : afterDrain;
            final var key = connectorKey(channel.connector());
            final var connectorUpdate = stateDiffingEnabled
                    ? clprConnectorMapUpdate(key, com.hederahashgraph.api.proto.java.ClprConnector.getDefaultInstance())
                            .toBuilder()
                            .setPartial(true)
                            .addClearedFields(
                                    com.hederahashgraph.api.proto.java.ClprConnector
                                            .IN_FLIGHT_MESSAGE_COUNT_FIELD_NUMBER)
                            .build()
                    : clprConnectorMapUpdate(key, channel.connector());
            final var bundle = recordItemBuilder
                    .clprSubmitBundle()
                    .transactionBody(b -> b.setChannelId(channel.channelId()))
                    .recordItem(r -> r.clprContextBuilder()
                            .clprChannels(List.of(new ClprChannelEntry(
                                    channel.channelId(),
                                    channelMapUpdate(channel.channelId(), channelUpdate, stateDiffingEnabled))))
                            .clprMessages(List.of())
                            .clprConnectors(List.of(new ClprConnectorEntry(key, connectorUpdate)))
                            .clprConnectorsRemoved(List.of()))
                    .customize(this::finalizeRecordItem)
                    .build();
            final var queueRemoval = StateChange.newBuilder()
                    .setStateId(StateIdentifier.STATE_ID_CLPR_MESSAGE_QUEUE_VALUE)
                    .setMapDelete(MapDeleteChange.newBuilder()
                            .setKey(MapChangeKey.newBuilder().setClprMessageKey(messageKey(channel.channelId(), 1L))))
                    .build();
            files.add(List.of(blockTransactionBuilder
                    .clprSubmitBundle(bundle)
                    .stateChanges(changes -> changes.set(
                            0,
                            changes.getFirst().toBuilder()
                                    .addStateChanges(queueRemoval)
                                    .build()))
                    .build()));
            drainTimestamp = bundle.getConsensusTimestamp();
        } else {
            drainTimestamp = firstDelete.getConsensusTimestamp();
        }
        final long closeTimestamp;
        if (automaticClosure) {
            closeTimestamp = drainTimestamp;
        } else {
            final var close =
                    deleteChannel(afterDrain.toBuilder().setStatus(CLOSED).build(), stateDiffingEnabled);
            files.add(List.of(blockTransactionBuilder.clprDeleteChannel(close).build()));
            closeTimestamp = close.getConsensusTimestamp();
        }

        parseFiles(transformFiles(files), commitIndex);

        assertThat(clprChannelRepository
                        .findById(DomainUtils.toBytes(channel.channelId()))
                        .orElseThrow())
                .returns(CLOSED.getNumber(), ClprChannel::getStatus)
                .returns(channel.createdTimestamp(), ClprChannel::getCreatedTimestamp)
                .returns("hiero:testing", ClprChannel::getChainId)
                .returns(
                        DomainUtils.toBytes(channel.value().getVerifierFingerprint()),
                        ClprChannel::getVerifierFingerprint)
                .returns(afterDrain.getAckedMessageId(), ClprChannel::getAckedMessageId)
                .returns(afterDrain.getNextMessageId(), ClprChannel::getNextMessageId)
                .returns(afterDrain.getReceivedMessageId(), ClprChannel::getReceivedMessageId)
                .returns(DomainUtils.toBytes(receivedRunningHash), ClprChannel::getReceivedRunningHash)
                .returns(DomainUtils.toBytes(channel.value().getSalt()), ClprChannel::getSalt)
                .returns(DomainUtils.toBytes(channel.value().getChannelContext()), ClprChannel::getChannelContext)
                .returns(Range.atLeast(closeTimestamp), ClprChannel::getTimestampRange);
        final var history = new ArrayList<>(findHistory(ClprChannel.class));
        final var historizeSend = outstandingMessages && !stateDiffingEnabled;
        final var expectedHistoryCount =
                2 + (outstandingMessages ? 1 : 0) + (historizeSend ? 1 : 0) - (automaticClosure ? 1 : 0);
        assertThat(history).hasSize(expectedHistoryCount);
        assertThat(history.getFirst())
                .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                .returns(0L, ClprChannel::getAckedMessageId)
                .returns(outstandingMessages && stateDiffingEnabled ? 2L : 1L, ClprChannel::getNextMessageId)
                .returns(
                        Range.closedOpen(
                                channel.createdTimestamp(),
                                historizeSend ? sendTimestamp : firstDelete.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);
        if (historizeSend) {
            assertThat(history.get(1))
                    .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                    .returns(2L, ClprChannel::getNextMessageId)
                    .returns(
                            Range.closedOpen(sendTimestamp, firstDelete.getConsensusTimestamp()),
                            ClprChannel::getTimestampRange);
        }
        if (outstandingMessages) {
            assertThat(history.get(historizeSend ? 2 : 1))
                    .returns(CLOSING.getNumber(), ClprChannel::getStatus)
                    .returns(0L, ClprChannel::getAckedMessageId)
                    .returns(2L, ClprChannel::getNextMessageId)
                    .returns(0L, ClprChannel::getReceivedMessageId)
                    .returns(
                            DomainUtils.toBytes(firstValue.getReceivedRunningHash()),
                            ClprChannel::getReceivedRunningHash)
                    .returns(
                            Range.closedOpen(firstDelete.getConsensusTimestamp(), drainTimestamp),
                            ClprChannel::getTimestampRange);
            assertThat(clprConnectorRepository
                            .findById(connectorId(channel.connector()))
                            .orElseThrow())
                    .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                    .returns(channel.connector().getLockedStake(), ClprConnector::getLockedStake)
                    .returns(0L, ClprConnector::getInFlightMessageCount)
                    .returns(
                            Range.atLeast(stateDiffingEnabled ? channel.consensusTimestamp() : drainTimestamp),
                            ClprConnector::getTimestampRange);
            final var connectorHistory = findHistory(ClprConnector.class);
            if (stateDiffingEnabled) {
                assertThat(connectorHistory).isEmpty();
            } else {
                assertThat(connectorHistory)
                        .satisfiesExactly(
                                value -> assertThat(value)
                                        .returns(0L, ClprConnector::getInFlightMessageCount)
                                        .returns(
                                                Range.closedOpen(channel.consensusTimestamp(), sendTimestamp),
                                                ClprConnector::getTimestampRange),
                                value -> assertThat(value)
                                        .returns(1L, ClprConnector::getInFlightMessageCount)
                                        .returns(
                                                Range.closedOpen(sendTimestamp, drainTimestamp),
                                                ClprConnector::getTimestampRange));
            }
        }
        if (automaticClosure) {
            assertThat(history).extracting(ClprChannel::getStatus).doesNotContain(DRAINED.getNumber());
        } else {
            assertThat(history.getLast())
                    .returns(DRAINED.getNumber(), ClprChannel::getStatus)
                    .returns(afterDrain.getAckedMessageId(), ClprChannel::getAckedMessageId)
                    .returns(afterDrain.getReceivedMessageId(), ClprChannel::getReceivedMessageId)
                    .returns(DomainUtils.toBytes(receivedRunningHash), ClprChannel::getReceivedRunningHash)
                    .returns(Range.closedOpen(drainTimestamp, closeTimestamp), ClprChannel::getTimestampRange);
        }
        if (message != null) {
            // Removing the acknowledged message from consensus's operational queue must not delete MN history.
            final var payload = message.getPayload().getMessage();
            assertThat(allMessages())
                    .singleElement()
                    .returns(sendTimestamp, ClprMessage::getConsensusTimestamp)
                    .returns(DomainUtils.toBytes(channel.channelId()), ClprMessage::getChannelId)
                    .returns(1L, ClprMessage::getMessageId)
                    .returns(MESSAGE_FIELD_NUMBER, ClprMessage::getMessageType)
                    .returns(DomainUtils.toBytes(payload.getConnectorId()), ClprMessage::getConnectorId)
                    .returns(DomainUtils.toBytes(payload.getSender()), ClprMessage::getSender)
                    .returns(DomainUtils.toBytes(payload.getTargetApplication()), ClprMessage::getTargetApplication)
                    .returns(DomainUtils.toBytes(payload.getMessageData()), ClprMessage::getMessageData)
                    .returns(DomainUtils.toBytes(message.getRunningHashAfterProcessing()), ClprMessage::getRunningHash);
        } else {
            assertThat(allMessages()).isEmpty();
        }
        assertThat(history)
                .allSatisfy(value -> assertThat(value)
                        .returns(channel.createdTimestamp(), ClprChannel::getCreatedTimestamp)
                        .returns("hiero:testing", ClprChannel::getChainId)
                        .returns(
                                DomainUtils.toBytes(channel.value().getVerifierFingerprint()),
                                ClprChannel::getVerifierFingerprint));
    }

    @ParameterizedTest
    @CsvSource({
        "false,0", "false,1", "false,2", "false,3", "false,4", "false,5", "true,0", "true,1", "true,2", "true,3",
        "true,4", "true,5"
    })
    void clprConnectorIdReuseAcrossChannels(boolean ban, int commitIndex) {
        final var firstChannel = createClprMessageChannel("hiero:first");
        final var secondChannel = createClprMessageChannel("hiero:second");
        // Synthetic connector ID, shared identity key and salt; the importer does not derive or validate the ID.
        final var sharedId = recordItemBuilder.bytes(32);
        final var firstConnector = connectorValue(firstChannel.channelId(), sharedId, 100_000_000L);
        final var secondConnector = connectorValue(secondChannel.channelId(), sharedId, 200_000_000L);
        final var identityKey = recordItemBuilder.key();
        final var salt = recordItemBuilder.bytes(32);
        final var firstCreate = createConnector(firstConnector, identityKey, salt);
        final var secondCreate = createConnector(secondConnector, identityKey, salt);
        final var files = new ArrayList<>(firstChannel.setupFiles());
        files.addAll(secondChannel.setupFiles());
        files.add(
                List.of(blockTransactionBuilder.clprCreateConnector(firstCreate).build()));
        files.add(List.of(
                blockTransactionBuilder.clprCreateConnector(secondCreate).build()));

        final long removalTimestamp;
        if (ban) {
            final var bundle = recordItemBuilder
                    .clprSubmitBundle()
                    .transactionBody(b -> b.setChannelId(firstChannel.channelId()))
                    .recordItem(r -> r.clprContextBuilder()
                            .clprChannels(List.of(new ClprChannelEntry(
                                    firstChannel.channelId(),
                                    clprChannelMapUpdate(firstChannel.channelId(), firstChannel.value()))))
                            .clprMessages(List.of())
                            .clprConnectors(List.of())
                            .clprConnectorsRemoved(List.of(connectorKey(firstConnector))))
                    .customize(this::finalizeRecordItem)
                    .build();
            files.add(List.of(blockTransactionBuilder.clprSubmitBundle(bundle).build()));
            removalTimestamp = bundle.getConsensusTimestamp();
        } else {
            final var delete = recordItemBuilder
                    .clprDeleteConnector()
                    .transactionBody(
                            b -> b.setChannelId(firstChannel.channelId()).setConnectorId(sharedId))
                    .recordItem(
                            r -> r.clprContextBuilder().clprConnectorsRemoved(List.of(connectorKey(firstConnector))))
                    .customize(this::finalizeRecordItem)
                    .build();
            files.add(
                    List.of(blockTransactionBuilder.clprDeleteConnector(delete).build()));
            removalTimestamp = delete.getConsensusTimestamp();
        }

        parseFiles(transformFiles(files), commitIndex);

        assertThat(clprConnectorRepository.count()).isEqualTo(2);
        assertThat(clprConnectorRepository.findById(connectorId(firstConnector)).orElseThrow())
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(firstCreate.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(Range.atLeast(removalTimestamp), ClprConnector::getTimestampRange);
        assertThat(clprConnectorRepository
                        .findById(connectorId(secondConnector))
                        .orElseThrow())
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(secondCreate.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(secondConnector.getLockedStake(), ClprConnector::getLockedStake)
                .returns(secondConnector.getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(0L, ClprConnector::getSlashCount)
                .returns(0L, ClprConnector::getInFlightMessageCount)
                .returns(null, ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(secondCreate.getConsensusTimestamp()), ClprConnector::getTimestampRange);
        assertThat(findHistory(ClprConnector.class))
                .singleElement()
                .returns(DomainUtils.toBytes(firstChannel.channelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(sharedId), ClprConnector::getConnectorId)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(firstConnector.getLockedStake(), ClprConnector::getLockedStake)
                .returns(
                        Range.closedOpen(firstCreate.getConsensusTimestamp(), removalTimestamp),
                        ClprConnector::getTimestampRange);
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,2", "false,4", "true,0", "true,2", "true,4"})
    void clprChannelManifestRecovery(boolean stateDiffingEnabled, int commitIndex) {
        // given -- a channel created without a proof timestamp, a normal manifest update, then recovery.
        final var channelId = recordItemBuilder.bytes(32);
        final var channel = activeChannelValue(channelId, "hiero:testing");
        final var endpoint = com.hederahashgraph.api.proto.java.ClprEndpoint.newBuilder()
                .setAccountId(recordItemBuilder.bytes(20))
                .setServiceEndpoint(com.hederahashgraph.api.proto.java.ClprServiceEndpoint.newBuilder()
                        .setIpAddress("10.0.0.1")
                        .setPort(50211))
                .setTlsCertificate(recordItemBuilder.bytes(64))
                .build();
        final var originalManifest = com.hederahashgraph.api.proto.java.ClprEndpointManifest.newBuilder()
                .setServiceAddress(channel.getChannelContext().substring(32))
                .setVersion(1L)
                .addEndpoints(endpoint)
                .build();
        final var original = channel.toBuilder()
                .setEndpointManifest(originalManifest)
                .setEndpointManifestVersion(originalManifest.getVersion())
                .build();
        final var create = createChannel(original);

        final var proofTimestamp =
                Timestamp.newBuilder().setSeconds(100L).setNanos(123).build();
        final var updatedManifest = originalManifest.toBuilder().setVersion(5L).build();
        final var updated = original.toBuilder()
                .setEndpointManifest(updatedManifest)
                .setEndpointManifestVersion(updatedManifest.getVersion())
                .setEndpointManifestProofTimestamp(proofTimestamp)
                .build();
        final var update = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        .setEndpointManifest(updatedManifest)
                        .setEndpointManifestVersion(updatedManifest.getVersion())
                        .setEndpointManifestProofTimestamp(proofTimestamp)
                        .build()
                : updated;
        final var refresh = submitChannelUpdate(channelId, update, stateDiffingEnabled, null);

        final var recoveredEndpoint = endpoint.toBuilder()
                .setServiceEndpoint(endpoint.getServiceEndpoint().toBuilder().setIpAddress("10.0.0.5"))
                .build();
        final var recoveredManifest = updatedManifest.toBuilder()
                .setVersion(3L)
                .setEndpoints(0, recoveredEndpoint)
                .build();
        final var recoveredProofTimestamp =
                Timestamp.newBuilder().setSeconds(200L).setNanos(456).build();
        final var recovered = updated.toBuilder()
                .setEndpointManifest(recoveredManifest)
                .setEndpointManifestVersion(recoveredManifest.getVersion())
                .setEndpointManifestProofTimestamp(recoveredProofTimestamp)
                .setPeerManifestRecovered(true)
                .build();
        final var recoveryUpdate = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        .setEndpointManifest(recoveredManifest)
                        .setEndpointManifestVersion(recoveredManifest.getVersion())
                        .setEndpointManifestProofTimestamp(recoveredProofTimestamp)
                        .setPeerManifestRecovered(true)
                        .build()
                : recovered;
        final var recover =
                submitChannelUpdate(channelId, recoveryUpdate, stateDiffingEnabled, updatedManifest.getVersion());

        // An unrelated update must preserve the recovered manifest and proof metadata.
        final var trustAnchor = recordItemBuilder.bytes(32);
        final var trustAnchorId = recordItemBuilder.bytes(32);
        final var unrelatedUpdate = (stateDiffingEnabled
                        ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        : recovered.toBuilder())
                .setTrustAnchor(trustAnchor)
                .setTrustAnchorId(trustAnchorId)
                .build();
        final var unrelated = submitChannelUpdate(channelId, unrelatedUpdate, stateDiffingEnabled, null);

        // when
        parseFiles(
                transformFiles(List.of(
                        List.of(blockTransactionBuilder
                                .clprCreateChannel(create)
                                .build()),
                        List.of(blockTransactionBuilder
                                .clprSubmitBundle(refresh)
                                .build()),
                        List.of(blockTransactionBuilder
                                .clprSubmitBundle(recover)
                                .build()),
                        List.of(blockTransactionBuilder
                                .clprSubmitBundle(unrelated)
                                .build()))),
                commitIndex);

        // then -- the peer proof timestamps are stored as supplied, not replaced by local consensus timestamps.
        final var expectedEndpoint = ClprEndpoint.builder()
                .accountId(DomainUtils.toBytes(endpoint.getAccountId()))
                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.1", 50211))
                .tlsCertificate(DomainUtils.toBytes(endpoint.getTlsCertificate()))
                .build();
        final var expectedRecoveredEndpoint = ClprEndpoint.builder()
                .accountId(DomainUtils.toBytes(recoveredEndpoint.getAccountId()))
                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.5", 50211))
                .tlsCertificate(DomainUtils.toBytes(recoveredEndpoint.getTlsCertificate()))
                .build();
        final var proofNanos = DomainUtils.timestampInNanosMax(proofTimestamp);
        final var recoveredProofNanos = DomainUtils.timestampInNanosMax(recoveredProofTimestamp);
        assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(3L, ClprChannel::getEndpointManifestVersion)
                .returns(List.of(expectedRecoveredEndpoint), ClprChannel::getEndpointManifest)
                .returns(recoveredProofNanos, ClprChannel::getEndpointManifestProofTimestamp)
                .returns(true, ClprChannel::getPeerManifestRecovered)
                .returns(DomainUtils.toBytes(trustAnchor), ClprChannel::getTrustAnchor)
                .returns(DomainUtils.toBytes(trustAnchorId), ClprChannel::getTrustAnchorId)
                .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                .returns(1L, ClprChannel::getNextMessageId)
                .returns(0L, ClprChannel::getAckedMessageId)
                .returns(0L, ClprChannel::getReceivedMessageId)
                .returns(DomainUtils.toBytes(original.getSentRunningHash()), ClprChannel::getSentRunningHash)
                .returns(DomainUtils.toBytes(original.getReceivedRunningHash()), ClprChannel::getReceivedRunningHash)
                .returns(create.getConsensusTimestamp(), ClprChannel::getCreatedTimestamp)
                .returns(Range.atLeast(unrelated.getConsensusTimestamp()), ClprChannel::getTimestampRange);
        final var history = new ArrayList<>(findHistory(ClprChannel.class));
        assertThat(history).hasSize(3);
        assertThat(history.getFirst())
                .returns(1L, ClprChannel::getEndpointManifestVersion)
                .returns(List.of(expectedEndpoint), ClprChannel::getEndpointManifest)
                .returns(null, ClprChannel::getEndpointManifestProofTimestamp)
                .returns(false, ClprChannel::getPeerManifestRecovered)
                .returns(
                        Range.closedOpen(create.getConsensusTimestamp(), refresh.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);
        assertThat(history.get(1))
                .returns(5L, ClprChannel::getEndpointManifestVersion)
                .returns(List.of(expectedEndpoint), ClprChannel::getEndpointManifest)
                .returns(proofNanos, ClprChannel::getEndpointManifestProofTimestamp)
                .returns(false, ClprChannel::getPeerManifestRecovered)
                .returns(
                        Range.closedOpen(refresh.getConsensusTimestamp(), recover.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);
        assertThat(history.get(2))
                .returns(3L, ClprChannel::getEndpointManifestVersion)
                .returns(List.of(expectedRecoveredEndpoint), ClprChannel::getEndpointManifest)
                .returns(recoveredProofNanos, ClprChannel::getEndpointManifestProofTimestamp)
                .returns(true, ClprChannel::getPeerManifestRecovered)
                .returns(
                        Range.closedOpen(recover.getConsensusTimestamp(), unrelated.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);
        assertThat(allMessages()).isEmpty();
    }

    private RecordItem submitChannelUpdate(
            ByteString channelId,
            com.hederahashgraph.api.proto.java.ClprChannel update,
            boolean partial,
            Long expectedManifestVersion) {
        return recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> {
                    b.setChannelId(channelId);
                    if (expectedManifestVersion != null) {
                        b.setExpectedCachedManifestVersion(UInt64Value.of(expectedManifestVersion));
                    }
                })
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(
                                List.of(new ClprChannelEntry(channelId, channelMapUpdate(channelId, update, partial)))))
                .recordItem(r -> r.clprContextBuilder()
                        .clprMessages(List.of())
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of()))
                .customize(this::finalizeRecordItem)
                .build();
    }

    private RecordItem deleteChannel(
            com.hederahashgraph.api.proto.java.ClprChannel value, boolean stateDiffingEnabled) {
        final var channelId = value.getChannelId();
        final var update = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        .setStatus(value.getStatus())
                        .build()
                : value;
        return recordItemBuilder
                .clprDeleteChannel()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                channelId, channelMapUpdate(channelId, update, stateDiffingEnabled)))))
                .customize(this::finalizeRecordItem)
                .build();
    }

    private com.hederahashgraph.api.proto.java.ClprChannel activeChannelValue(ByteString channelId, String chainId) {
        return com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setChainId(chainId)
                .setChannelContext(channelId.concat(recordItemBuilder.bytes(20)))
                .setSalt(recordItemBuilder.bytes(32))
                .setStatus(ACTIVE)
                .setNextMessageId(1L)
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
            ClprMessageChannel channel, long expectedTimestamp, List<ClprMessageValue> expectedMessages) {
        final var messages = allMessages();
        assertThat(messages).hasSize(expectedMessages.size());
        for (var i = 0; i < expectedMessages.size(); i++) {
            final var messageId = i + 1L;
            final var expectedValue = expectedMessages.get(i);
            final var expectedMessage = expectedValue.getPayload().getMessage();
            final var message = messages.stream()
                    .filter(item -> item.getMessageId() == messageId)
                    .findFirst()
                    .orElseThrow();
            assertThat(message)
                    .returns(MESSAGE_FIELD_NUMBER, ClprMessage::getMessageType)
                    .returns(expectedTimestamp, ClprMessage::getConsensusTimestamp)
                    .returns(DomainUtils.toBytes(channel.channelId()), ClprMessage::getChannelId)
                    .returns(DomainUtils.toBytes(expectedMessage.getConnectorId()), ClprMessage::getConnectorId)
                    .returns(DomainUtils.toBytes(expectedMessage.getSender()), ClprMessage::getSender)
                    .returns(
                            DomainUtils.toBytes(expectedMessage.getTargetApplication()),
                            ClprMessage::getTargetApplication)
                    .returns(
                            DomainUtils.toBytes(expectedValue.getRunningHashAfterProcessing()),
                            ClprMessage::getRunningHash)
                    .returns(DomainUtils.toBytes(expectedMessage.getMessageData()), ClprMessage::getMessageData);
        }
        final var persisted = clprChannelRepository
                .findById(DomainUtils.toBytes(channel.channelId()))
                .orElseThrow();
        assertThat(persisted)
                .returns(ACTIVE.getNumber(), ClprChannel::getStatus)
                .returns(expectedMessages.size() + 1L, ClprChannel::getNextMessageId)
                .returns(
                        DomainUtils.toBytes(expectedMessages.getLast().getRunningHashAfterProcessing()),
                        ClprChannel::getSentRunningHash);
        final var connector = clprConnectorRepository
                .findById(connectorId(channel.connector()))
                .orElseThrow();
        assertThat(connector)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns((long) expectedMessages.size(), ClprConnector::getInFlightMessageCount)
                .returns(channel.connector().getLockedStake(), ClprConnector::getLockedStake)
                .returns(channel.connector().getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(Range.atLeast(expectedTimestamp), ClprConnector::getTimestampRange);
        assertThat(findHistory(ClprConnector.class))
                .singleElement()
                .returns(0L, ClprConnector::getInFlightMessageCount)
                .returns(
                        Range.closedOpen(channel.consensusTimestamp(), expectedTimestamp),
                        ClprConnector::getTimestampRange);
    }

    private MapUpdateChange channelMapUpdate(
            ByteString channelId, com.hederahashgraph.api.proto.java.ClprChannel value, boolean partial) {
        return clprChannelMapUpdate(channelId, value).toBuilder()
                .setPartial(partial)
                .build();
    }

    private MapUpdateChange sendMessageChannelUpdate(
            com.hederahashgraph.api.proto.java.ClprChannel value, boolean stateDiffingEnabled) {
        final var update = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                        .setNextMessageId(value.getNextMessageId())
                        .setSentRunningHash(value.getSentRunningHash())
                        .build()
                : value;
        return channelMapUpdate(value.getChannelId(), update, stateDiffingEnabled);
    }

    private StateChange channelStateChange(MapUpdateChange update) {
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_CLPR_CHANNELS_VALUE)
                .setMapUpdate(update)
                .build();
    }

    private StateChanges clprSendMessageChanges(
            ClprMessageChannel channel,
            com.hederahashgraph.api.proto.java.ClprChannel updatedChannel,
            List<ClprMessageValue> messages) {
        return clprSendMessageChanges(channel, updatedChannel, messages, false);
    }

    private StateChanges clprSendMessageChanges(
            ClprMessageChannel channel,
            com.hederahashgraph.api.proto.java.ClprChannel updatedChannel,
            List<ClprMessageValue> messages,
            boolean stateDiffingEnabled) {
        final var updatedConnector = channel.connector().toBuilder()
                .setInFlightMessageCount(messages.size())
                .build();
        final var changes = StateChanges.newBuilder()
                .addStateChanges(channelStateChange(sendMessageChannelUpdate(updatedChannel, stateDiffingEnabled)))
                .addStateChanges(connectorStateChange(sendMessageConnectorUpdate(
                        connectorKey(updatedConnector), updatedConnector, stateDiffingEnabled)));
        for (var i = 0; i < messages.size(); i++) {
            changes.addStateChanges(messageStateChange(channel.channelId(), i + 1L, messages.get(i)));
        }
        return changes.build();
    }

    private MapUpdateChange sendMessageConnectorUpdate(
            ClprConnectorKey key, com.hederahashgraph.api.proto.java.ClprConnector value, boolean stateDiffingEnabled) {
        final var update = stateDiffingEnabled
                ? com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                        .setInFlightMessageCount(value.getInFlightMessageCount())
                        .build()
                : value;
        return clprConnectorMapUpdate(key, update).toBuilder()
                .setPartial(stateDiffingEnabled)
                .build();
    }

    private StateChange connectorStateChange(MapUpdateChange update) {
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_CLPR_CONNECTORS_VALUE)
                .setMapUpdate(update)
                .build();
    }

    private ClprMessageChannel createClprMessageChannel(String chainId) {
        final var channelId = recordItemBuilder.bytes(32);
        final var value = activeChannelValue(channelId, chainId);
        final var createChannel = createChannel(value);
        final var setupFiles = List.of(
                List.of(blockTransactionBuilder.clprCreateChannel(createChannel).build()));
        return new ClprMessageChannel(
                channelId,
                value,
                null,
                setupFiles,
                createChannel.getConsensusTimestamp(),
                createChannel.getConsensusTimestamp());
    }

    private RecordItem createChannel(com.hederahashgraph.api.proto.java.ClprChannel value) {
        final var channelId = value.getChannelId();
        return recordItemBuilder
                .clprCreateChannel()
                .transactionBody(body -> body.setChannelId(channelId)
                        .setSalt(value.getSalt())
                        .setVerifierContract(value.getVerifierContract()))
                .recordItem(item -> item.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(channelId, clprChannelMapUpdate(channelId, value)))))
                .customize(this::finalizeRecordItem)
                .build();
    }

    private ClprMessageChannel createClprMessageChannelWithConnector() {
        final var channel = createClprMessageChannel("hiero:testing");
        final var connector = connectorValue(channel.channelId(), recordItemBuilder.bytes(32), 100_000_000L);
        final var createConnector = createConnector(connector);
        final var setupTransactions = new ArrayList<>(channel.setupFiles().getFirst());
        setupTransactions.add(
                blockTransactionBuilder.clprCreateConnector(createConnector).build());
        return new ClprMessageChannel(
                channel.channelId(),
                channel.value(),
                connector,
                List.of(setupTransactions),
                channel.createdTimestamp(),
                createConnector.getConsensusTimestamp());
    }

    private com.hederahashgraph.api.proto.java.ClprConnector connectorValue(
            ByteString channelId, ByteString connectorId, long stake) {
        return com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setAdminKey(recordItemBuilder.key())
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .setConnectorContract(recordItemBuilder.contractId())
                .setLockedStake(stake)
                .build();
    }

    private RecordItem createConnector(com.hederahashgraph.api.proto.java.ClprConnector connector) {
        return createConnector(connector, recordItemBuilder.key(), recordItemBuilder.bytes(32));
    }

    private RecordItem createConnector(
            com.hederahashgraph.api.proto.java.ClprConnector connector, Key identityKey, ByteString salt) {
        return recordItemBuilder
                .clprCreateConnector()
                .transactionBody(b -> b.setAdminKey(connector.getAdminKey())
                        .setChannelId(connector.getChannelId())
                        .setConnectorId(connector.getConnectorId())
                        .setConnectorContract(connector.getConnectorContract())
                        .setIdentityKey(identityKey)
                        .setLockedStake(connector.getLockedStake())
                        .setSalt(salt))
                .recordItem(r -> r.clprContextBuilder()
                        .clprConnectors(List.of(
                                new ClprConnectorEntry(connectorKey(connector), clprConnectorMapUpdate(connector)))))
                .customize(this::finalizeRecordItem)
                .build();
    }

    private ClprConnectorKey connectorKey(com.hederahashgraph.api.proto.java.ClprConnector connector) {
        return ClprConnectorKey.newBuilder()
                .setChannelId(connector.getChannelId())
                .setConnectorId(connector.getConnectorId())
                .build();
    }

    private AbstractClprConnector.Id connectorId(com.hederahashgraph.api.proto.java.ClprConnector connector) {
        return new AbstractClprConnector.Id(
                DomainUtils.toBytes(connector.getChannelId()), DomainUtils.toBytes(connector.getConnectorId()));
    }

    private ClprLedgerConfiguration expectedLedgerConfiguration(
            long consensusTimestamp, com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        final var throttles = configuration.getThrottles();
        return ClprLedgerConfiguration.builder()
                .chainId(configuration.getChainId())
                .consensusTimestamp(consensusTimestamp)
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()))
                .protocolVersion(Integer.toUnsignedLong(configuration.getProtocolVersion()))
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .throttle(ClprThrottle.builder()
                        .maxGasPerMessage(throttles.getMaxGasPerMessage())
                        .maxLocalEndpoints(Integer.toUnsignedLong(throttles.getMaxLocalEndpoints()))
                        .maxMessagePayloadBytes(Integer.toUnsignedLong(throttles.getMaxMessagePayloadBytes()))
                        .maxMessagesPerBundle(Integer.toUnsignedLong(throttles.getMaxMessagesPerBundle()))
                        .maxPeerEndpoints(Integer.toUnsignedLong(throttles.getMaxPeerEndpoints()))
                        .maxQueueDepth(Integer.toUnsignedLong(throttles.getMaxQueueDepth()))
                        .maxSyncBytes(throttles.getMaxSyncBytes())
                        .build())
                .build();
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

    private ClprMessageValue messageValue(ByteString connectorId, String data, ByteString runningHash) {
        return ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(connectorId)
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
        assertThat(root.getClprContext().getClprConnectors()).hasSize(1);
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
            com.hederahashgraph.api.proto.java.ClprConnector connector,
            List<List<BlockTransaction>> setupFiles,
            long createdTimestamp,
            long consensusTimestamp) {}
}
