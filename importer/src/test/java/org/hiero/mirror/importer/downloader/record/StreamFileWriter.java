// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureType.SHA_384_WITH_RSA;
import static org.hiero.mirror.importer.domain.StreamFilename.SIDECAR_FOLDER;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SidecarMetadata;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import jakarta.inject.Named;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.DigestOutputStream;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.reader.signature.ProtoSignatureFileReader;

@CustomLog
@Named
@RequiredArgsConstructor
public class StreamFileWriter {

    private static final StreamType TYPE = StreamType.RECORD;

    private final ImporterProperties properties;

    @SneakyThrows
    public void write(RecordFile recordFile, Collection<SigningConsensusNode> nodes) {
        var stopwatch = Stopwatch.createStarted();
        var algorithm = recordFile.getDigestAlgorithm();
        var fileDigest = DigestUtils.getDigest(algorithm.getName());
        var byteArrayOutputStream = new ByteArrayOutputStream();
        var sidecar = toSidecar(recordFile);

        try (var outputStream = new DigestOutputStream(new GZIPOutputStream(byteArrayOutputStream), fileDigest)) {

            var recordStreamFile = RecordStreamFile.newBuilder()
                    .addAllRecordStreamItems(recordFile.getItems().stream()
                            .map(this::toRecordStreamItem)
                            .toList())
                    .addAllSidecars(sidecar.metadata())
                    .setBlockNumber(recordFile.getIndex())
                    .setEndObjectRunningHash(toHashObject(Hex.decodeHex(recordFile.getHash())))
                    .setHapiProtoVersion(SemanticVersion.newBuilder()
                            .setMajor(recordFile.getHapiVersionMajor())
                            .setMinor(recordFile.getHapiVersionMinor())
                            .setPatch(recordFile.getHapiVersionPatch()))
                    .setStartObjectRunningHash(toHashObject(Hex.decodeHex(recordFile.getPreviousHash())))
                    .build();

            outputStream.write(Ints.toByteArray(recordFile.getVersion()));
            recordStreamFile.writeTo(outputStream);
        }

        var bytes = byteArrayOutputStream.toByteArray();
        var fileHash = fileDigest.digest();
        var metadataHash = calculateMetadataHash(recordFile);
        var filename = recordFile.getName();
        var sidecarName = filename.replace(".rcd.gz", "_01.rcd.gz");
        var base = properties.getStreamPath().resolve(TYPE.getPath());

        for (var node : nodes) {
            var nodePath = base.resolve(TYPE.getNodePrefix() + node.getNodeAccountId());

            if (sidecar.bytes() != null) {
                var sidecarPath = nodePath.resolve(SIDECAR_FOLDER);
                var sidecarFile = sidecarPath.resolve(sidecarName).toFile();
                sidecarPath.toFile().mkdirs();
                FileUtils.writeByteArrayToFile(sidecarFile, sidecar.bytes());
            }

            var recordFileFile = nodePath.resolve(filename).toFile();
            FileUtils.writeByteArrayToFile(recordFileFile, bytes);
            writeSignature(node.getPrivateKey(), recordFileFile, fileHash, metadataHash);
        }

        log.info("Wrote stream file {} with {} items in {}", filename, recordFile.getCount(), stopwatch);
    }

    @SneakyThrows
    private void writeSignature(PrivateKey privateKey, File recordFile, byte[] fileHash, byte[] metadataHash) {
        var filename = recordFile.getAbsolutePath().replace(".gz", "_sig");

        try (var outputStream = new BufferedOutputStream(new FileOutputStream(filename))) {
            var signatureFile = SignatureFile.newBuilder()
                    .setFileSignature(toSignatureObject(fileHash, privateKey))
                    .setMetadataSignature(toSignatureObject(metadataHash, privateKey))
                    .build();

            outputStream.write(ProtoSignatureFileReader.VERSION);
            signatureFile.writeTo(outputStream);
        }
    }

    @SneakyThrows
    private byte[] calculateMetadataHash(RecordFile recordFile) {
        var digest = DigestUtils.getDigest(recordFile.getDigestAlgorithm().getName());

        try (var outputStream = new DataOutputStream(new DigestOutputStream(NullOutputStream.INSTANCE, digest))) {
            outputStream.writeInt(recordFile.getVersion());
            outputStream.writeInt(recordFile.getHapiVersionMajor());
            outputStream.writeInt(recordFile.getHapiVersionMinor());
            outputStream.writeInt(recordFile.getHapiVersionPatch());
            outputStream.write(Hex.decodeHex(recordFile.getPreviousHash()));
            outputStream.write(Hex.decodeHex(recordFile.getHash()));
            outputStream.writeLong(recordFile.getIndex());
            return digest.digest();
        }
    }

    private HashObject toHashObject(final byte[] hash) {
        return HashObject.newBuilder()
                .setAlgorithm(HashAlgorithm.SHA_384)
                .setHash(DomainUtils.fromBytes(hash))
                .setLength(48)
                .build();
    }

    private RecordStreamItem toRecordStreamItem(RecordItem recordItem) {
        return RecordStreamItem.newBuilder()
                .setTransaction(recordItem.getTransaction())
                .setRecord(recordItem.getTransactionRecord())
                .build();
    }

    @SneakyThrows
    private Sidecar toSidecar(RecordFile recordFile) {
        var sidecarRecords = recordFile.getItems().stream()
                .flatMap(r -> r.getSidecarRecords().stream())
                .toList();

        if (sidecarRecords.isEmpty()) {
            return new Sidecar(List.of(), null);
        }

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var digest = DigestUtils.getDigest(recordFile.getDigestAlgorithm().getName());
        var sidecar =
                SidecarFile.newBuilder().addAllSidecarRecords(sidecarRecords).build();

        try (var outputStream = new GZIPOutputStream(new DigestOutputStream(byteArrayOutputStream, digest))) {
            sidecar.writeTo(outputStream);
        }

        var sidecarMetadata = SidecarMetadata.newBuilder()
                .addTypes(SidecarType.CONTRACT_ACTION)
                .addTypes(SidecarType.CONTRACT_BYTECODE)
                .addTypes(SidecarType.CONTRACT_STATE_CHANGE)
                .setHash(toHashObject(digest.digest()))
                .setId(1)
                .build();

        return new Sidecar(List.of(sidecarMetadata), byteArrayOutputStream.toByteArray());
    }

    @SneakyThrows
    private SignatureObject toSignatureObject(byte[] hash, PrivateKey privateKey) {
        var signature = Signature.getInstance(SHA_384_WITH_RSA.getAlgorithm(), SHA_384_WITH_RSA.getProvider());
        signature.initSign(privateKey);
        signature.update(hash);
        var signatureBytes = signature.sign();

        return SignatureObject.newBuilder()
                .setChecksum(101 - signatureBytes.length)
                .setHashObject(toHashObject(hash))
                .setLength(signatureBytes.length)
                .setSignature(DomainUtils.fromBytes(signatureBytes))
                .setType(SignatureType.SHA_384_WITH_RSA)
                .build();
    }

    private record Sidecar(List<SidecarMetadata> metadata, byte[] bytes) {}

    @lombok.Value
    public static class SigningConsensusNode implements ConsensusNode {

        @Delegate
        private final ConsensusNode consensusNode;

        private final PrivateKey privateKey;
    }
}
