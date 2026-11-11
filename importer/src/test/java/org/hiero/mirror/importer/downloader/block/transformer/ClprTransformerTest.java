// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClprTransformerTest extends AbstractTransformerTest {

    @Test
    void clprCompleteChannel() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprCompleteChannel()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprCompleteChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCompleteConnector() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprCompleteConnector()
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .clprCompleteConnector(expectedRecordItem)
                .build();
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
    void clprRegisterChannel() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprRegisterChannel()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprRegisterChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprRegisterConnector() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .clprRegisterConnector()
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .clprRegisterConnector(expectedRecordItem)
                .build();
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
    void clprCloseChannel() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.clprCloseChannel().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.clprCloseChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCloseChannelAbandonedCommitment() {
        // given -- no Channel existed yet for the supplied channel_id, so there's no clpr_channel_value state
        // change to read back; the transformer should leave RecordItem.clprContext.clprChannel unset
        final var expectedRecordItem = recordItemBuilder
                .clprCloseChannel()
                .recordItem(r -> r.clprContextBuilder().clprChannels(List.of()))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprCloseChannel(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }
}
