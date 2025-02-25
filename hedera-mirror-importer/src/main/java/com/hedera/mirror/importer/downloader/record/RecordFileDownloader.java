// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.record;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimaps;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import com.hedera.mirror.importer.reader.record.ProtoRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.record.sidecar.SidecarFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ConditionalOnProperty(
        name = "hedera.mirror.importer.downloader.block.enabled",
        havingValue = "false",
        matchIfMissing = true)
@Named
public class RecordFileDownloader extends Downloader<RecordFile, RecordItem> {

    private static final String HASH_TYPE_SIDECAR = "Sidecar";

    private final SidecarFileReader sidecarFileReader;
    private final SidecarProperties sidecarProperties;

    @SuppressWarnings("java:S107")
    public RecordFileDownloader(
            ConsensusNodeService consensusNodeService,
            RecordDownloaderProperties downloaderProperties,
            ImporterProperties importerProperties,
            MeterRegistry meterRegistry,
            DateRangeCalculator dateRangeCalculator,
            NodeSignatureVerifier nodeSignatureVerifier,
            SidecarFileReader sidecarFileReader,
            SidecarProperties sidecarProperties,
            SignatureFileReader signatureFileReader,
            StreamFileNotifier streamFileNotifier,
            StreamFileProvider streamFileProvider,
            RecordFileReader streamFileReader) {
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
        this.sidecarFileReader = sidecarFileReader;
        this.sidecarProperties = sidecarProperties;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@recordDownloaderProperties.getFrequency().toMillis()}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected void onVerified(StreamFileData streamFileData, RecordFile recordFile, ConsensusNode node) {
        downloadSidecars(streamFileData.getStreamFilename(), recordFile, node);
        super.onVerified(streamFileData, recordFile, node);
    }

    @Override
    protected void setStreamFileIndex(RecordFile recordFile) {
        // Starting from the record stream file v6, the record file index is externalized as the block_number field of
        // the protobuf RecordStreamFile, so only set the record file index to be last + 1 if it's pre-v6.
        if (recordFile.getVersion() < ProtoRecordFileReader.VERSION) {
            super.setStreamFileIndex(recordFile);
        }
    }

    private void downloadSidecars(StreamFilename recordFilename, RecordFile recordFile, ConsensusNode node) {
        if (!sidecarProperties.isEnabled() || recordFile.getSidecars().isEmpty()) {
            return;
        }

        var acceptedTypes =
                sidecarProperties.getTypes().stream().map(Enum::ordinal).collect(Collectors.toSet());

        var records = Flux.fromIterable(recordFile.getSidecars())
                .filter(sidecar ->
                        acceptedTypes.isEmpty() || sidecar.getTypes().stream().anyMatch(acceptedTypes::contains))
                .flatMap(sidecar -> getSidecar(node, recordFilename, sidecar))
                .flatMapIterable(SidecarFile::getRecords)
                .filter(t -> acceptedTypes.isEmpty() || acceptedTypes.contains(getSidecarType(t)))
                .collect(Multimaps.toMultimap(
                        TransactionSidecarRecord::getConsensusTimestamp,
                        Function.identity(),
                        ArrayListMultimap::create))
                .block();

        recordFile.getItems().forEach(recordItem -> {
            var timestamp = recordItem.getTransactionRecord().getConsensusTimestamp();
            if (records.containsKey(timestamp)) {
                recordItem.setSidecarRecords(records.get(timestamp));
            }
        });
    }

    private Mono<SidecarFile> getSidecar(ConsensusNode node, StreamFilename recordFilename, SidecarFile sidecar) {
        var sidecarFilename = StreamFilename.from(recordFilename, sidecar.getName());
        return streamFileProvider.get(node, sidecarFilename).map(streamFileData -> {
            sidecarFileReader.read(sidecar, streamFileData);

            if (!Arrays.equals(sidecar.getHash(), sidecar.getActualHash())) {
                throw new HashMismatchException(
                        sidecar.getName(), sidecar.getHash(), sidecar.getActualHash(), HASH_TYPE_SIDECAR);
            }

            if (downloaderProperties.isWriteFiles()) {
                var streamPath = importerProperties.getStreamPath();
                Utility.archiveFile(streamFileData.getFilePath(), sidecar.getBytes(), streamPath);
            }

            if (!sidecarProperties.isPersistBytes()) {
                sidecar.setBytes(null);
            }

            return sidecar;
        });
    }

    private int getSidecarType(TransactionSidecarRecord transactionSidecarRecord) {
        return switch (transactionSidecarRecord.getSidecarRecordsCase()) {
            case ACTIONS -> SidecarType.CONTRACT_ACTION_VALUE;
            case BYTECODE -> SidecarType.CONTRACT_BYTECODE_VALUE;
            case STATE_CHANGES -> SidecarType.CONTRACT_STATE_CHANGE_VALUE;
            default -> {
                Utility.handleRecoverableError(
                        "Unknown sidecar transaction record type at {}: {}",
                        transactionSidecarRecord.getConsensusTimestamp(),
                        transactionSidecarRecord.getSidecarRecordsCase());
                yield SidecarType.SIDECAR_TYPE_UNKNOWN_VALUE;
            }
        };
    }
}
