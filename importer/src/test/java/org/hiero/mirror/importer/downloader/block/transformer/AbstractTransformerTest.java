// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.parser.domain.BlockFileBuilder;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.springframework.data.util.Version;

abstract class AbstractTransformerTest extends ImporterIntegrationTest {

    private static final Version HAPI_VERSION = new Version(0, 57, 0);
    private static final RecursiveComparisonConfiguration RECORD_ITEMS_COMPARISON_CONFIG =
            RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields("parent", "previous", "transactionBody", "signatureMap")
                    .withEqualsForType(Object::equals, TransactionRecord.class)
                    .withEqualsForType(Object::equals, TransactionSidecarRecord.class)
                    .build();

    @Resource
    protected BlockFileBuilder blockFileBuilder;

    @Resource
    protected BlockTransactionBuilder blockTransactionBuilder;

    @Resource
    protected BlockFileTransformer blockFileTransformer;

    protected static final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    protected void assertRecordFile(RecordFile actual, BlockFile blockFile, Consumer<List<RecordItem>> itemsAssert) {
        var hapiProtoVersion = blockFile.getBlockHeader().getHapiProtoVersion();
        var softwareVersion = blockFile.getBlockHeader().getSoftwareVersion();
        assertThat(actual)
                .returns(blockFile.getBytes(), RecordFile::getBytes)
                .returns(blockFile.getConsensusEnd(), RecordFile::getConsensusEnd)
                .returns(blockFile.getConsensusStart(), RecordFile::getConsensusStart)
                .returns(blockFile.getCount(), RecordFile::getCount)
                .returns(blockFile.getDigestAlgorithm(), RecordFile::getDigestAlgorithm)
                .returns(StringUtils.EMPTY, RecordFile::getFileHash)
                .returns(0L, RecordFile::getGasUsed)
                .returns(hapiProtoVersion.getMajor(), RecordFile::getHapiVersionMajor)
                .returns(hapiProtoVersion.getMinor(), RecordFile::getHapiVersionMinor)
                .returns(hapiProtoVersion.getPatch(), RecordFile::getHapiVersionPatch)
                .returns(blockFile.getHash(), RecordFile::getHash)
                .returns(blockFile.getIndex(), RecordFile::getIndex)
                .returns(null, RecordFile::getLoadEnd)
                .returns(blockFile.getLoadStart(), RecordFile::getLoadStart)
                .returns(null, RecordFile::getLogsBloom)
                .returns(null, RecordFile::getMetadataHash)
                .returns(blockFile.getName(), RecordFile::getName)
                .returns(blockFile.getPreviousHash(), RecordFile::getPreviousHash)
                .returns(blockFile.getRoundEnd(), RecordFile::getRoundEnd)
                .returns(blockFile.getRoundStart(), RecordFile::getRoundStart)
                .returns(0, RecordFile::getSidecarCount)
                .satisfies(r -> assertThat(r.getSidecars()).isEmpty())
                .returns(blockFile.getSize(), RecordFile::getSize)
                .returns(softwareVersion.getMajor(), RecordFile::getSoftwareVersionMajor)
                .returns(softwareVersion.getMinor(), RecordFile::getSoftwareVersionMinor)
                .returns(softwareVersion.getPatch(), RecordFile::getSoftwareVersionPatch)
                .returns(blockFile.getVersion(), RecordFile::getVersion)
                .extracting(RecordFile::getItems)
                .satisfies(itemsAssert);
    }

    protected void assertRecordItems(List<RecordItem> actual, List<RecordItem> expected) {
        var expectedPreviousItems = new ArrayList<>(expected.subList(0, expected.size() - 1));
        expectedPreviousItems.addFirst(null);
        assertThat(actual)
                .usingRecursiveFieldByFieldElementComparator(RECORD_ITEMS_COMPARISON_CONFIG)
                .containsExactlyElementsOf(expected)
                .map(RecordItem::getPrevious)
                .containsExactlyElementsOf(expectedPreviousItems);
    }

    protected void finalize(RecordItemBuilder.Builder<?> builder) {
        builder.contractTransactionPredicate(null)
                .entityTransactionPredicate(null)
                .recordItem(r -> r.blockstream(true).hapiVersion(HAPI_VERSION));
    }
}
