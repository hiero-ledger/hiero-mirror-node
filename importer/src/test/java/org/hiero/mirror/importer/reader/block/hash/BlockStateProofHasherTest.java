// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.hedera.hapi.block.stream.protoc.MerklePath;
import com.hedera.hapi.block.stream.protoc.SiblingNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class BlockStateProofHasherTest {

    private static final List<StateProofTestArtifact> TEST_ARTIFACTS = loadTestArtifacts();

    private final BlockStateProofHasher hasher = new BlockStateProofHasherImpl();

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideStateProofTestArtifact")
    void getHash(final long block, final StateProofTestArtifact testArtifact) {
        final byte[] actual = hasher.getRootHash(block, testArtifact.blockHash(), testArtifact.merklePaths());
        assertThat(actual).isEqualTo(testArtifact.expectedRootHash());
    }

    @Test
    void getHashThrowWhenMerklePathCountsNotThree() {
        assertThatThrownBy(() -> hasher.getRootHash(0, TestUtils.generateRandomByteArray(48), Collections.emptyList()))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Number of merkle paths in block 0's StateProof is not 3");
    }

    @Test
    void getHashThrowWhenLessThanMinSiblings() {
        // given
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setNextPathIndex(2)
                        .setTimestampLeaf(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .build()
                                .toByteString())
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(TestUtils.generateRandomByteArray(48)))
                        .setNextPathIndex(2)
                        .addSiblings(SiblingNode.newBuilder().build())
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(-1).build());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, TestUtils.generateRandomByteArray(48), merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's merkle path from the previous block root has less than 7 siblings");
    }

    @Test
    void getHashThrownWhenHashMismatch() {
        // given
        final var testArtifact = TEST_ARTIFACTS.getFirst();
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var expectedMessage = "Block 0 root hash mismatch: expected=%s, actual=%s"
                .formatted(Hex.encodeHexString(currentRootHash), Hex.encodeHexString(testArtifact.blockHash()));

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, testArtifact.merklePaths()))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage(expectedMessage);
    }

    @SneakyThrows
    private static List<StateProofTestArtifact> loadTestArtifacts() {
        final var file = TestUtils.getResource("data/stateproof/stateProofTestArtifact.json");
        final var mapper = new ObjectMapper();
        final var module = new SimpleModule();
        module.addDeserializer(byte[].class, new Base64ByteArrayDeserializer());
        module.addDeserializer(MerklePath.class, new MerklePathDeserializer());
        mapper.registerModule(module);
        return mapper.readValue(file, new TypeReference<>() {});
    }

    private static Stream<Arguments> provideStateProofTestArtifact() {
        return TEST_ARTIFACTS.stream().map(t -> Arguments.of(t.block(), t));
    }

    public static final class Base64ByteArrayDeserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Base64.getDecoder().decode(p.getValueAsString());
        }
    }

    private static final class MerklePathDeserializer extends JsonDeserializer<MerklePath> {

        @Override
        public MerklePath deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
            final var builder = MerklePath.newBuilder();
            JsonFormat.parser()
                    .ignoringUnknownFields()
                    .merge(p.readValueAsTree().toString(), builder);
            return builder.build();
        }
    }

    private record StateProofTestArtifact(
            long block, byte[] blockHash, byte[] expectedRootHash, List<MerklePath> merklePaths) {}
}
