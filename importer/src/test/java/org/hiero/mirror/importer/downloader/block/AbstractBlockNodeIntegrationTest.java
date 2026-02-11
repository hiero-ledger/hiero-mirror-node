// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBlockNodeIntegrationTest extends ImporterIntegrationTest {

    protected PassThroughStreamFileNotifier streamFileNotifier;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    @Resource
    private BlockStreamReader blockStreamReader;

    @Resource
    private CommonDownloaderProperties commonDownloaderProperties;

    @Resource
    private RecordDownloaderProperties recordDownloaderProperties;

    @Resource
    private ManagedChannelBuilderProvider managedChannelBuilderProvider;

    @Resource
    private RecordFileRepository recordFileRepository;

    private final ManagedChannelBuilderProvider inProcessManagedChannelBuilderProvider =
            new InProcessManagedChannelBuilderProvider();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    protected void assertVerifiedBlockFiles(Long... blockNumbers) {
        assertThat(streamFileNotifier.getVerifiedStreamFiles())
                .map(StreamFile::getIndex)
                .containsExactly(blockNumbers);
    }

    protected final BlockNodeSubscriber getBlockNodeSubscriber(List<BlockNodeProperties> nodes) {
        var blockProperties = new BlockProperties();
        blockProperties.setEnabled(true);
        blockProperties.setNodes(nodes);
        boolean isInProcess = nodes.getFirst().getStatusPort() == -1;
        final var cutoverService =
                new CutoverServiceImpl(blockProperties, recordDownloaderProperties, recordFileRepository);
        streamFileNotifier = new PassThroughStreamFileNotifier(cutoverService);
        var blockStreamVerifier = new BlockStreamVerifier(
                blockFileTransformer,
                blockProperties,
                cutoverService,
                mock(LedgerIdPublicationTransactionParser.class),
                meterRegistry,
                streamFileNotifier,
                mock(TssVerifier.class));
        var channelBuilderProvider =
                isInProcess ? inProcessManagedChannelBuilderProvider : managedChannelBuilderProvider;
        return new BlockNodeSubscriber(
                blockStreamReader,
                blockStreamVerifier,
                commonDownloaderProperties,
                cutoverService,
                channelBuilderProvider,
                blockProperties,
                meterRegistry);
    }

    @NullMarked
    @RequiredArgsConstructor
    protected static class PassThroughStreamFileNotifier implements StreamFileNotifier {

        private final StreamFileNotifier delegate;

        @Getter
        private final List<StreamFile<?>> verifiedStreamFiles = new ArrayList<>();

        @Override
        public void verified(final StreamFile<?> streamFile) {
            delegate.verified(streamFile);
            verifiedStreamFiles.add(streamFile);
        }
    }
}
