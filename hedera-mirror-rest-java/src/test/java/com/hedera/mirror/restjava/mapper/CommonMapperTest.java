/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.restjava.mapper;

import static com.hedera.mirror.restjava.mapper.CommonMapper.NANO_DIGITS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Key.TypeEnum;
import com.hedera.mirror.rest.model.TimestampRange;
import com.hedera.mirror.restjava.exception.InvalidMappingException;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CommonMapperTest {

    private final CommonMapper commonMapper = Mappers.getMapper(CommonMapper.class);
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void mapEntityId() {
        var entityId = com.hedera.mirror.common.domain.entity.EntityId.of("1.2.3");
        assertThat(commonMapper.mapEntityId((com.hedera.mirror.common.domain.entity.EntityId) null))
                .isNull();
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
        var feeExemptKeyList = innerKeyList.toBuilder().addKeys(innerKeyListKey).build();
        var expectedKeyList = List.of(
                new com.hedera.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesEcdsa))
                        .type(TypeEnum.ECDSA_SECP256_K1),
                new com.hedera.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesEd25519))
                        .type(TypeEnum.ED25519),
                new com.hedera.mirror.rest.model.Key()
                        .key(Hex.encodeHexString(bytesInnerKeyListKey))
                        .type(TypeEnum.PROTOBUF_ENCODED));

        // Then
        assertThat(commonMapper.mapKeyList(null)).isEmpty();
        assertThat(commonMapper.mapKeyList(new byte[0])).isEmpty();
        assertThat(commonMapper.mapKeyList(keyList.toByteArray())).isEqualTo(expectedKeyList);
        assertThat(commonMapper.mapKeyList(feeExemptKeyList.toByteArray())).isEqualTo(expectedKeyList);
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
        assertThat(commonMapper.mapKey(protobufEncoded)).isEqualTo(toKey(protobufEncoded, TypeEnum.PROTOBUF_ENCODED));
    }

    @Test
    void mapLowerRange() {
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

    private com.hedera.mirror.rest.model.Key toKey(byte[] bytes, TypeEnum type) {
        return new com.hedera.mirror.rest.model.Key()
                .key(Hex.encodeHexString(bytes))
                .type(type);
    }
}
