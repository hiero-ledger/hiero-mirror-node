// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import java.io.Serial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.block.scheduler.LatencyService;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerSupplier;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
abstract class AbstractBlockNodeIntegrationTest extends ImporterIntegrationTest {

    protected BlockProperties blockProperties = new BlockProperties();

    protected BlockStreamVerifier blockStreamVerifier;

    @AutoClose
    protected ScheduledExecutorService executor;

    @AutoClose
    protected LatencyService latencyService;

    @AutoClose
    protected AutoCloseArrayList<BlockNodeSimulator> simulators = new AutoCloseArrayList<>();

    @AutoClose
    protected BlockNodeSubscriber subscriber;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    @Resource
    private BlockStreamReader blockStreamReader;

    @Resource
    protected CommonDownloaderProperties commonDownloaderProperties;

    @Resource
    private ManagedChannelBuilderProvider managedChannelBuilderProvider;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected StreamFileNotifier streamFileNotifier;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        blockStreamVerifier = new BlockStreamVerifier(
                blockFileTransformer,
                commonDownloaderProperties,
                recordFileRepository,
                streamFileNotifier,
                meterRegistry);
        var scheduler = blockProperties.getScheduler();
        scheduler.setMinRescheduleInterval(Duration.ofMillis(500));
        scheduler.setRescheduleLatencyThreshold(Duration.ofMillis(20));
        latencyService = new LatencyService(blockProperties, blockStreamReader, blockStreamVerifier);
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> latencyService.schedule(), 5, 5, TimeUnit.MILLISECONDS);
    }

    @AfterEach
    void teardown() {
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(null);
    }

    protected final String endpoint(int index) {
        return simulators.get(index).getEndpoint();
    }

    protected final BlockNodeSubscriber getBlockNodeSubscriber() {
        return getBlockNodeSubscriber(false);
    }

    protected final BlockNodeSubscriber getBlockNodeSubscriber(boolean reversedNodes) {
        startAll();
        var nodes = reversedNodes ? getBlockNodeProperties().reversed() : getBlockNodeProperties();
        blockProperties.setNodes(nodes);
        var channelBuilderProvider = nodes.getFirst().getPort() == -1
                ? InProcessManagedChannelBuilderProvider.INSTANCE
                : managedChannelBuilderProvider;
        var schedulerSupplier =
                new SchedulerSupplier(blockProperties, latencyService, channelBuilderProvider, meterRegistry);
        return new BlockNodeSubscriber(
                blockStreamReader, blockStreamVerifier, commonDownloaderProperties, blockProperties, schedulerSupplier);
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
}
