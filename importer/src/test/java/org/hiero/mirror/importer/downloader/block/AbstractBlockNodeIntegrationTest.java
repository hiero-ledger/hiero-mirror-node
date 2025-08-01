// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.util.List;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBlockNodeIntegrationTest extends ImporterIntegrationTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected StreamFileNotifier streamFileNotifier;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    @Resource
    private BlockStreamReader blockStreamReader;

    @Resource
    private CommonDownloaderProperties commonDownloaderProperties;

    @Resource
    private ManagedChannelBuilderProvider managedChannelBuilderProvider;

    @Resource
    private RecordFileRepository recordFileRepository;

    private final ManagedChannelBuilderProvider inProcessManagedChannelBuilderProvider =
            new InProcessManagedChannelBuilderProvider();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    protected BlockNodeSubscriber getBlockNodeSubscriber(List<BlockNodeProperties> nodes) {
        var blockProperties = new BlockProperties();
        blockProperties.setNodes(nodes);
        boolean isInProcess = nodes.getFirst().getPort() == -1;
        var blockStreamVerifier =
                new BlockStreamVerifier(blockFileTransformer, recordFileRepository, streamFileNotifier, meterRegistry);
        var channelBuilderProvider =
                isInProcess ? inProcessManagedChannelBuilderProvider : managedChannelBuilderProvider;
        return new BlockNodeSubscriber(
                blockStreamReader,
                blockStreamVerifier,
                commonDownloaderProperties,
                channelBuilderProvider,
                blockProperties);
    }
}
