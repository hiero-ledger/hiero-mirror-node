// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class RecordFileReaderImplV5Test extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new RecordFileReaderImplV5();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 5;
    }

    @SneakyThrows
    @TestFactory
    Stream<DynamicTest> verifyRecordItemLinksInEthTransactionValidFile() {
        String template = "read file %s containing eth transactions";
        var resourceResolver = new PathMatchingResourcePatternResolver();

        return DynamicTest.stream(
                Arrays.stream(resourceResolver.getResources(
                        "classpath:data/recordstreams/eth-0.26.0/record0.0.3/*" + ".rcd")),
                recordFile -> String.format(template, recordFile.getFilename()),
                recordFile -> {
                    // given
                    StreamFileData streamFileData = StreamFileData.from(recordFile.getFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    RecordItem previousItem = null;
                    RecordItem lastParentItem = null;
                    for (var item : actual.getItems()) {
                        // assert previous link points to previous item
                        assertThat(item.getPrevious()).isEqualTo(previousItem);

                        // confirm if child that parent is populated
                        if (item.isChild()) {
                            assertThat(item.getParent()).isEqualTo(lastParentItem);
                        } else {
                            lastParentItem = item;
                        }

                        previousItem = item;
                    }
                });
    }
}
