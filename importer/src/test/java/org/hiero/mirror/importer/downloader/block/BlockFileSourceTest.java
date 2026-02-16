// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.TestUtils.S3_PROXY_PORT;
import static org.hiero.mirror.importer.TestUtils.generateRandomByteArray;
import static org.hiero.mirror.importer.TestUtils.zstd;
import static org.hiero.mirror.importer.reader.block.BlockStreamReaderTest.TEST_BLOCK_FILES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.assertArg;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.gaul.s3proxy.S3Proxy;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.reader.block.BlockStreamReaderImpl;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
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

    @TempDir
    private Path archivePath;

    private BlockStreamVerifier blockStreamVerifier;
    private BlockFileSource blockFileSource;

    @TempDir
    private Path dataPath;

    private CommonDownloaderProperties commonDownloaderProperties;
    private CutoverService cutoverService;
    private FileCopier fileCopier;
    private ImporterProperties importerProperties;
    private BlockProperties properties;
    private MeterRegistry meterRegistry;

    @Mock
    private RecordFileRepository recordFileRepository;

    private S3Proxy s3Proxy;

    private static BlockFile blockFile(int index) {
        return TEST_BLOCK_FILES.get(index);
    }

    @BeforeEach
    @SneakyThrows
    void setup() {
        if (LoggerFactory.getLogger(getClass().getPackageName()) instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }

        importerProperties = new ImporterProperties();
        importerProperties.setDataPath(archivePath);
        commonDownloaderProperties = new CommonDownloaderProperties(importerProperties);
        commonDownloaderProperties.setPathType(PathType.NODE_ID);
        properties = new BlockProperties(importerProperties);
        properties.setEnabled(true);
        meterRegistry = new SimpleMeterRegistry();

        s3Proxy = TestUtils.startS3Proxy(dataPath);
        var s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(commonDownloaderProperties.getRegion()))
                .build();
        var streamFileProvider = new S3StreamFileProvider(
                properties, CommonProperties.getInstance(), commonDownloaderProperties, s3AsyncClient);
        var blockFileTransformer = mock(BlockFileTransformer.class);
        lenient()
                .doAnswer(invocation -> {
                    var blockFile = invocation.getArgument(0, BlockFile.class);
                    // Only the minimal set: hash and index
                    return RecordFile.builder()
                            .consensusEnd(blockFile.getConsensusEnd())
                            .consensusStart(blockFile.getConsensusStart())
                            .hash(blockFile.getHash())
                            .index(blockFile.getIndex())
                            .build();
                })
                .when(blockFileTransformer)
                .transform(any(BlockFile.class));
        cutoverService =
                new CutoverServiceImpl(properties, mock(RecordDownloaderProperties.class), recordFileRepository);
        blockStreamVerifier = spy(new BlockStreamVerifier(
                blockFileTransformer,
                new BlockProperties(new ImporterProperties()),
                cutoverService,
                mock(LedgerIdPublicationTransactionParser.class),
                meterRegistry,
                cutoverService,
                mock(TssVerifier.class)));
        blockFileSource = new BlockFileSource(
                new BlockStreamReaderImpl(),
                blockStreamVerifier,
                commonDownloaderProperties,
                cutoverService,
                meterRegistry,
                properties,
                streamFileProvider);

        var fromPath = Path.of("data", "blockstreams");
        fileCopier = FileCopier.create(
                        TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(properties.getBucketName())
                .to(importerProperties.getNetwork())
                .to(StreamType.BLOCK.getPath());
        FileUtils.forceMkdir(fileCopier.getTo().toFile());
    }

    @AfterEach
    @SneakyThrows
    void teardown() {
        s3Proxy.stop();
    }

    @SneakyThrows
    @Test
    void getFromResettableNetwork(final CapturedOutput output) {
        // given
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.PREVIEWNET);
        fileCopier = fileCopier.resetTo(dataPath).to(properties.getBucketName());
        final var prefix = importerProperties.getNetwork() + "-";
        final var formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneId.of("UTC"));
        final var now = Instant.now();
        final var previousFolder = prefix + formatter.format(now.minus(Duration.ofDays(10)));
        FileUtils.forceMkdir(fileCopier.getTo().resolve(previousFolder).toFile());
        final var latestFolder = prefix + formatter.format(now);
        fileCopier = fileCopier.to(latestFolder).to(StreamType.BLOCK.getPath());
        filterFiles(blockFile(0)).copy();
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
                .verify(assertArg(b ->
                        assertThat(b).returns(null, BlockFile::getBytes).returns(blockNumber(0), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();

        final var logs = output.getAll();
        assertThat(countMatches(logs, "Discovered latest network folder '%s'".formatted(latestFolder)))
                .isOne();
        assertThat(countMatches(logs, "Downloaded block file " + blockFile(0).getName()))
                .isOne();
    }

    @SneakyThrows
    @Test
    void getFromResettableNetworkFolderNotfound() {
        // given
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.PREVIEWNET);
        FileUtils.forceMkdir(dataPath.resolve(properties.getBucketName()).toFile());

        // when, then
        assertThatThrownBy(() -> blockFileSource.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to discover network folder for 'previewnet'");
    }

    @ParameterizedTest(name = "startBlockNumber={0}")
    @NullSource
    @ValueSource(longs = {981L})
    @SneakyThrows
    void poll(final Long startBlockNumber, final CapturedOutput output) {
        // given
        importerProperties.setStartBlockNumber(startBlockNumber);
        properties.setWriteFiles(true);
        filterFiles(blockFile(0)).copy();
        filterFiles(blockFile(1)).copy();
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
                .verify(assertArg(b ->
                        assertThat(b).returns(null, BlockFile::getBytes).returns(blockNumber(0), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();

        var logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.zstd");
        assertThat(countMatches(logs, "Downloaded block file " + blockFile(0).getName()))
                .isEqualTo(1);

        // given now persist bytes
        properties.setPersistBytes(true);
        Mockito.reset(blockStreamVerifier);

        // when
        blockFileSource.get();

        // then
        final byte[] expectedBytes = FileUtils.readFileToByteArray(fileCopier
                .getTo()
                .resolve(StreamType.BLOCK.toBucketFilename(blockFile(1).getName()))
                .toFile());
        verify(blockStreamVerifier).verify(assertArg(b -> assertThat(b)
                .returns(expectedBytes, BlockFile::getBytes)
                .returns(blockNumber(1), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();

        logs = output.getAll();
        nodeLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.zstd");
        assertThat(nodeLogs)
                .containsExactly(
                        "Downloaded block file " + blockFile(0).getName(),
                        "Downloaded block file " + blockFile(1).getName());

        verifyArchivedFile(blockFile(0).getName());
        verifyArchivedFile(blockFile(1).getName());
    }

    @Test
    void genesisNotFound(final CapturedOutput output) {
        // given, when
        final var filename = BlockFile.getFilename(0L, true);
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(recordFileRepository).findLatest();

        String logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
    }

    @SneakyThrows
    @Test
    void readerFailure(final CapturedOutput output) {
        // given
        final var filename = BlockFile.getFilename(0L, true);
        final var genesisBlockFile = fileCopier
                .getTo()
                .resolve("", StringUtils.split(StreamType.BLOCK.toBucketFilename(filename), "/"))
                .toFile();
        FileUtils.writeByteArrayToFile(genesisBlockFile, zstd(generateRandomByteArray(1024)));

        // when
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(recordFileRepository).findLatest();

        final var logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isOne();
    }

    @Test
    void startBlockNumber(final CapturedOutput output) {
        // given
        final var block0 = blockFile(0);
        final var filename = blockFile(0).getName();
        importerProperties.setStartBlockNumber(block0.getIndex());
        filterFiles(block0).copy();
        doNothing().when(blockStreamVerifier).verify(any());

        // when
        blockFileSource.get();

        // then
        verify(blockStreamVerifier).verify(assertArg(b -> assertThat(b)
                .returns(null, BlockFile::getBytes)
                .returns(block0.getIndex(), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();

        final var logs = output.getAll();
        assertThat(findAllMatches(logs, "Downloaded block file .*\\.blk\\.zstd"))
                .containsExactly("Downloaded block file " + filename);
        assertThat(countMatches(logs, "Failed to download block file " + filename))
                .isZero();
    }

    @Test
    void endBlockNumber() {
        // given
        final var block0 = blockFile(0);
        final var block1 = blockFile(1);
        importerProperties.setStartBlockNumber(block0.getIndex());
        importerProperties.setEndBlockNumber(block0.getIndex());
        filterFiles(block0).copy();
        filterFiles(block1).copy();

        // when
        blockFileSource.get();
        blockFileSource.get();

        // then
        verify(blockStreamVerifier)
                .verify(assertArg(b -> assertThat(b).returns(block0.getIndex(), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();
    }

    @Test
    void timeout(final CapturedOutput output) {
        // given
        final var filename = BlockFile.getFilename(0L, true);
        commonDownloaderProperties.setTimeout(Duration.ofMillis(100L));
        final var streamFileProvider = mock(StreamFileProvider.class);
        when(streamFileProvider.discoverNetwork()).thenReturn(Mono.just(importerProperties.getNetwork()));
        when(streamFileProvider.get(any()))
                .thenReturn(Mono.delay(Duration.ofMillis(120L)).then(Mono.empty()));
        final var source = new BlockFileSource(
                new BlockStreamReaderImpl(),
                blockStreamVerifier,
                commonDownloaderProperties,
                cutoverService,
                meterRegistry,
                properties,
                streamFileProvider);

        // when
        assertThatThrownBy(source::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier, never()).verify(any(BlockFile.class));
        verify(recordFileRepository).findLatest();
        verify(streamFileProvider).discoverNetwork();
        verify(streamFileProvider).get(any());

        final var logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isZero();
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
                cutoverService,
                meterRegistry,
                properties,
                streamFileProvider);

        // when, then
        assertThatThrownBy(source::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BlockFileSource doesn't support earliest available block number");
    }

    @Test
    void verifyFailure(final CapturedOutput output) {
        // given
        final var block0 = blockFile(0);
        doThrow(new InvalidStreamFileException("")).when(blockStreamVerifier).verify(any());
        when(recordFileRepository.findLatest())
                .thenReturn(Optional.of(RecordFile.builder()
                        .index(block0.getIndex() - 1)
                        .hash(block0.getPreviousHash())
                        .build()));
        filterFiles(block0).copy();

        // when
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + block0.getName());

        // then
        verify(blockStreamVerifier)
                .verify(assertArg(b -> assertThat(b).returns(block0.getIndex(), BlockFile::getIndex)));
        verify(recordFileRepository).findLatest();

        final var logs = output.getAll();
        final var downloadedLogs = findAllMatches(logs, "Downloaded block file .*\\.blk\\.zstd");
        assertThat(downloadedLogs).containsExactly("Downloaded block file " + block0.getName());
    }

    @Test
    void verifyFailureThenSuccess(final CapturedOutput output) {
        // given
        final var filename = blockFile(0).getName();
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
        filterFiles(blockFile(0)).copy();

        // when
        assertThatThrownBy(blockFileSource::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Failed to download block file " + filename);

        // then
        verify(blockStreamVerifier).verify(argThat(b -> b.getIndex() == blockNumber(0)));
        verify(recordFileRepository).findLatest();

        // when
        blockFileSource.get();

        // then
        verify(blockStreamVerifier, times(2)).verify(argThat(b -> b.getIndex() == blockNumber(0)));
        verify(recordFileRepository).findLatest();
        final var logs = output.getAll();
        assertThat(countMatches(logs, "Downloaded block file " + filename)).isEqualTo(2);
    }

    private long blockNumber(int index) {
        return blockFile(index).getIndex();
    }

    private FileCopier filterFiles(final BlockFile blockFile) {
        final var filename =
                StringUtils.substringAfterLast(StreamType.BLOCK.toBucketFilename(blockFile.getName()), "/");
        return fileCopier.filterFiles(filename);
    }

    @SneakyThrows
    private void verifyArchivedFile(final String filename) {
        final var bucketFilename = StreamType.BLOCK.toBucketFilename(filename);
        final byte[] expected = FileUtils.readFileToByteArray(
                fileCopier.getTo().resolve(bucketFilename).toFile());
        var actualFile = importerProperties
                .getStreamPath()
                .resolve(importerProperties.getNetwork())
                .resolve(StreamType.BLOCK.getPath())
                .resolve(bucketFilename)
                .toFile();
        assertThat(actualFile).isFile();
        final byte[] actual = FileUtils.readFileToByteArray(actualFile);
        assertThat(actual).isEqualTo(expected);
    }

    private Collection<String> findAllMatches(String message, String pattern) {
        var matcher = Pattern.compile(pattern).matcher(message);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
