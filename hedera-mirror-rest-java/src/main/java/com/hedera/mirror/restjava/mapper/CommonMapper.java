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

import com.google.common.collect.Range;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.Key;
import com.hedera.mirror.rest.model.Key.TypeEnum;
import com.hedera.mirror.rest.model.TimestampRange;
import com.hedera.mirror.restjava.exception.InvalidMappingException;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.MappingInheritanceStrategy;
import org.mapstruct.Named;

@Mapper(mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface CommonMapper {

    String QUALIFIER_TIMESTAMP = "timestamp";
    int NANO_DIGITS = 9;
    Pattern PATTERN_ECDSA = Pattern.compile("^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$");
    Pattern PATTERN_ED25519 = Pattern.compile("^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$");

    default String mapEntityId(Long source) {
        if (source == null || source == 0) {
            return null;
        }

        var eid = EntityId.of(source);
        return mapEntityId(eid);
    }

    default String mapEntityId(com.hedera.mirror.common.domain.entity.EntityId source) {
        return source != null ? source.toString() : null;
    }

    default Key mapKey(byte[] source) {
        if (source == null) {
            return null;
        }

        var hex = Hex.encodeHexString(source);
        var ed25519 = PATTERN_ED25519.matcher(hex);

        if (ed25519.matches()) {
            return new Key().key(ed25519.group(2)).type(TypeEnum.ED25519);
        }

        var ecdsa = PATTERN_ECDSA.matcher(hex);

        if (ecdsa.matches()) {
            return new Key().key(ecdsa.group(2)).type(TypeEnum.ECDSA_SECP256_K1);
        }

        return new Key().key(hex).type(TypeEnum.PROTOBUF_ENCODED);
    }

    default List<Key> mapKeyList(byte[] source) {
        if (ArrayUtils.isEmpty(source)) {
            return Collections.emptyList();
        }

        try {
            var keyList = KeyList.parseFrom(source);
            return keyList.getKeysList().stream()
                    .map(key -> mapKey(key.toByteArray()))
                    .toList();
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidMappingException("Error parsing protobuf message", e);
        }
    }

    default String mapLowerRange(Range<Long> source) {
        if (source == null || !source.hasLowerBound()) {
            return null;
        }

        return mapTimestamp(source.lowerEndpoint());
    }

    default TimestampRange mapRange(Range<Long> source) {
        if (source == null) {
            return null;
        }

        var target = new TimestampRange();
        if (source.hasLowerBound()) {
            target.setFrom(mapTimestamp(source.lowerEndpoint()));
        }

        if (source.hasUpperBound()) {
            target.setTo(mapTimestamp(source.upperEndpoint()));
        }

        return target;
    }

    @Named(QUALIFIER_TIMESTAMP)
    default String mapTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "0.0";
        }

        var timestampString = StringUtils.leftPad(String.valueOf(timestamp), NANO_DIGITS + 1, '0');
        return new StringBuilder(timestampString)
                .insert(timestampString.length() - NANO_DIGITS, '.')
                .toString();
    }
}
