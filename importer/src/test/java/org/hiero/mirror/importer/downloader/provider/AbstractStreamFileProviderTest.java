// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.domain.StreamFilename.SIDECAR_FOLDER;
import static org.hiero.mirror.importer.reader.block.BlockStreamReaderTest.TEST_BLOCK_FILES;

import com.google.common.collect.Streams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.AccumulatorPathVisitor;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.PathMatcherFileFilter;
import org.bouncycastle.util.Arrays;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.BlockBucketProperties;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.reader.signature.ProtoSignatureFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

abstract class AbstractStreamFileProviderTest {

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    @TempDir
    protected Path dataPath;

    protected BlockBucketProperties blockBucketProperties;
    protected String blockStreamTargetRootPath;
    protected CommonProperties commonProperties = CommonProperties.getInstance();
    protected ImporterProperties importerProperties;
    protected CommonDownloaderProperties properties;
    protected StreamFileProvider streamFileProvider;
    protected String targetRootPath;

    @BeforeEach
    void setup() {
        importerProperties = new ImporterProperties();
        importerProperties.setDataPath(dataPath);
        blockBucketProperties = new BlockBucketProperties(importerProperties);
        properties = new CommonDownloaderProperties(importerProperties);
    }

    protected FileCopier createDefaultFileCopier() {
        return createFileCopier(Path.of("data", "recordstreams", "v6"), StreamType.RECORD.getPath());
    }

    protected FileCopier createFileCopier(Path fromPath, String toPath) {
        return FileCopier.create(TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(targetRootPath, properties.getPathPrefix(), toPath);
    }

    protected abstract String providerPathSeparator();

    @Test
    void get() {
        var node = node(3);
        var nodeFileCopier = createDefaultFileCopier();
        get(nodeFileCopier, node);
    }

    @Test
    void getLargeFile() {
        var node = node(3);
        var nodeFileCopier = createDefaultFileCopier();
        nodeFileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.create(streamFileProvider.get(data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(2L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(4000L));

        // Increase data2 1 byte beyond the max size
        long maxSize = data.getBytes().length;
        properties.setMaxSize(maxSize);
        replaceContents(data, Arrays.append(data.getBytes(), (byte) 1));

        StepVerifier.withVirtualTime(() -> streamFileProvider.get(data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(InvalidDatasetException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getSidecar() {
        getSidecar(createDefaultFileCopier(), node(3));
    }

    @Test
    void getNotFound() {
        getNotFound(createDefaultFileCopier(), node(3));
    }

    @Test
    void getError() {
        getError(createDefaultFileCopier(), node(3));
    }

    @Test
    void getBlockFile() {
        // given
        createBlockStreamFileCopier().copy();
        final var expected = streamFileData(TEST_BLOCK_FILES.getFirst().getIndex());

        // when, then
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(expected.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(expected)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void getBlockFileNotFound() {
        // given
        createBlockStreamFileCopier().copy();
        final var path = "%s/%s".formatted(importerProperties.getNetwork(), StreamType.BLOCK.getPath());
        final var streamFilename = StreamFilename.from(path, 7858853);

        // when, then
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(streamFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(TransientProviderException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void list() {
        var node = node(3);
        list(createDefaultFileCopier(), node);
    }

    @SneakyThrows
    @Test
    void listNetwork() {
        // given
        final var rootPath = dataPath.resolve(blockStreamTargetRootPath);
        final var now = Instant.now();
        final var latestFolder = "%s-%s".formatted(importerProperties.getNetwork(), now);
        final var previousFolder = "%s-%s".formatted(importerProperties.getNetwork(), now.minus(Duration.ofDays(10)));
        FileUtils.forceMkdir(rootPath.resolve(latestFolder).toFile());
        FileUtils.forceMkdir(rootPath.resolve(previousFolder).toFile());
        // create a top-level file and a subdirectory
        FileUtils.touch(rootPath.resolve("streamfile").toFile());
        FileUtils.forceMkdir(
                rootPath.resolve(latestFolder).resolve(previousFolder).toFile());

        // when, then
        StepVerifier.create(streamFileProvider.listNetwork().collectList())
                .assertNext(actualFolders ->
                        assertThat(actualFolders).containsExactlyInAnyOrder(latestFolder, previousFolder))
                .verifyComplete();
    }

    @Test
    void listWithPathPrefix() {
        properties.setPathPrefix("prefix");
        var node = node(3);
        list(createDefaultFileCopier(), node);
    }

    @Test
    void listThenGet() {
        var node = node(3);
        var nodeFileCopier = createDefaultFileCopier();
        nodeFileCopier.copy();
        var last = StreamFilename.from("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var recordFile = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd.gz");
        var sidecar = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");

        var files = streamFileProvider
                .list(node, last)
                .map(StreamFileData::getStreamFilename)
                .map(s -> StreamFileSignature.builder()
                        .filename(s)
                        .version(ProtoSignatureFileReader.VERSION)
                        .build())
                .map(StreamFileSignature::getDataFilename)
                .flatMap(s -> streamFileProvider.get(s))
                .collectList()
                .block();
        assertThat(files)
                .hasSize(1)
                .first()
                .isEqualTo(recordFile)
                .extracting(StreamFileData::getDecompressedBytes)
                .isEqualTo(recordFile.getDecompressedBytes());

        var sidecars = Flux.fromIterable(files)
                .map(StreamFileData::getStreamFilename)
                .flatMap(s -> streamFileProvider.get(StreamFilename.from(s, s.getSidecarFilename(1))))
                .collectList()
                .block();
        assertThat(sidecars)
                .hasSize(1)
                .first()
                .isEqualTo(sidecar)
                .extracting(StreamFileData::getDecompressedBytes)
                .isEqualTo(sidecar.getDecompressedBytes());
    }

    @Test
    void listThenGetWithPathPrefix() {
        properties.setPathPrefix("prefix");
        listThenGet();
    }

    @Test
    void listWhenBatchSizeLessThanFilesAvailable() {
        var node = node(3);
        var nodeFileCopier = createDefaultFileCopier();
        nodeFileCopier.copy();
        streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd.gz");
        var sig1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd.gz");
        streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        properties.setBatchSize(1);
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(sig1)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void listAfter() {
        var node = node(3);
        listAfter(createDefaultFileCopier(), node);
    }

    @Test
    void listNotFound() {
        var node = node(3);
        listNotFound(createDefaultFileCopier(), node);
    }

    @Test
    void listError() {
        var node = node(3);
        listError(createDefaultFileCopier(), node);
    }

    @Test
    void listInvalidFilename() {
        var node = node(3);
        listInvalidFilename(createDefaultFileCopier(), node);
    }

    @Test
    void listLargeFiles() {
        var node = node(3);
        var nodeFileCopier = createDefaultFileCopier();
        nodeFileCopier.copy();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");

        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));

        // Increase data2 1 byte beyond the max size
        long maxSize = data2.getBytes().length;
        properties.setMaxSize(maxSize);
        replaceContents(data2, Arrays.append(data2.getBytes(), (byte) 1));

        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @SneakyThrows
    private void replaceContents(StreamFileData streamFileData, byte[] contents) {
        var file = createDefaultFileCopier()
                .getTo()
                .getParent()
                .resolve(streamFileData.getFilePath())
                .toFile();
        FileUtils.writeByteArrayToFile(file, contents);
    }

    protected final void get(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getSidecar(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z_01.rcd.gz");
        StepVerifier.withVirtualTime(() -> streamFileProvider
                        .get(data.getStreamFilename())
                        .doOnNext(sfd -> assertThat(sfd.getBytes()).isEqualTo(data.getBytes())))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getNotFound(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(TransientProviderException.class)
                .verify(Duration.ofSeconds(10L));
    }

    protected final void getError(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        dataPath.toFile().setExecutable(false);
        StepVerifier.withVirtualTime(() -> streamFileProvider.get(data.getStreamFilename()))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    protected final void list(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listAfter(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var lastFilename = StreamFilename.from("2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listNotFound(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var lastFilename = StreamFilename.from("2100-01-01T01_01_01.000000001Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected final void listError(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        dataPath.toFile().setExecutable(false);
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectError(RuntimeException.class)
                .verify(Duration.ofSeconds(10L));
    }

    @SneakyThrows
    protected final void listInvalidFilename(FileCopier fileCopier, ConsensusNode node) {
        fileCopier.copy();
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        fileCopier
                .getTo()
                // the parent folder is either recordstreams or the network name, needed because data1's path includes
                // the parent folder
                .getParent()
                .resolve(data1.getStreamFilename().getPath())
                .resolve("Invalid.file")
                .toFile()
                .createNewFile();
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    protected ConsensusNode node(long accountNum) {
        var nodeAccountId = EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), accountNum);
        return ConsensusNodeStub.builder()
                .nodeAccountId(nodeAccountId)
                .nodeId(nodeAccountId.getNum() - 3)
                .build();
    }

    @SneakyThrows
    protected StreamFileData streamFileData(ConsensusNode node, String filename) {
        // regex to match file path with either ACCOUNT_ID or NODE_ID path style
        String regex = String.format("^.*(%s|/\\d+/%d)/(.*/)?%s$", node.getNodeAccountId(), node.getNodeId(), filename);
        var pattern = Pattern.compile(regex);
        PathMatcher pathMatcher = path -> pattern.matcher(path.toString()).matches();
        var visitor = AccumulatorPathVisitor.withLongCounters(
                new PathMatcherFileFilter(pathMatcher), FileFilterUtils.trueFileFilter());
        Files.walkFileTree(dataPath, visitor);
        var filePath = visitor.getFileList().getFirst();
        var basePath = Streams.stream(filePath.iterator())
                .map(Path::toString)
                // also ignore sidecar folder because StreamFilename adds it for sidecar files
                .filter(p -> !Objects.equals(p, SIDECAR_FOLDER) && !Objects.equals(p, filename))
                // ignore path segments until hit recordstreams or the network name
                .dropWhile(
                        p -> !Objects.equals(p, "recordstreams") && !Objects.equals(p, importerProperties.getNetwork()))
                .collect(Collectors.joining(providerPathSeparator()));
        var streamFilename = StreamFilename.from(basePath, filename, providerPathSeparator());
        var bytes = FileUtils.readFileToByteArray(filePath.toFile());
        return new StreamFileData(streamFilename, () -> bytes, Instant.now());
    }

    @SneakyThrows
    protected StreamFileData streamFileData(final long blockNumber) {
        final var basePath = "%s/%s".formatted(importerProperties.getNetwork(), StreamType.BLOCK.getPath());
        final var streamFilename = StreamFilename.from(basePath, blockNumber);
        final var filePath = dataPath.resolve(targetRootPath).resolve(streamFilename.getBucketFilePath());
        final byte[] bytes = FileUtils.readFileToByteArray(filePath.toFile());
        return new StreamFileData(streamFilename, () -> bytes, Instant.now());
    }

    private FileCopier createBlockStreamFileCopier() {
        targetRootPath = blockStreamTargetRootPath;
        final var toPath = importerProperties.getNetwork() + "/block";
        return createFileCopier(Path.of("data", "blockstreams"), toPath);
    }
}
