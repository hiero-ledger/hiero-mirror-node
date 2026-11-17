// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.reader.block.BlockStreamTestUtils.BLOCK_STREAM_HASH_SIZE;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TssVerifierTest {

    private static final TssTestArtifact TEST_ARTIFACT = loadTssTestArtifact();

    private TssVerifier tssVerifier;

    @Mock
    private LedgerRepository ledgerRepository;

    @BeforeEach
    void setup() {
        tssVerifier = new TssVerifierImpl(ledgerRepository);
    }

    @Test
    void concurrentGetLedgerCallsOnLedgerSetOnce() throws Exception {
        // given
        when(ledgerRepository.findLatest()).thenAnswer(invocation -> {
            Thread.sleep(50); // widen the race window
            return Optional.of(TEST_ARTIFACT.toLedger());
        });

        // when
        final var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        final var latch = new java.util.concurrent.CountDownLatch(2);
        final Runnable task = () -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps);
        };
        final var f1 = pool.submit(task);
        final var f2 = pool.submit(task);
        f1.get();
        f2.get();
        pool.shutdown();

        // then
        verify(ledgerRepository, times(1)).findLatest();
    }

    @Test
    void verifyWhenThrow() {
        // given
        when(ledgerRepository.findLatest()).thenReturn(Optional.of(TEST_ARTIFACT.toLedger()));

        // when, then
        assertThatThrownBy(() -> tssVerifier.verify(
                        0,
                        TestUtils.generateRandomByteArray(BLOCK_STREAM_HASH_SIZE),
                        TEST_ARTIFACT.signatureWithWraps()))
                .isInstanceOf(SignatureVerificationException.class)
                .hasMessage("TSS signature verification failed for block 0");
        verify(ledgerRepository).findLatest();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, 0
            true, 1
            """)
    void verifyWithLedgerSet(final boolean fromConfig, final int expectedDbCalls) {
        // given
        tssVerifier.setLedger(TEST_ARTIFACT.toLedger(), fromConfig);

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        verify(ledgerRepository, times(expectedDbCalls)).findLatest();
    }

    @Test
    void verifyWithOnChainLedger() {
        // given
        tssVerifier.setLedger(TEST_ARTIFACT.toLedger(), false);
        tssVerifier.setLedger(Ledger.builder().ledgerId(new byte[64]).build(), true);

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        verify(ledgerRepository, never()).findLatest();
    }

    @Test
    void verifyWithLedgerFromDb() {
        // given
        when(ledgerRepository.findLatest()).thenReturn(Optional.of(TEST_ARTIFACT.toLedger()));
        tssVerifier.setLedger(Ledger.builder().ledgerId(new byte[64]).build(), true);

        // when, then
        assertThatCode(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .doesNotThrowAnyException();
        verify(ledgerRepository).findLatest();
    }

    @Test
    void verifyWithoutLedger() {
        // given. when, then
        assertThatThrownBy(() -> tssVerifier.verify(0, TEST_ARTIFACT.message, TEST_ARTIFACT.signatureWithWraps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ledger id not found");
        verify(ledgerRepository).findLatest();
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

    private record TssTestArtifact(byte[] ledgerId, byte[] message, byte[] signatureWithWraps) {
        Ledger toLedger() {
            return Ledger.builder().ledgerId(ledgerId).build();
        }
    }

    private static class HexByteArrayDeserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            return Hex.decode(p.getText());
        }
    }
}
