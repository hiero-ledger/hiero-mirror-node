// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.CONSENSUS_REACHED;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.DOWNLOADED;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.NOT_FOUND;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.VERIFIED;
import static org.hiero.mirror.importer.downloader.Downloader.SIGNATURE_VERIFICATION_METRIC_NAME;
import static org.hiero.mirror.importer.downloader.Downloader.STREAM_VERIFICATION_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.TransientProviderException;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.reader.StreamFileReader;
import org.hiero.mirror.importer.reader.signature.SignatureFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DownloaderTest {

    @Mock
    private ConsensusNodeService consensusNodeService;

    @Mock
    private DateRangeCalculator dateRangeCalculator;

    @Mock
    private NodeSignatureVerifier nodeSignatureVerifier;

    @Mock
    private SignatureFileReader signatureFileReader;

    @Mock
    private StreamFileNotifier streamFileNotifier;

    @Mock
    private StreamFileProvider streamFileProvider;

    @Mock
    private StreamFileReader<RecordFile, ?> streamFileReader;

    @Mock
    private Downloader<RecordFile, ?> mockDownloader;

    private ImporterProperties importerProperties;
    private RecordDownloaderProperties downloaderProperties;
    private MeterRegistry meterRegistry;
    private TestDownloader downloader;

    @BeforeEach
    void setUp() {
        importerProperties = new ImporterProperties();
        var commonProperties = new CommonDownloaderProperties(importerProperties);
        commonProperties.setDownloadRatio(BigDecimal.ONE);
        downloaderProperties = new RecordDownloaderProperties(commonProperties);
        meterRegistry = new SimpleMeterRegistry();

        downloader = new TestDownloader(
                consensusNodeService,
                downloaderProperties,
                importerProperties,
                meterRegistry,
                dateRangeCalculator,
                nodeSignatureVerifier,
                signatureFileReader,
                streamFileNotifier,
                streamFileProvider,
                streamFileReader);
    }

    @Test
    void streamFileSignatureMultiMapExpectNoDuplicate() {
        when(mockDownloader.getStreamFileSignatureMultiMap()).thenCallRealMethod();
        var signatureFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var multimap = mockDownloader.getStreamFileSignatureMultiMap();
        var streamFileSignature = streamFileSignature(1L, signatureFilename);
        multimap.put(signatureFilename, streamFileSignature);
        multimap.put(signatureFilename, streamFileSignature);
        multimap.put(signatureFilename, streamFileSignature(1L, signatureFilename));
        multimap.put(signatureFilename, streamFileSignature(2L, signatureFilename));

        var actual = multimap.get(signatureFilename);

        assertThat(actual)
                .map(StreamFileSignature::getNode)
                .map(ConsensusNode::getNodeId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("Successful signature verification records CONSENSUS_REACHED and success timer")
    void downloadSuccessfulVerification() {
        // given
        var node1 = node(1L);
        var node2 = node(2L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1, node2));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename, hash);
        var sig2 = streamFileSignature(node2, sigFilename, hash);
        var sigData1 = StreamFileData.from(sigFilename.getFilename(), new byte[] {1});
        var sigData2 = StreamFileData.from(sigFilename.getFilename(), new byte[] {2});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1));
        when(streamFileProvider.list(node2, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData2));
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doAnswer(invocation -> {
                    Collection<StreamFileSignature> signatures = invocation.getArgument(0);
                    signatures.forEach(s -> s.setStatus(CONSENSUS_REACHED));
                    return null;
                })
                .when(nodeSignatureVerifier)
                .verify(any());

        var dataFilename = sig1.getDataFilename();
        var dataFileBytes = new byte[] {10, 20};
        var dataFileData = StreamFileData.from(dataFilename.getFilename(), dataFileBytes);
        var recordFile = recordFile(dataFilename, hash);

        when(streamFileProvider.get(dataFilename)).thenReturn(Mono.just(dataFileData));
        when(streamFileReader.read(dataFileData)).thenReturn(recordFile);

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        key(1L, CONSENSUS_REACHED), 1.0,
                        key(2L, CONSENSUS_REACHED), 1.0));
        assertThat(streamVerificationCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(true, 1L));
        assertThat(streamVerificationTimer(true).totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isPositive();
    }

    @Test
    @DisplayName("Consensus failure records non-consensus signature statuses and failure timer")
    void downloadConsensusFailure() {
        // given
        var node1 = node(1L);
        var node2 = node(2L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1, node2));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename, hash);
        var sig2 = streamFileSignature(node2, sigFilename, hash);
        var sigData1 = StreamFileData.from(sigFilename.getFilename(), new byte[] {1});
        var sigData2 = StreamFileData.from(sigFilename.getFilename(), new byte[] {2});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1));
        when(streamFileProvider.list(node2, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData2));
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doThrow(new SignatureVerificationException("Consensus not reached"))
                .when(nodeSignatureVerifier)
                .verify(any());

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        key(1L, DOWNLOADED), 1.0,
                        key(2L, DOWNLOADED), 1.0));
        assertThat(streamVerificationCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(false, 1L));
        assertThat(streamVerificationTimer(false).totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isPositive();
    }

    @Test
    @DisplayName("Consensus failure with verified, unverified, and missing nodes records all statuses")
    void downloadConsensusFailureWithMissingAndUnverifiedNodes() {
        // given
        var node1 = node(1L);
        var node2 = node(2L);
        var node3 = node(3L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1, node2, node3));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename, hash);
        var sig2 = streamFileSignature(node2, sigFilename, hash);
        var sigData1 = StreamFileData.from(sigFilename.getFilename(), new byte[] {1});
        var sigData2 = StreamFileData.from(sigFilename.getFilename(), new byte[] {2});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1));
        when(streamFileProvider.list(node2, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData2));
        when(streamFileProvider.list(node3, StreamFilename.EPOCH)).thenReturn(Flux.empty());
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doAnswer(invocation -> {
                    Collection<StreamFileSignature> signatures = invocation.getArgument(0);
                    signatures.stream()
                            .filter(s -> s.getNode().getNodeId() == 1L)
                            .forEach(s -> s.setStatus(VERIFIED));
                    throw new SignatureVerificationException("Consensus not reached");
                })
                .when(nodeSignatureVerifier)
                .verify(any());

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        key(1L, VERIFIED), 1.0,
                        key(2L, DOWNLOADED), 1.0,
                        key(3L, NOT_FOUND), 1.0));
        assertThat(streamVerificationCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(false, 1L));
    }

    @Test
    @DisplayName("Data file download failure records CONSENSUS_REACHED signatures and failure timer")
    void downloadDataFileVerificationFailure() {
        // given
        var node1 = node(1L);
        var node2 = node(2L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1, node2));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename, hash);
        var sig2 = streamFileSignature(node2, sigFilename, hash);
        var sigData1 = StreamFileData.from(sigFilename.getFilename(), new byte[] {1});
        var sigData2 = StreamFileData.from(sigFilename.getFilename(), new byte[] {2});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1));
        when(streamFileProvider.list(node2, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData2));
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doAnswer(invocation -> {
                    Collection<StreamFileSignature> signatures = invocation.getArgument(0);
                    signatures.forEach(s -> s.setStatus(CONSENSUS_REACHED));
                    return null;
                })
                .when(nodeSignatureVerifier)
                .verify(any());

        var dataFilename = sig1.getDataFilename();
        when(streamFileProvider.get(dataFilename))
                .thenReturn(Mono.error(new TransientProviderException(new RuntimeException("Not found"))));

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        key(1L, CONSENSUS_REACHED), 1.0,
                        key(2L, CONSENSUS_REACHED), 1.0));
        assertThat(streamVerificationCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(false, 1L));
    }

    @Test
    @DisplayName("Empty signature-file collection records no metrics")
    void downloadEmptySignatureFiles() {
        // given
        var node1 = node(1L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());
        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.empty());

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts()).isEmpty();
        assertThat(streamVerificationCounts()).isEmpty();
    }

    @Test
    @DisplayName("Multiple files where first fails consensus and second succeeds records both outcomes")
    void downloadMultipleFilesFirstFailsConsensusSecondSucceeds() {
        // given
        var node1 = node(1L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename1 = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var sigFilename2 = StreamFilename.from("2022-01-01T00_00_05Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename1, hash);
        var sig2 = streamFileSignature(node1, sigFilename2, hash);
        var sigData1 = StreamFileData.from(sigFilename1.getFilename(), new byte[] {1});
        var sigData2 = StreamFileData.from(sigFilename2.getFilename(), new byte[] {2});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1, sigData2));
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doAnswer(invocation -> {
                    Collection<StreamFileSignature> signatures = invocation.getArgument(0);
                    var first = signatures.iterator().next();
                    if (first.getFilename().equals(sigFilename1)) {
                        throw new SignatureVerificationException("File 1 consensus failure");
                    }
                    signatures.forEach(s -> s.setStatus(CONSENSUS_REACHED));
                    return null;
                })
                .when(nodeSignatureVerifier)
                .verify(any());

        var dataFilename2 = sig2.getDataFilename();
        var dataFileData2 = StreamFileData.from(dataFilename2.getFilename(), new byte[] {20});
        var recordFile2 = recordFile(dataFilename2, hash);
        when(streamFileProvider.get(dataFilename2)).thenReturn(Mono.just(dataFileData2));
        when(streamFileReader.read(dataFileData2)).thenReturn(recordFile2);

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        key(1L, DOWNLOADED), 1.0,
                        key(1L, CONSENSUS_REACHED), 1.0));
        assertThat(streamVerificationCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        false, 1L,
                        true, 1L));
    }

    @Test
    @DisplayName("Unexpected runtime exception in verifier still records failure metrics")
    void downloadUnexpectedExceptionRecordsFailureMetrics() {
        // given
        var node1 = node(1L);
        when(consensusNodeService.getNodes()).thenReturn(List.of(node1));
        when(dateRangeCalculator.getLastStreamFile(StreamType.RECORD)).thenReturn(Optional.empty());

        var sigFilename = StreamFilename.from("2022-01-01T00_00_00Z.rcd_sig");
        var hash = new byte[] {1, 2, 3};
        var sig1 = streamFileSignature(node1, sigFilename, hash);
        var sigData1 = StreamFileData.from(sigFilename.getFilename(), new byte[] {1});

        when(streamFileProvider.list(node1, StreamFilename.EPOCH)).thenReturn(Flux.just(sigData1));
        when(signatureFileReader.read(any())).thenAnswer(invocation -> {
            StreamFileData data = invocation.getArgument(0);
            return streamFileSignature(null, data.getStreamFilename(), hash);
        });

        doThrow(new RuntimeException("Unexpected I/O failure"))
                .when(nodeSignatureVerifier)
                .verify(any());

        // when
        downloader.download();

        // then
        assertThat(signatureStatusCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(key(1L, DOWNLOADED), 1.0));
        assertThat(streamVerificationCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(false, 1L));
    }

    private ConsensusNode node(long nodeId) {
        return ConsensusNodeStub.builder().nodeId(nodeId).build();
    }

    private StreamFileSignature streamFileSignature(long nodeId, StreamFilename streamFilename) {
        return streamFileSignature(node(nodeId), streamFilename, new byte[] {1, 2, 3});
    }

    private StreamFileSignature streamFileSignature(ConsensusNode node, StreamFilename filename, byte[] fileHash) {
        return StreamFileSignature.builder()
                .fileHash(fileHash)
                .filename(filename)
                .metadataHash(fileHash)
                .node(node)
                .status(DOWNLOADED)
                .streamType(StreamType.RECORD)
                .build();
    }

    private RecordFile recordFile(StreamFilename filename, byte[] fileHash) {
        var file = new RecordFile();
        file.setBytes(new byte[] {1});
        file.setConsensusEnd(200L);
        file.setConsensusStart(100L);
        file.setCount(1L);
        file.setFileHash(DomainUtils.bytesToHex(fileHash));
        file.setHash(DomainUtils.bytesToHex(fileHash));
        file.setIndex(1L);
        file.setLoadEnd(200L);
        file.setLoadStart(100L);
        file.setMetadataHash(DomainUtils.bytesToHex(fileHash));
        file.setName(filename.getFilename());
        file.setPreviousHash("");
        file.setVersion(6);
        return file;
    }

    private static String key(final long nodeId, final SignatureStatus status) {
        return nodeId + ":" + status;
    }

    private Map<String, Double> signatureStatusCounts() {
        return meterRegistry
                .find(SIGNATURE_VERIFICATION_METRIC_NAME)
                .tag("type", StreamType.RECORD.toString())
                .counters()
                .stream()
                .collect(Collectors.toMap(
                        c -> key(
                                Long.parseLong(c.getId().getTag("node")),
                                SignatureStatus.valueOf(c.getId().getTag("status"))),
                        Counter::count));
    }

    private Map<Boolean, Long> streamVerificationCounts() {
        return meterRegistry
                .find(STREAM_VERIFICATION_METRIC_NAME)
                .tag("type", StreamType.RECORD.toString())
                .timers()
                .stream()
                .collect(Collectors.toMap(t -> Boolean.parseBoolean(t.getId().getTag("success")), Timer::count));
    }

    private Timer streamVerificationTimer(boolean success) {
        return meterRegistry
                .find(STREAM_VERIFICATION_METRIC_NAME)
                .tag("type", StreamType.RECORD.toString())
                .tag("success", String.valueOf(success))
                .timer();
    }

    private static class TestDownloader extends Downloader<RecordFile, RecordItem> {
        public TestDownloader(
                ConsensusNodeService consensusNodeService,
                DownloaderProperties downloaderProperties,
                ImporterProperties importerProperties,
                MeterRegistry meterRegistry,
                DateRangeCalculator dateRangeCalculator,
                NodeSignatureVerifier nodeSignatureVerifier,
                SignatureFileReader signatureFileReader,
                StreamFileNotifier streamFileNotifier,
                StreamFileProvider streamFileProvider,
                StreamFileReader<RecordFile, ?> streamFileReader) {
            super(
                    consensusNodeService,
                    downloaderProperties,
                    importerProperties,
                    meterRegistry,
                    dateRangeCalculator,
                    nodeSignatureVerifier,
                    signatureFileReader,
                    streamFileNotifier,
                    streamFileProvider,
                    streamFileReader);
        }

        @Override
        public void download() {
            downloadNextBatch();
        }
    }
}
