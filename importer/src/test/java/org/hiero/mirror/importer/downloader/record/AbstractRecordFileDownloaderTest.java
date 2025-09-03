// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.downloader.AbstractLinkedStreamDownloaderTest;
import org.hiero.mirror.importer.downloader.Downloader;
import org.hiero.mirror.importer.downloader.DownloaderProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;
import org.hiero.mirror.importer.reader.record.CompositeRecordFileReader;
import org.hiero.mirror.importer.reader.record.ProtoRecordFileReader;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV1;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV2;
import org.hiero.mirror.importer.reader.record.RecordFileReaderImplV5;
import org.hiero.mirror.importer.reader.record.sidecar.SidecarFileReaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

abstract class AbstractRecordFileDownloaderTest extends AbstractLinkedStreamDownloaderTest<RecordFile> {

    protected Map<String, RecordFile> recordFileMap;

    protected SidecarProperties sidecarProperties;

    @Override
    @BeforeEach
    protected void beforeEach() {
        super.beforeEach();
        setupRecordFiles(getRecordFileMap());
    }

    protected abstract Map<String, RecordFile> getRecordFileMap();

    @Override
    protected DownloaderProperties getDownloaderProperties() {
        return new RecordDownloaderProperties(new BlockProperties(), commonDownloaderProperties);
    }

    @Override
    protected Downloader<RecordFile, RecordItem> getDownloader() {
        return getDownloader(s3AsyncClient);
    }

    private Downloader<RecordFile, RecordItem> getDownloader(S3AsyncClient s3AsyncClient) {

        var recordFileReader = new CompositeRecordFileReader(
                new RecordFileReaderImplV1(),
                new RecordFileReaderImplV2(),
                new RecordFileReaderImplV5(),
                new ProtoRecordFileReader());
        sidecarProperties = new SidecarProperties();
        sidecarProperties.setEnabled(true);
        var streamFileProvider =
                new S3StreamFileProvider(CommonProperties.getInstance(), commonDownloaderProperties, s3AsyncClient);
        return new RecordFileDownloader(
                consensusNodeService,
                (RecordDownloaderProperties) downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeProcessor,
                nodeSignatureVerifier,
                new SidecarFileReaderImpl(),
                sidecarProperties,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                recordFileReader);
    }

    protected void setupRecordFiles(Map<String, RecordFile> recordFileMap) {
        this.recordFileMap = recordFileMap;
        setTestFilesAndInstants(recordFileMap.keySet().stream().sorted().toList());
    }

    @Override
    protected void verifyStreamFiles(List<String> files, Consumer<RecordFile> extraAssert) {
        Consumer<RecordFile> recordAssert = recordFile -> {
            var expected = recordFileMap.get(recordFile.getName());
            assertAll(
                    () -> assertThat(recordFile)
                            .returns(expected.getSidecarCount(), RecordFile::getSidecarCount)
                            .returns(expected.getSize(), RecordFile::getSize),
                    () -> assertThat(recordFile.getSidecars())
                            .containsExactlyInAnyOrderElementsOf(expected.getSidecars())
                            .allMatch(sidecar -> sidecarProperties.isPersistBytes() ^ (sidecar.getBytes() == null)));
        };
        super.verifyStreamFiles(files, recordAssert.andThen(extraAssert));
    }

    @Test
    void timeoutList() {
        commonDownloaderProperties.setTimeout(Duration.ofMillis(200000L));
        var s3AsyncClient = mock(S3AsyncClient.class);
        var downloader = getDownloader(s3AsyncClient);

        when(s3AsyncClient.listObjectsV2(isA(ListObjectsV2Request.class)))
                .thenReturn(future())
                .thenReturn(future())
                .thenReturn(future());

        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);
        downloader.download();

        verifyUnsuccessful();
    }

    @SuppressWarnings("java:S2925")
    private <T> CompletableFuture<T> future() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
