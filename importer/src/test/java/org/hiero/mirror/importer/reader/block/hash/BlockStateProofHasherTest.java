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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class BlockStateProofHasherTest {

    private static final int MIN_SIBLING_COUNT = 4;

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
        final byte[] rootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setNextPathIndex(2)
                        .setTimestampLeaf(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .build()
                                .toByteString())
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(rootHash))
                        .setNextPathIndex(2)
                        .addSiblings(SiblingNode.newBuilder().build())
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(-1).build());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, rootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's block contents merkle path has less than 4 siblings");
    }

    @Test
    void getHashThrowWhenFirstMerklePathHasNoTimestampLeaf() {
        // given
        final byte[] rootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder().setNextPathIndex(2).build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(rootHash))
                        .setNextPathIndex(2)
                        .addAllSiblings(siblings(MIN_SIBLING_COUNT))
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(-1).build());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, rootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("The first merkle path in block 0's StateProof is not the timestamp leaf");
    }

    @ParameterizedTest(name = "sibling hash of {0} bytes")
    @ValueSource(ints = {0, 1, 47, 49})
    void getHashThrowWhenSiblingHashLengthIncorrect(final int length) {
        // given
        final byte[] rootHash = TestUtils.generateRandomByteArray(48);
        final var siblings = new ArrayList<>(siblings(MIN_SIBLING_COUNT));
        siblings.set(
                siblings.size() - 1,
                SiblingNode.newBuilder().setHash(fromBytes(new byte[length])).build());
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setNextPathIndex(2)
                        .setTimestampLeaf(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .build()
                                .toByteString())
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(rootHash))
                        .setNextPathIndex(2)
                        .addAllSiblings(siblings)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(-1).build());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, rootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Sibling hash length %d != 48".formatted(length));
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

    private static List<SiblingNode> siblings(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> SiblingNode.newBuilder()
                        .setHash(fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .toList();
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
