// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClprTransformerTest extends AbstractTransformerTest {

    @Test
    void clprCompleteChannel() {
        // given
        var expectedRecordItem = recordItemBuilder
                .clprCompleteChannel()
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.clprCompleteChannel(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCompleteConnector() {
        // given
        var expectedRecordItem = recordItemBuilder
                .clprCompleteConnector()
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .clprCompleteConnector(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprMaintenance() {
        // given
        var expectedRecordItem =
                recordItemBuilder.clprMaintenance().customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.clprMaintenance(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprRegisterChannel() {
        // given
        var expectedRecordItem = recordItemBuilder
                .clprRegisterChannel()
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.clprRegisterChannel(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprRegisterConnector() {
        // given
        var expectedRecordItem = recordItemBuilder
                .clprRegisterConnector()
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .clprRegisterConnector(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprUpdateLedgerConfiguration() {
        // given
        var expectedRecordItem = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .clprUpdateLedgerConfiguration(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprSubmitBundle() {
        // given
        var expectedRecordItem =
                recordItemBuilder.clprSubmitBundle().customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.clprSubmitBundle(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCloseChannel() {
        // given
        var expectedRecordItem =
                recordItemBuilder.clprCloseChannel().customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.clprCloseChannel(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }

    @Test
    void clprCloseChannelAbandonedCommitment() {
        // given -- no Channel existed yet for the supplied channel_id, so there's no clpr_channel_value state
        // change to read back; the transformer should leave RecordItem.clprChannel unset
        var expectedRecordItem = recordItemBuilder
                .clprCloseChannel()
                .recordItem(r -> r.clprChannel(null))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.clprCloseChannel(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, List.of(expectedRecordItem)));
    }
}
