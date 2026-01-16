// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.DOWNLOADED;
import static org.hiero.mirror.importer.domain.StreamFileSignature.SignatureStatus.VERIFIED;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.ConsensusNodeStub;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFileSignature.SignatureType;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeSignatureVerifierTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private Signature signer;

    private CommonDownloaderProperties commonDownloaderProperties;

    @Mock
    private ConsensusValidator consensusValidator;

    @InjectMocks
    private NodeSignatureVerifier nodeSignatureVerifier;

    @BeforeAll
    @SneakyThrows
    static void generateKeys() {
        KeyPair nodeKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        privateKey = nodeKeyPair.getPrivate();
        publicKey = nodeKeyPair.getPublic();
    }

    @BeforeEach
    @SneakyThrows
    void setup() {
        commonDownloaderProperties = new CommonDownloaderProperties(new ImporterProperties());
        commonDownloaderProperties.setConsensusRatio(
                BigDecimal.ONE.divide(BigDecimal.valueOf(3), 19, RoundingMode.DOWN));
        nodeSignatureVerifier = new NodeSignatureVerifier(consensusValidator);
        signer = Signature.getInstance("SHA384withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        consensusValidator.validate(any());
    }

    @Test
    void v2() {
        var signature = streamFileSignature();
        signature.setMetadataHash(null);
        signature.setMetadataHashSignature(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);

        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .containsOnly(VERIFIED);
    }

    @Test
    void v5() {
        var signature = streamFileSignature();
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .containsOnly(VERIFIED);
    }

    @Test
    void partialFailure() {
        var signature1 = streamFileSignature();
        var signature2 = streamFileSignature();
        var signature3 = streamFileSignature();
        signature3.setFileHashSignature(corruptSignature(signature3.getFileHashSignature()));
        var signatures = List.of(signature1, signature2, signature3);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .containsExactly(VERIFIED, VERIFIED, DOWNLOADED);
    }

    @Test
    void invalidFileSignature() {
        var signature = streamFileSignature();
        signature.setFileHashSignature(corruptSignature(signature.getFileHashSignature()));
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    @Test
    void invalidMetadataSignature() {
        var signature = streamFileSignature();
        signature.setMetadataHashSignature(corruptSignature(signature.getMetadataHashSignature()));
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    @Test
    void noSignatureType() {
        var signature = streamFileSignature();
        signature.setSignatureType(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    @Test
    void noFileHashSignature() {
        var signature = streamFileSignature();
        signature.setFileHashSignature(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    @Test
    void noFileHash() {
        var signature = streamFileSignature();
        signature.setFileHash(null);
        var signatures = List.of(signature);

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    @SneakyThrows
    @Test
    void signedWithWrongAlgorithm() {
        signer = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signer.initSign(privateKey);
        var signatures = List.of(streamFileSignature());

        nodeSignatureVerifier.verify(signatures);
        assertThat(signatures)
                .isNotEmpty()
                .extracting(StreamFileSignature::getStatus)
                .doesNotContain(VERIFIED);
    }

    private StreamFileSignature streamFileSignature() {
        var fileHash = TestUtils.generateRandomByteArray(48);
        var metadataHash = TestUtils.generateRandomByteArray(48);
        var node = ConsensusNodeStub.builder()
                .nodeAccountId(EntityId.of("0.0.3"))
                .publicKey(publicKey)
                .build();

        StreamFileSignature streamFileSignature = new StreamFileSignature();
        streamFileSignature.setFileHash(fileHash);
        streamFileSignature.setFileHashSignature(signHash(fileHash));
        streamFileSignature.setFilename(StreamFilename.EPOCH);
        streamFileSignature.setMetadataHash(metadataHash);
        streamFileSignature.setMetadataHashSignature(signHash(metadataHash));
        streamFileSignature.setNode(node);
        streamFileSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
        streamFileSignature.setStreamType(StreamType.RECORD);
        return streamFileSignature;
    }

    private byte[] corruptSignature(byte[] signature) {
        signature[0] = (byte) (signature[0] + 1);
        return signature;
    }

    private byte[] signHash(byte[] hash) {
        try {
            signer.update(hash);
            return signer.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
