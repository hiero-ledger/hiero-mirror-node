// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.restjava.mapper.CommonMapper.NANO_DIGITS;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Key.TypeEnum;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.restjava.exception.InvalidMappingException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CommonMapperTest {

    private final CommonMapper commonMapper = Mappers.getMapper(CommonMapper.class);
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void mapEntityId() {
        var entityId = EntityId.of("1.2.3");
        assertThat(commonMapper.mapEntityId((EntityId) null)).isNull();
        assertThat(commonMapper.mapEntityId(entityId))
                .isEqualTo(EntityId.of(1L, 2L, 3L).toString());
    }

    @Test
    void mapEntityIdLong() {
        assertThat(commonMapper.mapEntityId((Long) null)).isNull();
        assertThat(commonMapper.mapEntityId(0L)).isNull();
    }

    @Test
    void mapKeyList() {
        // Given
        var bytesEcdsa = domainBuilder.bytes(DomainBuilder.KEY_LENGTH_ECDSA);
        var ecdsa = Key.newBuilder()
                .setECDSASecp256K1(DomainUtils.fromBytes(bytesEcdsa))
                .build();
        var bytesEd25519 = domainBuilder.bytes(DomainBuilder.KEY_LENGTH_ED25519);
        var ed25519 =
                Key.newBuilder().setEd25519(DomainUtils.fromBytes(bytesEd25519)).build();
        var innerKeyList = KeyList.newBuilder().addKeys(ecdsa).addKeys(ed25519).build();
        var innerKeyListKey = Key.newBuilder().setKeyList(innerKeyList).build();
        var bytesInnerKeyListKey = innerKeyListKey.toByteArray();
        var keyList = innerKeyList.toBuilder().addKeys(innerKeyListKey).build();
        var feeExemptKeyList =
                FeeExemptKeyList.newBuilder().addAllKeys(keyList.getKeysList()).build();
        var emptyKeyList = KeyList.newBuilder().build();
        var emptyFeeExemptKeyList = FeeExemptKeyList.newBuilder().build();
        var expectedKeyList = List.of(
                new org.hiero.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesEcdsa))
                        .type(TypeEnum.ECDSA_SECP256_K1),
                new org.hiero.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesEd25519))
                        .type(TypeEnum.ED25519),
                new org.hiero.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesInnerKeyListKey))
                        .type(TypeEnum.PROTOBUF_ENCODED));

        // Then
        assertThat(commonMapper.mapKeyList(null)).isEmpty();
        assertThat(commonMapper.mapKeyList(new byte[0])).isEmpty();
        assertThat(commonMapper.mapKeyList(keyList.toByteArray())).isEqualTo(expectedKeyList);
        assertThat(commonMapper.mapKeyList(feeExemptKeyList.toByteArray())).isEqualTo(expectedKeyList);
        assertThat(commonMapper.mapKeyList(emptyKeyList.toByteArray())).isEmpty();
        assertThat(commonMapper.mapKeyList(emptyFeeExemptKeyList.toByteArray())).isEmpty();
    }

    @SneakyThrows
    @Test
    void mapKeyListThrow() {
        byte[] data = Hex.decodeHex("deadbeef");
        assertThatThrownBy(() -> commonMapper.mapKeyList(data)).isInstanceOf(InvalidMappingException.class);
    }

    @Test
    void mapKey() {
        // Given
        var bytesEcdsa = domainBuilder.bytes(DomainBuilder.KEY_LENGTH_ECDSA);
        var bytesEd25519 = domainBuilder.bytes(DomainBuilder.KEY_LENGTH_ED25519);
        var ecdsa = Key.newBuilder()
                .setECDSASecp256K1(DomainUtils.fromBytes(bytesEcdsa))
                .build();
        var ecdsaList =
                Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(ecdsa)).build();
        var ed25519 =
                Key.newBuilder().setEd25519(DomainUtils.fromBytes(bytesEd25519)).build();
        var ed25519List = Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addKeys(ed25519))
                .build();
        var emptyKeyListKey =
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
        var protobufEncoded = Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addKeys(ecdsa).addKeys(ed25519))
                .build()
                .toByteArray();

        // Then
        assertThat(commonMapper.mapKey(null)).isNull();
        assertThat(commonMapper.mapKey(ecdsa.toByteArray())).isEqualTo(toKey(bytesEcdsa, TypeEnum.ECDSA_SECP256_K1));
        assertThat(commonMapper.mapKey(ecdsaList.toByteArray()))
                .isEqualTo(toKey(bytesEcdsa, TypeEnum.ECDSA_SECP256_K1));
        assertThat(commonMapper.mapKey(ed25519.toByteArray())).isEqualTo(toKey(bytesEd25519, TypeEnum.ED25519));
        assertThat(commonMapper.mapKey(ed25519.toByteArray())).isEqualTo(toKey(bytesEd25519, TypeEnum.ED25519));
        assertThat(commonMapper.mapKey(ed25519List.toByteArray())).isEqualTo(toKey(bytesEd25519, TypeEnum.ED25519));
        assertThat(commonMapper.mapKey(emptyKeyListKey.toByteArray())).isNull();
        assertThat(commonMapper.mapKey(protobufEncoded)).isEqualTo(toKey(protobufEncoded, TypeEnum.PROTOBUF_ENCODED));
    }

    @Test
    void mapLowerRange() {
        assertThat(commonMapper.mapLowerRange(null)).isNull();
        assertThat(commonMapper.mapLowerRange(Range.atMost(200L))).isNull();
        assertThat(commonMapper.mapLowerRange(Range.atLeast(0L))).isEqualTo("0.0");
        assertThat(commonMapper.mapLowerRange(Range.atLeast(1500123456789L))).isEqualTo("1500.123456789");
        assertThat(commonMapper.mapLowerRange(Range.atLeast(1500123456000L))).isEqualTo("1500.123456000");
    }

    @Test
    void mapRange() {
        var range = new TimestampRange();
        var now = System.nanoTime();
        var timestampString = StringUtils.leftPad(String.valueOf(now), 10, '0');

        // test1
        assertThat(commonMapper.mapRange(null)).isNull();

        // test2
        range.setFrom("0.0");
        assertThat(commonMapper.mapRange(Range.atLeast(0L)))
                .usingRecursiveComparison()
                .isEqualTo(range);
        range.setTo(timestampString.substring(0, timestampString.length() - NANO_DIGITS) + "."
                + timestampString.substring(timestampString.length() - NANO_DIGITS));
        assertThat(commonMapper.mapRange(Range.openClosed(0L, now)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        // test 3
        range.setFrom("0.0");
        range.setTo("1.000000001");
        assertThat(commonMapper.mapRange(Range.openClosed(0L, 1_000_000_001L)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        // test 4
        range.setFrom("1586567700.453054000");
        range.setTo("1586567700.453054000");
        assertThat(commonMapper.mapRange(Range.openClosed(1586567700453054000L, 1586567700453054000L)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        // test5
        range.setFrom("0.000000001");
        range.setTo("0.000000100");
        assertThat(commonMapper.mapRange(Range.openClosed(1L, 100L)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        // test6
        range.setFrom("0.110000000");
        range.setTo("1.100000000");
        assertThat(commonMapper.mapRange(Range.openClosed(110000000L, 1100000000L)))
                .usingRecursiveComparison()
                .isEqualTo(range);
    }

    private org.hiero.mirror.rest.model.Key toKey(byte[] bytes, TypeEnum type) {
        return new org.hiero.mirror.rest.model.Key()
                .key(Hex.encodeHexString(bytes))
                .type(type);
    }
}
