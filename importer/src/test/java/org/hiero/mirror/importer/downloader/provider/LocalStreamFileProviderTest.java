// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.ImporterProperties.STREAMS;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import lombok.SneakyThrows;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

final class LocalStreamFileProviderTest extends AbstractStreamFileProviderTest {

    private final LocalStreamFileProperties localProperties = new LocalStreamFileProperties();

    @Override
    protected String providerPathSeparator() {
        return File.separator;
    }

    @Override
    @BeforeEach
    void setup() {
        super.setup();
        blockStreamTargetRootPath = STREAMS;
        streamFileProvider = new LocalStreamFileProvider(properties, localProperties);
        targetRootPath = STREAMS;
    }

    @Test
    void listAll() {
        var node = node(3);
        createDefaultFileCopier().copy();
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(sig -> streamFileProvider.get(sig.getStreamFilename()).block());
    }

    @Test
    void listByDay() {
        var node = node(3);
        var folder = "record" + node.getNodeAccountId();
        createSignature("2022-07-13", "recordstreams", folder, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        createSignature("2022-07-13", "recordstreams", folder, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(sig -> streamFileProvider.get(sig.getStreamFilename()).block());
    }

    @Test
    void listAcrossDays() {
        var node = node(3);
        var folder = "record" + node.getNodeAccountId();
        createSignature("2022-07-13", "recordstreams", folder, "2022-07-13T23_59_59.304284003Z.rcd_sig");
        createSignature("2022-07-14", "recordstreams", folder, "2022-07-14T00_01_01.203216501Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(sig -> streamFileProvider.get(sig.getStreamFilename()).block());
    }

    @Test
    void listStartsAtNextDay() throws Exception {
        localProperties.setDeleteAfterProcessing(false);
        var node = node(3);
        var folder = "record" + node.getNodeAccountId();
        var previous = createSignature("2022-07-13", "recordstreams", folder, "2022-07-13T23_59_59.304284003Z.rcd_sig");
        var expected = createSignature("2022-07-14", "recordstreams", folder, "2022-07-14T00_01_01.203216501Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.from(previous.getPath()))
                .collectList()
                .block();
        assertThat(sigs).hasSize(1).extracting(StreamFileData::getFilename).containsExactly(expected.getName());
        sigs.forEach(sig -> streamFileProvider.get(sig.getStreamFilename()).block());
        assertThat(Files.walk(dataPath)
                        .filter(p ->
                                p.toString().contains(node.getNodeAccountId().toString()))
                        .filter(p -> !p.toString().contains("sidecar"))
                        .filter(p -> p.toFile().isFile()))
                .hasSize(2);
    }

    @Test
    void listDeletesFiles() throws Exception {
        localProperties.setDeleteAfterProcessing(true);
        var node = node(3);
        createDefaultFileCopier().copy();
        var lastFilename = StreamFilename.from(Instant.now().toString().replace(':', '_') + ".rcd.gz");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        assertThat(Files.walk(dataPath)
                        .filter(p ->
                                p.toString().contains(node.getNodeAccountId().toString()))
                        .filter(p -> !p.toString().contains("sidecar"))
                        .noneMatch(p -> p.toFile().isFile()))
                .isTrue();
    }

    @Disabled("PathPrefix not supported")
    @Override
    void listWithPathPrefix() {
        // empty
    }

    @Disabled("PathPrefix not supported")
    @Override
    void listThenGetWithPathPrefix() {
        // empty
    }

    @SneakyThrows
    private File createSignature(String... paths) {
        var subPath = Path.of("", paths);
        var streamsDir = properties.getImporterProperties().getStreamPath();
        var file = streamsDir.resolve(subPath).toFile();
        file.getParentFile().mkdirs();
        file.createNewFile();
        return file;
    }
}
