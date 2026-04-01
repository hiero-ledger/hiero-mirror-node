// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hedera.cryptography.wraps.WRAPSVerificationKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TssVerifierTest {

    private static final TssTestArtifact TEST_ARTIFACT = loadTssTestArtifact();
    private static final byte[] WRAPS_VERIFICATION_KEY = WRAPSVerificationKey.getDefaultKey();

    private BlockProperties blockProperties;
    private TssVerifier tssVerifier;

    @Mock
    private LedgerRepository ledgerRepository;

    @BeforeEach
    void setup() {
        blockProperties = new BlockProperties(new ImporterProperties());
        tssVerifier = new TssVerifierImpl(blockProperties, ledgerRepository);
    }

    @Test
    void verifyWhenThrow() {
        // given
        when(ledgerRepository.findTopByOrderByConsensusTimestampDesc())
                .thenReturn(Optional.of(TEST_ARTIFACT.toLedger()));

        // when, then
        assertThatThrownBy(() -> tssVerifier.verify(
                        0, TestUtils.generateRandomByteArray(48), TEST_ARTIFACT.signatureWithWraps()))
                .isInstanceOf(SignatureVerificationException.class)
                .hasMessage("TSS signature verification failed for block 0");
        verify(ledgerRepository).findTopByOrderByConsensusTimestampDesc();
    }

    @Test
    void verifyWithLedgerSet() {
        // given
        tssVerifier.setLedger(TEST_ARTIFACT.toLedger());

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithSchnorr))
                .doesNotThrowAnyException();
        verify(ledgerRepository, never()).findTopByOrderByConsensusTimestampDesc();
    }

    @Test
    void verifyWithLedgerFromConfig() {
        // given
        final var ledgerProperties = LedgerProperties.builder()
                .historyProofVerificationKey(WRAPS_VERIFICATION_KEY)
                .ledgerId(TEST_ARTIFACT.ledgerId())
                .nodeContributions(TEST_ARTIFACT.nodeContributions())
                .build();
        blockProperties.setLedger(ledgerProperties);
        when(ledgerRepository.findTopByOrderByConsensusTimestampDesc()).thenReturn(Optional.empty());

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithSchnorr))
                .doesNotThrowAnyException();
        verify(ledgerRepository).findTopByOrderByConsensusTimestampDesc();
    }

    @Test
    void verifyWithLedgerFromDb() {
        // given
        when(ledgerRepository.findTopByOrderByConsensusTimestampDesc())
                .thenReturn(Optional.of(TEST_ARTIFACT.toLedger()));

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithSchnorr))
                .doesNotThrowAnyException();
        verify(ledgerRepository).findTopByOrderByConsensusTimestampDesc();
    }

    @Test
    void verifyWithoutLedger() {
        // given. when, then
        assertThatThrownBy(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ledger id, history proof verification key and node contributions not found");
        verify(ledgerRepository).findTopByOrderByConsensusTimestampDesc();
    }

    @SneakyThrows
    private static TssTestArtifact loadTssTestArtifact() {
        final var file = TestUtils.getResource("data/tss/tssTestArtifact.json");
        final var mapper = new ObjectMapper();
        final var module = new SimpleModule();
        module.addDeserializer(byte[].class, new HexByteArrayDeserializer());
        mapper.registerModule(module);
        return mapper.readValue(file, TssTestArtifact.class);
    }

    private record TssTestArtifact(
            byte[] ledgerId,
            byte[] message,
            List<LedgerNodeContribution> nodeContributions,
            byte[] signatureWithWraps,
            byte[] signatureWithSchnorr) {
        Ledger toLedger() {
            return Ledger.builder()
                    .historyProofVerificationKey(WRAPS_VERIFICATION_KEY)
                    .ledgerId(ledgerId)
                    .nodeContributions(nodeContributions)
                    .build();
        }
    }

    private static class HexByteArrayDeserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            return Hex.decode(p.getText());
        }
    }
}
