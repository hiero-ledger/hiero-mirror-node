// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import static org.hiero.mirror.common.domain.StreamType.RECORD;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.getHashBytes;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SidecarMetadata;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;
import org.hiero.mirror.importer.util.Utility;

@RequiredArgsConstructor
abstract class AbstractRecordFileItemReader implements RecordFileItemReader {

    private static final String HASH_TYPE_SIDECAR = "Sidecar";

    private final SidecarProperties sidecarProperties;

    @Override
    public RecordFile read(final RecordFileItem recordFileItem, final int version) {
        try (final var bos = new ByteArrayOutputStream();
                final var hos = new DigestOutputStream(bos, createSha384Digest());
                final var dos = new DataOutputStream(hos)) {
            final var context = new Context(
                    dos,
                    hos.getMessageDigest(),
                    hos,
                    new ArrayList<>(),
                    new RecordFile(),
                    recordFileItem,
                    new HashMap<>());

            dos.writeInt(version);
            onHeader(context);
            onBody(context);
            onEnd(context);

            // Set other common fields. Note metadata hash calculation is skipped because there is only signature for
            // file hash in SignedRecordFileProof
            final var items = context.items();
            final var recordFile = context.recordFile();
            final byte[] bytes = bos.toByteArray();
            recordFile.setBytes(bytes);
            recordFile.setConsensusEnd(items.getLast().getConsensusTimestamp());
            recordFile.setConsensusStart(items.getFirst().getConsensusTimestamp());
            recordFile.setCount((long) items.size());
            recordFile.setDigestAlgorithm(DigestAlgorithm.SHA_384);
            recordFile.setFileHash(Hex.encodeHexString(context.fileDigest().digest()));
            recordFile.setIndex(recordFileItem.getRecordFileContents().getBlockNumber());
            recordFile.setItems(items);
            recordFile.setSize(bytes.length);
            recordFile.setVersion(version);
            finalize(recordFile);

            return recordFile;
        } catch (final IOException ex) {
            throw new BlockStreamException("Failed to parse RecordFileItem", ex);
        }
    }

    private static long getConsensusTimestamp(final RecordStreamItem recordStreamItem) {
        return DomainUtils.timestampInNanosMax(recordStreamItem.getRecord().getConsensusTimestamp());
    }

    protected void finalize(final RecordFile recordFile) {
        final long consensusEnd = recordFile.getConsensusEnd();
        for (final var sidecar : recordFile.getSidecars()) {
            sidecar.setConsensusEnd(consensusEnd);
        }
    }

    protected void onBody(final Context context) throws IOException {
        readSidecars(context);

        final var recordFileItem = context.recordFileItem();
        final var amendments = recordFileItem.getAmendmentsList();
        final var recordStreamItems = recordFileItem.getRecordFileContents().getRecordStreamItemsList();

        if (amendments.isEmpty()) {
            // Most WRBs don't have amendments, shortcut for performance gain
            for (final var recordStreamItem : recordStreamItems) {
                onRecordStreamItem(context, recordStreamItem);
            }

            return;
        }

        int amendmentIndex = 0;
        for (final var recordStreamItem : recordStreamItems) {
            final long recordTimestamp = getConsensusTimestamp(recordStreamItem);

            // Insert any amendments with earlier timestamps (additions)
            while (amendmentIndex < amendments.size()
                    && getConsensusTimestamp(amendments.get(amendmentIndex)) < recordTimestamp) {
                onRecordStreamItem(context, amendments.get(amendmentIndex++));
            }

            if (amendmentIndex < amendments.size()
                    && getConsensusTimestamp(amendments.get(amendmentIndex)) == recordTimestamp) {
                // Replace if the consensus timestamps are the same
                onRecordStreamItem(context, amendments.get(amendmentIndex++));
            } else {
                onRecordStreamItem(context, recordStreamItem);
            }
        }

        for (; amendmentIndex < amendments.size(); amendmentIndex++) {
            onRecordStreamItem(context, amendments.get(amendmentIndex));
        }
    }

    protected void onEnd(final Context context) throws IOException {
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        if (recordStreamFile.hasEndObjectRunningHash()) {
            context.recordFile().setHash(Hex.encodeHexString(getHashBytes(recordStreamFile.getEndObjectRunningHash())));
        }
    }

    protected void onHeader(final Context context) throws IOException {
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
        final var recordFile = context.recordFile();
        recordFile.setHapiVersionMajor(hapiProtoVersion.getMajor());
        recordFile.setHapiVersionMinor(hapiProtoVersion.getMinor());
        recordFile.setHapiVersionPatch(hapiProtoVersion.getPatch());
        recordFile.setSoftwareVersionMajor(hapiProtoVersion.getMajor());
        recordFile.setSoftwareVersionMinor(hapiProtoVersion.getMinor());
        recordFile.setSoftwareVersionPatch(hapiProtoVersion.getPatch());

        final var creationTime = context.recordFileItem().getCreationTime();
        final var fileInstant = Instant.ofEpochSecond(creationTime.getSeconds(), creationTime.getNanos());
        final var filename = StreamFilename.getFilename(RECORD, DATA, fileInstant);
        recordFile.setName(filename);

        recordFile.setPreviousHash(Hex.encodeHexString(getHashBytes(recordStreamFile.getStartObjectRunningHash())));
    }

    protected void onRecordStreamItem(final Context context, final RecordStreamItem recordStreamItem)
            throws IOException {
        final var items = context.items();
        final var recordItem = RecordItem.builder()
                .hapiVersion(context.recordFile().getHapiVersion())
                .previous(items.isEmpty() ? null : items.getLast())
                .transactionRecord(recordStreamItem.getRecord())
                .transaction(recordStreamItem.getTransaction())
                .transactionIndex(items.size())
                .build();
        recordItem.setSidecarRecords(
                context.sidecarRecords().getOrDefault(recordItem.getConsensusTimestamp(), Collections.emptyList()));
        items.add(recordItem);
    }

    private boolean isSidecarFileAccepted(final SidecarMetadata metadata) {
        final var acceptedTypes = sidecarProperties.getTypeOrdinals();
        if (acceptedTypes.isEmpty()) {
            return true;
        }

        for (final int type : metadata.getTypesValueList()) {
            if (acceptedTypes.contains(type)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSidecarRecordAccepted(final TransactionSidecarRecord record) {
        final var acceptedTypes = sidecarProperties.getTypeOrdinals();
        if (acceptedTypes.isEmpty()) {
            return true;
        }

        final int type =
                switch (record.getSidecarRecordsCase()) {
                    case ACTIONS -> SidecarType.CONTRACT_ACTION_VALUE;
                    case BYTECODE -> SidecarType.CONTRACT_BYTECODE_VALUE;
                    case STATE_CHANGES -> SidecarType.CONTRACT_STATE_CHANGE_VALUE;
                    default -> {
                        Utility.handleRecoverableError(
                                "Unknown sidecar transaction record type at {}: {}",
                                record.getConsensusTimestamp(),
                                record.getSidecarRecordsCase());
                        yield SidecarType.SIDECAR_TYPE_UNKNOWN_VALUE;
                    }
                };
        return acceptedTypes.contains(type);
    }

    private void readSidecars(final Context context) {
        final var recordFileItem = context.recordFileItem();
        final var sidecarMetadataList = recordFileItem.getRecordFileContents().getSidecarsList();
        final var sidecarFileContentsList = recordFileItem.getSidecarFileContentsList();
        if (sidecarMetadataList.isEmpty()) {
            return;
        }

        final var digest = createSha384Digest();
        final var recordFile = context.recordFile();
        final var sidecars = new ArrayList<SidecarFile>(sidecarMetadataList.size());
        final var sidecarRecords = context.sidecarRecords();
        final var streamFilename = StreamFilename.from(recordFile.getName());
        for (final var metadata : sidecarMetadataList) {
            final int sidecarId = metadata.getId();
            if (sidecarId < 1 || sidecarId > sidecarFileContentsList.size()) {
                Utility.handleRecoverableError("Missing sidecar file content for id %d of wrapped record file %s"
                        .formatted(sidecarId, recordFile.getName()));
                continue;
            }

            final var fileContent = sidecarFileContentsList.get(sidecarId - 1);
            final byte[] bytes = fileContent.toByteArray();
            final byte[] expectedHash = getHashBytes(metadata.getHash());
            final var sidecarFilename = streamFilename.getSidecarFilename(metadata.getId());
            sidecars.add(SidecarFile.builder()
                    .bytes(sidecarProperties.isPersistBytes() ? bytes : null)
                    .count(fileContent.getSidecarRecordsCount())
                    .hashAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(expectedHash)
                    .index(sidecarId)
                    .name(sidecarFilename)
                    .size(bytes.length)
                    .types(metadata.getTypesValueList())
                    .build());

            if (!sidecarProperties.isEnabled() || !isSidecarFileAccepted(metadata)) {
                // Skip remaining steps if sidecar is disabled or all sidecar transaction record types in the file are
                // excluded. Note the sidecar object is still added to the record file and thus persisted to database.
                continue;
            }

            final byte[] actualHash = digest.digest(bytes);
            if (!Arrays.equals(actualHash, expectedHash)) {
                throw new HashMismatchException(sidecarFilename, expectedHash, actualHash, HASH_TYPE_SIDECAR);
            }

            for (final var record : fileContent.getSidecarRecordsList()) {
                if (!isSidecarRecordAccepted(record)) {
                    continue;
                }

                final long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());
                sidecarRecords
                        .computeIfAbsent(consensusTimestamp, _ -> new ArrayList<>())
                        .add(record);
            }
        }

        recordFile.setSidecarCount(sidecars.size());
        recordFile.setSidecars(sidecars);
    }

    protected record Context(
            DataOutputStream dos,
            MessageDigest fileDigest,
            DigestOutputStream hos,
            List<RecordItem> items,
            RecordFile recordFile,
            RecordFileItem recordFileItem,
            Map<Long, List<TransactionSidecarRecord>> sidecarRecords) {}
}
