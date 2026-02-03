// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.TestUtils.S3_PROXY_PORT;
import static org.hiero.mirror.importer.TestUtils.findAllMatches;
import static org.hiero.mirror.importer.TestUtils.generateRandomByteArray;
import static org.hiero.mirror.importer.TestUtils.gzip;
import static org.hiero.mirror.importer.reader.block.BlockStreamReaderTest.TEST_BLOCK_FILES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.gaul.s3proxy.S3Proxy;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.reader.block.BlockStreamReaderImpl;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
final class BlockFileSourceTest {

    private final CommonProperties commonProperties = CommonProperties.getInstance();

    @TempDir
    private Path archivePath;

    private BlockStreamVerifier blockStreamVerifier;
    private BlockFileSource blockFileSource;

    @Mock(strictness = Strictness.LENIENT)
    private ConsensusNodeService consensusNodeService;

    @TempDir
    private Path dataPath;

    private CommonDownloaderProperties commonDownloaderProperties;
    private FileCopier fileCopier;
    private ImporterProperties importerProperties;
    private List<ConsensusNode> nodes;
    private BlockProperties properties;
    private MeterRegistry meterRegistry;

    @Mock
    private RecordFileRepository recordFileRepository;

    private S3Proxy s3Proxy;

    private static BlockFile blockFile(int index) {
        return TEST_BLOCK_FILES.get(index);
    }

    @BeforeEach
    void setup() {
        if (LoggerFactory.getLogger(getClass().getPackageName()) instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }

        importerProperties = new ImporterProperties();
        importerProperties.setDataPath(archivePath);
        commonDownloaderProperties = new CommonDownloaderProperties(importerProperties);
        commonDownloaderProperties.setPathType(PathType.NODE_ID);
        properties = new BlockProperties();
        properties.setEnabled(true);
        meterRegistry = new SimpleMeterRegistry();

        nodes = List.of(
                ConsensusNodeStub.builder().nodeId(0).build(),
                ConsensusNodeStub.builder().nodeId(1).build(),
                ConsensusNodeStub.builder().nodeId(2).build(),
                ConsensusNodeStub.builder().nodeId(3).build());
        when(consensusNodeService.getNodes()).thenReturn(nodes);

        s3Proxy = TestUtils.startS3Proxy(dataPath);
        var s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(commonDownloaderProperties.getRegion()))
                .build();
        var streamFileProvider = new S3StreamFileProvider(commonProperties, commonDownloaderProperties, s3AsyncClient);
        var blockFileTransformer = mock(BlockFileTransformer.class);
        lenient()
                .doAnswer(invocation -> {
                    var blockFile = invocation.getArgument(0, BlockFile.class);
                    // Only the minimal set: hash and index
                    return RecordFile.builder()
                            .hash(blockFile.getHash())
                            .index(blockFile.getIndex())
                            .build();
                })
                .when(blockFileTransformer)
                .transform(any(BlockFile.class));
        blockStreamVerifier = spy(new BlockStreamVerifier(
                blockFileTransformer,
                commonDownloaderProperties,
                recordFileRepository,
                mock(StreamFileNotifier.class),
                meterRegistry));
        blockFileSource = new BlockFileSource(
                new BlockStreamReaderImpl(),
                blockStreamVerifier,
                commonDownloaderProperties,
                consensusNodeService,
                meterRegistry,
                properties,
                streamFileProvider);

        var fromPath = Path.of("data", "blockstreams");
        fileCopier = FileCopier.create(
                        TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(commonDownloaderProperties.getBucketName())
                .to(Long.toString(commonProperties.getShard()));
    }

    @AfterEach
    @SneakyThrows
    void teardown() {
        s3Proxy.stop();
    }

    @ParameterizedTest(name = "startBlockNumber={0}")
    @NullSource
    @ValueSource(longs = {981L})
    @SneakyThrows
    void poll(Long startBlockNumber, CapturedOutput output) {
        // given
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(startBlockNumber);
        properties.setWriteFiles(true);
        fileCopier.filterFiles(blockFile(0).getName()).to("0").copy();
        fileCopier.filterFiles(blockFile(1).getName()).to("2").copy();
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .consensusStart(blockFile(0).getConsensusStart())
                        .build()));

        // when
        blockFileSource.get();

        // then
        verify(blockStreamVerifier)
                .verify(argThat(b -> b.getBytes() == null && b.getIndex() == blockNumber(0) && b.getNodeId() == 0L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs)
                .containsExactly("Downloaded block file " + blockFile(0).getName() + " from node 0");

        // given now persist bytes
        properties.setPersistBytes(true);
        Mockito.reset(blockStreamVerifier);

        // when
        blockFileSource.get();

        // then
        byte[] expectedBytes = FileUtils.readFileToByteArray(
                fileCopier.getTo().resolve("2").resolve(blockFile(1).getName()).toFile());
        verify(blockStreamVerifier)
                .verify(argThat(b -> Arrays.equals(b.getBytes(), expectedBytes)
                        && b.getIndex() == blockNumber(1)
                        && b.getNodeId() == 2L));
        verify(consensusNodeService, times(2)).getNodes();
        verify(recordFileRepository).findLatest();

        logs = output.getAll();
        nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(nodeLogs)
                .containsExactly(
                        "Downloaded block file " + blockFile(0).getName() + " from node 0",
                        "Downloaded block file " + blockFile(1).getName() + " from node 2");
        assertThat(countMatches(logs, "Failed to download block file ")).isZero();

        verifyArchivedFile(blockFile(0).getName(), 0);
        verifyArchivedFile(blockFile(1).getName(), 2);
    }

    @Test
    void genesisNotFound(CapturedOutput output) {
        // given, when
        String filename = BlockFile.getFilename(0L, true);
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
        var nodeLogs = findAllMatches(logs, "Failed to process block file " + filename + " from node \\d");
        var expectedNodeLogs = nodes.stream()
                .map(ConsensusNode::getNodeId)
                .map(nodeId -> "Failed to process block file %s from node %d".formatted(filename, nodeId))
                .toList();
        assertThat(nodeLogs).containsExactlyInAnyOrderElementsOf(expectedNodeLogs);
    }

    @SneakyThrows
    @Test
    void readerFailure(CapturedOutput output) {
        // given
        var filename = BlockFile.getFilename(0L, true);
        var genesisBlockFile = fileCopier.getTo().resolve("0").resolve(filename).toFile();
        FileUtils.writeByteArrayToFile(genesisBlockFile, gzip(generateRandomByteArray(1024)));

        // when
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename + " from node 0"))
                .isOne();
        var errorLogs = findAllMatches(logs, "Failed to process block file " + filename + " from node 0");
        assertThat(errorLogs).hasSize(1);
    }

    @Test
    void startBlockNumber(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        commonDownloaderProperties
                .getImporterProperties()
                .setStartBlockNumber(blockFile(0).getIndex());
        fileCopier.filterFiles(filename).to("0").copy();
        doNothing().when(blockStreamVerifier).verify(any());

        // when
        blockFileSource.get();

        // then
        verify(blockStreamVerifier)
                .verify(argThat(b -> b.getBytes() == null && b.getIndex() == blockNumber(0) && b.getNodeId() == 0L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        assertThat(findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d"))
                .containsExactly("Downloaded block file " + filename + " from node 0");
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    @Test
    void endBlockNumber() {
        // given
        var block0 = blockFile(0);
        var block1 = blockFile(1);
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(block0.getIndex());
        commonDownloaderProperties.getImporterProperties().setEndBlockNumber(block0.getIndex());
        fileCopier.filterFiles(block0.getName()).to("0").copy();
        fileCopier.filterFiles(block1.getName()).to("1").copy();

        // when
        blockFileSource.get();
        blockFileSource.get();

        // then
        verify(blockStreamVerifier)
                .verify(argThat(b -> Objects.equals(b.getIndex(), block0.getIndex()) && b.getNodeId() == 0L));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();
    }

    @Test
    void timeout(CapturedOutput output) {
        // given
        String filename = BlockFile.getFilename(0L, true);
        commonDownloaderProperties.setTimeout(Duration.ofMillis(100L));
        var streamFileProvider = mock(StreamFileProvider.class);
        when(streamFileProvider.get(any(), any()))
                .thenReturn(Mono.delay(Duration.ofMillis(120L)).then(Mono.empty()));
        var source = new BlockFileSource(
                new BlockStreamReaderImpl(),
                blockStreamVerifier,
                commonDownloaderProperties,
                consensusNodeService,
                meterRegistry,
                properties,
                streamFileProvider);

        // when
        assertThatThrownBy(source::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();
        verify(streamFileProvider).get(any(), any());

        String logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
        assertThat(countMatches(logs, "Failed to download block file " + filename + "from node"))
                .isZero();
        assertThat(countMatches(logs, "Failed to process block file " + filename))
                .isOne();
    }

    @Test
    void throwWhenStartFromEarliestAvailableBlockNumber() {
        // given
        importerProperties.setStartBlockNumber(-1L);
        final var streamFileProvider = mock(StreamFileProvider.class);
        final var source = new BlockFileSource(
                new BlockStreamReaderImpl(),
                blockStreamVerifier,
                commonDownloaderProperties,
                consensusNodeService,
                meterRegistry,
                properties,
                streamFileProvider);

        // when, then
        assertThatThrownBy(source::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BlockFileSource doesn't support earliest available block number");
    }

    @Test
    void verifyFailure(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        doThrow(new InvalidStreamFileException("")).when(blockStreamVerifier).verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .build()));
        fileCopier.filterFiles(filename).to("0").copy();
        fileCopier.filterFiles(filename).to("1").copy();

        // when
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == blockNumber(0)));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        var downloadedLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.gz from node \\d");
        assertThat(downloadedLogs)
                .containsExactlyInAnyOrder(
                        "Downloaded block file " + filename + " from node 0",
                        "Downloaded block file " + filename + " from node 1");
        var errorLogs = findAllMatches(
                logs, "(failing to download|Failed to process) block file " + filename + " from node \\d");
        var expected = Stream.concat(
                        Stream.of(0L, 1L).map(nodeId -> "Failed to process block file %s from node %d"
                                .formatted(filename, nodeId)),
                        Stream.of(2L, 3L).map(nodeId -> "failing to download block file %s from node %d"
                                .formatted(filename, nodeId)))
                .toList();
        assertThat(errorLogs).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(5)
    void verifyFailureThenSuccess(CapturedOutput output) {
        // given
        var filename = blockFile(0).getName();
        doThrow(new InvalidStreamFileException(""))
                .doCallRealMethod()
                .when(blockStreamVerifier)
                .verify(any(BlockFile.class));
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(blockFile(0).getIndex() - 1)
                        .hash(blockFile(0).getPreviousHash())
                        .consensusStart(blockFile(0).getConsensusStart())
                        .build()));
        fileCopier.filterFiles(filename).to("0").copy();
        fileCopier.filterFiles(filename).to("1").copy();

        // when
        blockFileSource.get();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == blockNumber(0)));
        verify(consensusNodeService).getNodes();
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        var downloadedLogs = findAllMatches(logs, "Downloaded block file " + filename + " from node \\d");
        assertThat(downloadedLogs)
                .containsExactlyInAnyOrder(
                        "Downloaded block file " + filename + " from node 0",
                        "Downloaded block file " + filename + " from node 1");
        assertThat(countMatches(logs, "Failed to process block file " + filename))
                .isBetween(1, 3);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    private long blockNumber(int index) {
        return blockFile(index).getIndex();
    }

    @SneakyThrows
    private void verifyArchivedFile(String filename, long nodeId) {
        byte[] expected = FileUtils.readFileToByteArray(fileCopier
                .getTo()
                .resolve(Long.toString(nodeId))
                .resolve(filename)
                .toFile());
        var actualFile = importerProperties
                .getStreamPath()
                .resolve(Long.toString(commonProperties.getShard()))
                .resolve(Long.toString(nodeId))
                .resolve(filename)
                .toFile();
        assertThat(actualFile).isFile();
        byte[] actual = FileUtils.readFileToByteArray(actualFile);
        assertThat(actual).isEqualTo(expected);
    }
}
