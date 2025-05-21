// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.hiero.mirror.importer.reader.signature.ProtoSignatureFileReader.VERSION;

import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNode;

@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@ToString(exclude = {"bytes", "fileHash", "fileHashSignature", "metadataHash", "metadataHashSignature"})
public class StreamFileSignature implements Comparable<StreamFileSignature> {

    private static final String COMPRESSED_EXTENSION = ".gz";
    private static final Comparator<StreamFileSignature> COMPARATOR =
            Comparator.comparing(StreamFileSignature::getNode).thenComparing(StreamFileSignature::getFilename);

    private byte[] bytes;
    private byte[] fileHash;
    private byte[] fileHashSignature;

    @EqualsAndHashCode.Include
    private StreamFilename filename;

    private byte[] metadataHash;
    private byte[] metadataHashSignature;

    @EqualsAndHashCode.Include
    private ConsensusNode node;

    private SignatureType signatureType;

    @Builder.Default
    private SignatureStatus status = SignatureStatus.DOWNLOADED;

    private StreamType streamType;
    private byte version;

    @Override
    public int compareTo(StreamFileSignature other) {
        return COMPARATOR.compare(this, other);
    }

    public StreamFilename getDataFilename() {
        String dataFilename = filename.getFilename().replace(StreamType.SIGNATURE_SUFFIX, "");

        if (hasCompressedDataFile() && !dataFilename.endsWith(COMPRESSED_EXTENSION)) {
            dataFilename += COMPRESSED_EXTENSION;
        }

        return StreamFilename.from(filename, dataFilename);
    }

    public String getFileHashAsHex() {
        return DomainUtils.bytesToHex(fileHash);
    }

    public String getMetadataHashAsHex() {
        return DomainUtils.bytesToHex(metadataHash);
    }

    private boolean hasCompressedDataFile() {
        return version >= VERSION || filename.isCompressed();
    }

    public enum SignatureStatus {
        DOWNLOADED, // Signature has been downloaded and parsed but not verified
        VERIFIED, // Signature has been verified against the node's public key
        CONSENSUS_REACHED, // Signature verification consensus reached by a node count greater than the consensusRatio
        NOT_FOUND, // Signature for given node was not found for download
    }

    @Getter
    @RequiredArgsConstructor
    public enum SignatureType {
        SHA_384_WITH_RSA(1, 384, "SHA384withRSA", "SunRsaSign");

        private final int fileMarker;
        private final int maxLength;
        private final String algorithm;
        private final String provider;

        public static SignatureType of(int signatureTypeIndicator) {
            for (SignatureType signatureType : SignatureType.values()) {
                if (signatureType.fileMarker == signatureTypeIndicator) {
                    return signatureType;
                }
            }
            return null;
        }
    }
}
