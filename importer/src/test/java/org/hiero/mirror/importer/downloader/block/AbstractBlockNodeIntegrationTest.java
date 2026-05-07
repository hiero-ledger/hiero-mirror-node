// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.io.Serial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.NodeSignatureVerifier;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverProperties;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverServiceImpl;
import org.hiero.mirror.importer.downloader.block.scheduler.LatencyService;
import org.hiero.mirror.importer.downloader.block.scheduler.LatencyServiceProperties;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerProperties;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerSupplier;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.block.hash.BlockStateProofHasher;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
abstract class AbstractBlockNodeIntegrationTest extends ImporterIntegrationTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected BlockNodeDiscoveryService blockNodeDiscoveryService;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    protected BlockProperties blockProperties;

    @Resource
    private BlockStreamReader blockStreamReader;

    protected BlockStreamVerifier blockStreamVerifier;

    @Resource
    protected CommonDownloaderProperties commonDownloaderProperties;

    protected CutoverService cutoverService;

    @AutoClose
    protected ScheduledExecutorService executor;

    @Resource
    private ImporterProperties importerProperties;

    @AutoClose
    protected LatencyService latencyService;

    @Resource
    private ManagedChannelBuilderProvider managedChannelBuilderProvider;

    @Resource
    private RecordDownloaderProperties recordDownloaderProperties;

    @Resource
    private RecordFileRepository recordFileRepository;

    protected SchedulerProperties schedulerProperties = new SchedulerProperties();

    @AutoClose
    protected AutoCloseArrayList<BlockNodeSimulator> simulators = new AutoCloseArrayList<>();

    protected PassThroughStreamFileNotifier streamFileNotifier;

    @AutoClose
    protected BlockNodeSubscriber subscriber;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        blockProperties = new BlockProperties(importerProperties);
        blockProperties.setEnabled(true);
        cutoverService = new CutoverServiceImpl(
                blockProperties, new CutoverProperties(), recordDownloaderProperties, recordFileRepository);
        streamFileNotifier = new PassThroughStreamFileNotifier(cutoverService);
        blockStreamVerifier = new BlockStreamVerifier(
                blockFileTransformer,
                mock(BlockStateProofHasher.class),
                mock(ConsensusNodeService.class),
                cutoverService,
                mock(LedgerIdPublicationTransactionParser.class),
                meterRegistry,
                mock(NodeSignatureVerifier.class),
                streamFileNotifier,
                mock(TssVerifier.class));
        schedulerProperties.setMinRescheduleInterval(Duration.ofMillis(500));
        schedulerProperties.setRescheduleLatencyThreshold(Duration.ofMillis(20));
        latencyService = new LatencyService(blockStreamReader, cutoverService, new LatencyServiceProperties());
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> latencyService.schedule(), 5, 5, TimeUnit.MILLISECONDS);
    }

    @AfterEach
    void teardown() {
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(null);
    }

    protected void assertVerifiedBlockFiles(Long... blockNumbers) {
        assertVerifiedBlockFiles(Arrays.stream(blockNumbers).toList());
    }

    protected void assertVerifiedBlockFiles(List<Long> blockNumbers) {
        assertThat(streamFileNotifier.getVerifiedStreamFiles())
                .map(StreamFile::getIndex)
                .containsExactlyElementsOf(blockNumbers);
    }

    protected final String endpoint(int index) {
        return simulators.get(index).getEndpoint();
    }

    protected final BlockNodeSubscriber getBlockNodeSubscriber() {
        return getBlockNodeSubscriber(false);
    }

    protected final BlockNodeSubscriber getBlockNodeSubscriber(boolean reversedNodes) {
        startAll();
        final var nodes = reversedNodes ? getBlockNodeProperties().reversed() : getBlockNodeProperties();
        blockProperties.setNodes(nodes);
        var channelBuilderProvider = nodes.getFirst().getPort() == -1
                ? InProcessManagedChannelBuilderProvider.INSTANCE
                : managedChannelBuilderProvider;

        final var sortedNodes = getBlockNodeProperties();
        Collections.sort(sortedNodes);
        when(blockNodeDiscoveryService.getBlockNodes()).thenReturn(sortedNodes);
        final var schedulerSupplier = new SchedulerSupplier(
                blockNodeDiscoveryService,
                blockProperties,
                latencyService,
                channelBuilderProvider,
                meterRegistry,
                schedulerProperties);
        return new BlockNodeSubscriber(
                blockStreamReader,
                blockStreamVerifier,
                commonDownloaderProperties,
                cutoverService,
                blockProperties,
                schedulerSupplier);
    }

    protected final BlockNodeSimulator addSimulatorWithBlocks(List<BlockGenerator.BlockRecord> blocks) {
        var simulator = new BlockNodeSimulator().withBlocks(blocks).withHttpChannel();
        simulators.add(simulator);
        return simulator;
    }

    private List<BlockNodeProperties> getBlockNodeProperties() {
        return simulators.stream().map(BlockNodeSimulator::toClientProperties).collect(Collectors.toList());
    }

    private void startAll() {
        simulators.forEach(BlockNodeSimulator::start);
    }

    protected static class AutoCloseArrayList<E extends AutoCloseable> extends ArrayList<E> {

        @Serial
        private static final long serialVersionUID = -8643910543540510015L;

        @SneakyThrows
        public void close() {
            for (var e : this) {
                e.close();
            }
        }
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
