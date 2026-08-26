// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.ByteString;
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
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void getHashWhenHashPathPrecedesTimestampLeaf() {
        // given
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var timestampLeaf = timestampLeaf();
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(2)
                        .build(),
                rootPath());

        final var digest = createSha384Digest();
        final byte[] timestampHash = HashUtils.hashLeaf(digest, timestampLeaf.toByteArray());
        final byte[] expected = HashUtils.hashInternalNode(digest, currentRootHash, timestampHash);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashWithIntermediateJoinPoint() {
        // given - the join point at index 2 joins the two paths below it, then contributes to the root path
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var blockItemLeaf = fromBytes(TestUtils.generateRandomByteArray(8));
        final var timestampLeaf = timestampLeaf();
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(4).build(),
                MerklePath.newBuilder()
                        .setBlockItemLeaf(blockItemLeaf)
                        .setNextPathIndex(4)
                        .build(),
                rootPath());

        final var digest = createSha384Digest();
        final byte[] timestampHash = HashUtils.hashLeaf(digest, timestampLeaf.toByteArray());
        final byte[] blockItemHash = HashUtils.hashLeaf(digest, blockItemLeaf.toByteArray());
        final byte[] joinHash = HashUtils.hashInternalNode(digest, timestampHash, currentRootHash);
        final byte[] expected = HashUtils.hashInternalNode(digest, joinHash, blockItemHash);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashWhenRootPathHasSiblings() {
        // given - the root path starts from the last join point, so it carries the siblings of the nodes between
        // that join point and the actual root
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var timestampLeaf = timestampLeaf();
        final var leftSibling = TestUtils.generateRandomByteArray(48);
        final var rightSibling = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .addSiblings(SiblingNode.newBuilder()
                                .setHash(fromBytes(rightSibling))
                                .build())
                        .addSiblings(SiblingNode.newBuilder()
                                .setHash(fromBytes(leftSibling))
                                .setIsLeft(true)
                                .build())
                        .setNextPathIndex(-1)
                        .build());

        final var digest = createSha384Digest();
        final byte[] timestampHash = HashUtils.hashLeaf(digest, timestampLeaf.toByteArray());
        final byte[] joinHash = HashUtils.hashInternalNode(digest, timestampHash, currentRootHash);
        final byte[] withRight = HashUtils.hashInternalNode(digest, joinHash, rightSibling);
        final byte[] expected = HashUtils.hashInternalNode(digest, leftSibling, withRight);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashWhenRootPathIsFirst() {
        // given
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var timestampLeaf = timestampLeaf();
        final var merklePaths = List.of(
                rootPath(),
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(0)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(0)
                        .build());

        final var digest = createSha384Digest();
        final byte[] timestampHash = HashUtils.hashLeaf(digest, timestampLeaf.toByteArray());
        final byte[] expected = HashUtils.hashInternalNode(digest, timestampHash, currentRootHash);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashIsIndependentOfPathOrder() {
        // given - the same tree laid out in depth first order and in a scrambled order. The branch holding the
        // lowest indexed content path is the left operand, so both must produce the same root hash.
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var blockItemLeaf = fromBytes(TestUtils.generateRandomByteArray(8));
        final var timestampLeaf = timestampLeaf();
        final var depthFirst = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(4).build(),
                MerklePath.newBuilder()
                        .setBlockItemLeaf(blockItemLeaf)
                        .setNextPathIndex(4)
                        .build(),
                rootPath());
        final var scrambled = List.of(
                rootPath(),
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(4)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(4)
                        .build(),
                MerklePath.newBuilder()
                        .setBlockItemLeaf(blockItemLeaf)
                        .setNextPathIndex(0)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(0).build());

        // when
        final byte[] actual = hasher.getRootHash(0, currentRootHash, scrambled);

        // then
        assertThat(actual).isEqualTo(hasher.getRootHash(0, currentRootHash, depthFirst));
    }

    @Test
    void getHashThrowWhenContentPathIsTheRootPath() {
        // given - a content path claiming the root leaves the join point at index 2 short of a second branch, so
        // the root hash can never come from anything but a join point
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(-1)
                        .build(),
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(2)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has a join point merkle path with only one child");
    }

    @Test
    void getHashThrowWhenMoreThanOneRootPath() {
        // given - both content paths climb straight to the root
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(-1)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(-1)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has more than one root merkle path");
    }

    @Test
    void getHashThrowWhenJoinPointPointsToItself() {
        // given - the join point at index 2 is its own parent
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(2).build());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof joins merkle path 2 more than once");
    }

    @Test
    void getHashWithStateItemLeaf() {
        // given
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var stateItemLeaf = fromBytes(TestUtils.generateRandomByteArray(16));
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setStateItemLeaf(stateItemLeaf)
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                rootPath());

        final var digest = createSha384Digest();
        final byte[] stateItemHash = HashUtils.hashLeaf(digest, stateItemLeaf.toByteArray());
        final byte[] expected = HashUtils.hashInternalNode(digest, stateItemHash, currentRootHash);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashWhenParkedBranchIsTheRightOperand() {
        // given - the branch parked at the root join point holds a higher content index than the branch arriving
        // later, so the arriving one becomes the left operand. The operands follow the lowest content index below
        // each branch, not the order in which the branches reach the join point.
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var blockItemLeaf = fromBytes(TestUtils.generateRandomByteArray(8));
        final var timestampLeaf = timestampLeaf();
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf)
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder()
                        .setBlockItemLeaf(blockItemLeaf)
                        .setNextPathIndex(4)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(4).build(),
                rootPath());

        final var digest = createSha384Digest();
        final byte[] timestampHash = HashUtils.hashLeaf(digest, timestampLeaf.toByteArray());
        final byte[] blockItemHash = HashUtils.hashLeaf(digest, blockItemLeaf.toByteArray());
        final byte[] joinHash = HashUtils.hashInternalNode(digest, timestampHash, currentRootHash);
        final byte[] expected = HashUtils.hashInternalNode(digest, joinHash, blockItemHash);

        // when, then
        assertThat(hasher.getRootHash(0, currentRootHash, merklePaths)).isEqualTo(expected);
    }

    @Test
    void getHashThrowWhenLessThanMinPaths() {
        assertThatThrownBy(() -> hasher.getRootHash(0, TestUtils.generateRandomByteArray(48), Collections.emptyList()))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Number of merkle paths in block 0's StateProof is less than 3");
    }

    @Test
    void getHashThrowWhenPathPointsToContentPath() {
        // given - the timestamp leaf path points at the hash path, which already has its own content
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(1)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has merkle path 0 pointing to merkle path 1 which has content");
    }

    @Test
    void getHashThrowWhenNoBranchReachesRoot() {
        // given - both branches park at a join point, so nothing ever climbs to the root
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(3).build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has no root merkle path");
    }

    @Test
    void getHashThrowWhenJoinPointHasNoChildren() {
        // given - the join point at index 1 is never pointed at by an earlier path
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder().setNextPathIndex(3).build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(3)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has a join point merkle path with no children");
    }

    @ParameterizedTest(name = "next path index of {0}")
    @ValueSource(ints = {-2, 3, 99})
    void getHashThrowWhenNextPathIndexOutOfRange(final int nextPathIndex) {
        // given - a path may only contribute to a later path within the list
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(nextPathIndex)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has out of range next path index %d in merkle path 1"
                        .formatted(nextPathIndex));
    }

    @Test
    void getHashThrowWhenJoinedMoreThanOnce() {
        // given - three paths all contribute to the root path, which can only join two children
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(3)
                        .build(),
                MerklePath.newBuilder()
                        .setBlockItemLeaf(fromBytes(TestUtils.generateRandomByteArray(8)))
                        .setNextPathIndex(3)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof joins merkle path 3 more than once");
    }

    @ParameterizedTest(name = "sibling hash of {0} bytes")
    @ValueSource(ints = {0, 1, 47, 49})
    void getHashThrowWhenSiblingHashLengthIncorrect(final int length) {
        // given
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);
        final var siblings = new ArrayList<>(siblings(4));
        siblings.set(
                siblings.size() - 1,
                SiblingNode.newBuilder().setHash(fromBytes(new byte[length])).build());
        final var merklePaths = List.of(
                MerklePath.newBuilder()
                        .setTimestampLeaf(timestampLeaf())
                        .setNextPathIndex(2)
                        .build(),
                MerklePath.newBuilder()
                        .setHash(fromBytes(currentRootHash))
                        .setNextPathIndex(2)
                        .addAllSiblings(siblings)
                        .build(),
                rootPath());

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, merklePaths))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Sibling hash length %d != 48".formatted(length));
    }

    @Test
    void getHashThrowWhenNoPathMatchesRootHash() {
        // given
        final var testArtifact = TEST_ARTIFACTS.getFirst();
        final byte[] currentRootHash = TestUtils.generateRandomByteArray(48);

        // when, then
        assertThatThrownBy(() -> hasher.getRootHash(0, currentRootHash, testArtifact.merklePaths()))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Block 0's StateProof has no merkle path matching the block's root hash");
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

    private static MerklePath rootPath() {
        return MerklePath.newBuilder().setNextPathIndex(-1).build();
    }

    private static List<SiblingNode> siblings(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> SiblingNode.newBuilder()
                        .setHash(fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .toList();
    }

    private static ByteString timestampLeaf() {
        return Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .build()
                .toByteString();
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
