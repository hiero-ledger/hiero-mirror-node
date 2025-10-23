// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import com.google.common.collect.Range;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Key;
import org.hiero.mirror.rest.model.Key.TypeEnum;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.exception.InvalidMappingException;
import org.mapstruct.Mapper;
import org.mapstruct.MappingInheritanceStrategy;
import org.mapstruct.Named;

@Mapper(mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface CommonMapper {

    byte[] IMMUTABILITY_SENTINEL_KEY = com.hederahashgraph.api.proto.java.Key.newBuilder()
            .setKeyList(KeyList.getDefaultInstance())
            .build()
            .toByteArray();
    String QUALIFIER_TIMESTAMP = "timestamp";
    String QUALIFIER_TIMESTAMP_RANGE = "timestampRange";
    String QUALIFIER_TIMESTAMP_RANGE_NULLABLE = "timestampRangeNullable";
    int NANO_DIGITS = 9;
    int FRACTION_SCALE = 9;
    Pattern PATTERN_ECDSA = Pattern.compile("^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$");
    Pattern PATTERN_ED25519 = Pattern.compile("^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$");
    long SECONDS_PER_DAY = 86400L;
    String TIMESTAMP_ZERO = "0.0";

    default String mapEntityId(Long source) {
        if (source == null || source == 0) {
            return null;
        }

        var eid = EntityId.of(source);
        return mapEntityId(eid);
    }

    default String mapEntityId(EntityId source) {
        return source != null ? source.toString() : null;
    }

    default Key mapKey(byte[] source) {
        if (source == null || Arrays.equals(source, IMMUTABILITY_SENTINEL_KEY)) {
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

    @Named(QUALIFIER_TIMESTAMP_RANGE_NULLABLE)
    default TimestampRangeNullable mapTimestampRangeNullable(Range<Long> source) {
        final var timestampRange = mapRange(source);
        if (timestampRange == null) {
            return null;
        }

        final var target = new TimestampRangeNullable();
        target.setFrom(timestampRange.getFrom());
        target.setTo(timestampRange.getTo());
        return target;
    }

    @Named(QUALIFIER_TIMESTAMP)
    default String mapTimestamp(Long timestamp) {
        if (timestamp == null) {
            return null;
        }

        if (timestamp == 0) {
            return TIMESTAMP_ZERO;
        }

        var timestampString = StringUtils.leftPad(String.valueOf(timestamp), NANO_DIGITS + 1, '0');
        return new StringBuilder(timestampString)
                .insert(timestampString.length() - NANO_DIGITS, '.')
                .toString();
    }

    default Long mapTimestampSeconds(TimestampSeconds source) {
        if (source == null) {
            return null;
        }
        return source.getSeconds();
    }

    @Named(QUALIFIER_TIMESTAMP_RANGE)
    default TimestampRange mapTimestampRange(long stakingPeriod) {
        final long fromNs = stakingPeriod + 1;
        final long toNs = fromNs + (SECONDS_PER_DAY * DomainUtils.NANOS_PER_SECOND);

        return new TimestampRange().from(mapTimestamp(fromNs)).to(mapTimestamp(toNs));
    }

    /**
     * Calculates the fractional value of a numerator and denominator as a float with up to {@value #FRACTION_SCALE}
     * decimal places.
     *
     * @param numerator   the numerator of the fraction
     * @param denominator the denominator of the fraction
     * @return the result of numerator / denominator as a float, or 0.0f if denominator is 0
     */
    default float mapFraction(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0f;
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), FRACTION_SCALE, RoundingMode.HALF_UP)
                .floatValue();
    }
}
