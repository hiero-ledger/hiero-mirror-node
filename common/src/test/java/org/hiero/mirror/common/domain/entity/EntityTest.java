// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EntityTest {

    @ParameterizedTest
    @CsvSource({",,", ",1,1", "1,,1", "1,1,2"})
    void addBalance(Long base, Long delta, Long expected) {
        var entity = new Entity();
        entity.setBalance(base);
        entity.addBalance(delta);
        assertThat(entity.getBalance()).isEqualTo(expected);
    }

    @Test
    void nullCharacters() {
        Entity entity = new Entity();
        entity.setMemo("abc" + (char) 0);
        var actualBytes = entity.getMemo().getBytes(StandardCharsets.UTF_8);
        var expectedBytes = "abc�".getBytes(StandardCharsets.UTF_8);
        assertThat(actualBytes).isEqualTo(expectedBytes);
    }

    @Test
    void history() {
        Entity entity = new Entity();
        assertThat(entity.getTimestampRange()).isNull();
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isNull();

        Range<Long> timestampRangeLower = Range.atLeast(1L);
        entity.setTimestampRange(timestampRangeLower);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeLower);
        assertThat(entity.getTimestampLower()).isEqualTo(timestampRangeLower.lowerEndpoint());
        assertThat(entity.getTimestampUpper()).isNull();

        entity.setTimestampUpper(2L);
        assertThat(entity.getTimestampUpper()).isEqualTo(2L);

        Range<Long> timestampRangeUpper = Range.atMost(1L);
        entity.setTimestampRange(timestampRangeUpper);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeUpper);
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isEqualTo(timestampRangeUpper.upperEndpoint());

        entity.setTimestampLower(0L);
        assertThat(entity.getTimestampLower()).isZero();
    }

    @Test
    void setKeyWithPublicKeySideEffect() {
        // simple key
        var entity = new Entity();
        byte[] keyBytes = CommonUtils.nextBytes(16);
        var protobufKeyBytes = Key.newBuilder()
                .setEd25519(DomainUtils.fromBytes(keyBytes))
                .build()
                .toByteArray();
        entity.setKey(protobufKeyBytes);
        assertThat(entity.getKey()).isEqualTo(protobufKeyBytes);
        assertThat(entity.getPublicKey()).isEqualTo(Hex.encodeHexString(keyBytes));

        // threshold key of one key
        entity = new Entity();
        keyBytes = CommonUtils.nextBytes(16);
        protobufKeyBytes = Key.newBuilder()
                .setThresholdKey(ThresholdKey.newBuilder()
                        .setKeys(KeyList.newBuilder()
                                .addKeys(Key.newBuilder().setEd25519(DomainUtils.fromBytes(keyBytes))))
                        .setThreshold(1))
                .build()
                .toByteArray();
        entity.setKey(protobufKeyBytes);
        assertThat(entity.getKey()).isEqualTo(protobufKeyBytes);
        assertThat(entity.getPublicKey()).isEqualTo(Hex.encodeHexString(keyBytes));

        // threshold key of two keys
        entity = new Entity();
        keyBytes = CommonUtils.nextBytes(16);
        byte[] otherKeyBytes = CommonUtils.nextBytes(16);
        protobufKeyBytes = Key.newBuilder()
                .setThresholdKey(ThresholdKey.newBuilder()
                        .setKeys(KeyList.newBuilder()
                                .addKeys(Key.newBuilder().setEd25519(DomainUtils.fromBytes(keyBytes)))
                                .addKeys(Key.newBuilder().setEd25519(DomainUtils.fromBytes(otherKeyBytes))))
                        .setThreshold(1))
                .build()
                .toByteArray();
        entity.setKey(protobufKeyBytes);
        assertThat(entity.getKey()).isEqualTo(protobufKeyBytes);
        assertThat(entity.getPublicKey()).isEqualTo(StringUtils.EMPTY);

        // unparsable
        entity = new Entity();
        protobufKeyBytes = new byte[] {0x00, 0x01, 0x02, 0x03};
        entity.setKey(protobufKeyBytes);
        assertThat(entity.getKey()).isEqualTo(protobufKeyBytes);
        assertThat(entity.getPublicKey()).isEqualTo(StringUtils.EMPTY);
    }
}
