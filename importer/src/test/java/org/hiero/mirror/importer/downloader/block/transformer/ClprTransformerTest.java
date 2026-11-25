// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.ClprChannel;
import com.hederahashgraph.api.proto.java.ClprChannelStatus;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.junit.jupiter.api.Test;

final class ClprTransformerTest extends AbstractTransformerTest {

    @Test
    void clprCreateChannel() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.clprCreateChannel().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.clprCreateChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCreateChannelUnsuccessful() {
        // given -- the isSuccessful() guard fires before clprContextBuilder() is ever touched, so the real
        // RecordItem.clprContext stays null rather than an empty-but-present ClprContext
        final var recordItem = recordItemBuilder
                .clprCreateChannel()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprCreateChannelMissingStateChange() {
        // given -- no clpr_channel_value state change for this transaction; the transformer should leave
        // RecordItem.clprContext.clprChannels empty
        final var expectedRecordItem = recordItemBuilder
                .clprCreateChannel()
                .recordItem(r -> r.clprContextBuilder().clprChannels(List.of()))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprCreateChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCreateConnectorUnsuccessful() {
        // given -- see clprCreateChannelUnsuccessful for why clprContext stays null, not empty
        final var recordItem = recordItemBuilder
                .clprCreateConnector()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprCreateConnectorMissingStateChange() {
        // given -- no clpr_connector_value state change for this transaction; the transformer should leave
        // RecordItem.clprContext.clprConnectors empty
        final var expectedRecordItem = recordItemBuilder
                .clprCreateConnector()
                .recordItem(r -> r.clprContextBuilder().clprConnectors(List.of()))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprCreateConnector(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprMaintenance() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.clprMaintenance().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.clprMaintenance(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprMaintenanceUnsuccessful() {
        // given -- see clprCreateChannelUnsuccessful for why clprContext stays null, not empty
        final var recordItem = recordItemBuilder
                .clprMaintenance()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .recordItem(r -> r.congestionPricingMultiplier(0L))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprMaintenanceLedgerConfiguration() {
        // given -- e.g. GENESIS_INITIALIZATION creating clpr_ledger_configuration's very first row with no
        // endpoint manifest change alongside it
        final var expectedRecordItem = recordItemBuilder
                .clprMaintenanceLedgerConfiguration()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprMaintenance(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprMaintenanceEndpointManifestAndLedgerConfiguration() {
        // given -- a single firing touching both singletons together, e.g. GENESIS_INITIALIZATION creating both
        // tables' very first rows at once
        final var expectedRecordItem = recordItemBuilder
                .clprMaintenanceEndpointManifestAndLedgerConfiguration()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprMaintenance(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCreateConnector() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprCreateConnector()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprCreateConnector(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprDeleteConnector() {
        // given -- no dedicated transformer, falls back to DefaultTransformer; see
        // RecordItemBuilder.clprDeleteConnector() for why clprContext still ends up with the connector-removal
        // state change via generic CLPR state-change extraction, even though
        // ClprDeleteConnectorTransactionHandler itself never reads it back
        final var expectedRecordItem = recordItemBuilder
                .clprDeleteConnector()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprDeleteConnector(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprUpdateLedgerConfiguration() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .clprUpdateLedgerConfiguration(expectedRecordItem)
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprUpdateLedgerConfigurationUnsuccessful() {
        // given -- see clprCreateChannelUnsuccessful for why clprContext stays null, not empty
        final var recordItem = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprSubmitBundle() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.clprSubmitBundle().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.clprSubmitBundle(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprSubmitBundleUnsuccessful() {
        // given -- see clprCreateChannelUnsuccessful for why clprContext stays null, not empty
        final var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprDeleteChannel() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.clprDeleteChannel().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.clprDeleteChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprDeleteChannelUnsuccessful() {
        // given -- see clprCreateChannelUnsuccessful for why clprContext stays null, not empty
        final var recordItem = recordItemBuilder
                .clprDeleteChannel()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.unsuccessfulTransaction(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems())
                .singleElement()
                .extracting(RecordItem::getClprContext)
                .isNull();
    }

    @Test
    void clprSendMessageSkippedForChildTransaction() {
        // given -- a ContractCall parent carrying a real clpr_channel_value state change, plus a child transaction
        // of that same parent; the child shares the parent's StateChangeContext by reference (see
        // AbstractBlockTransactionTransformer.transformClprSendMessageSideEffects), so it must not reprocess the
        // state the parent already claimed
        final var channelId = recordItemBuilder.bytes(32);
        final var clprChannelValue = ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setStatus(ClprChannelStatus.ACTIVE)
                .build();
        final var clprChannelMapUpdate = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setProtoBytesKey(BytesValue.of(channelId)))
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(clprChannelValue))
                .build();
        final var parentRecordItem =
                recordItemBuilder.contractCall().customize(this::finalize).build();
        final var parentBlockTransaction = blockTransactionBuilder
                .contractCall(parentRecordItem, false)
                .stateChanges(stateChanges -> stateChanges.add(StateChanges.newBuilder()
                        .addStateChanges(StateChange.newBuilder()
                                .setStateId(StateIdentifier.STATE_ID_CLPR_CHANNELS_VALUE)
                                .setMapUpdate(clprChannelMapUpdate))
                        .build()))
                .build();

        final var parentConsensusTimestamp =
                parentRecordItem.getTransactionRecord().getConsensusTimestamp();
        final var childTransactionId = parentRecordItem.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(1)
                .build();
        final var childRecordItem = recordItemBuilder
                .contractCall()
                .transactionBodyWrapper(w -> w.setTransactionID(childTransactionId))
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        final var childBlockTransaction = blockTransactionBuilder
                .contractCall(childRecordItem, false)
                .previous(parentBlockTransaction)
                .build();

        final var blockFile = blockFileBuilder
                .items(List.of(parentBlockTransaction, childBlockTransaction))
                .build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then -- the parent claims the channel; the child must see no CLPR context at all
        final var items = recordFile.getItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getClprContext().getClprChannels())
                .containsExactly(new ClprChannelEntry(channelId, clprChannelMapUpdate));
        assertThat(items.get(1).getClprContext()).isNull();
    }
}
