// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.hiero.mirror.common.domain.ListHolder;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.domain.node.ServiceEndpointsHolder;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FixedFeesHolder;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.FractionalFeesHolder;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.RoyaltyFeesHolder;
import org.hiero.mirror.common.domain.transaction.AccessList;
import org.hiero.mirror.common.domain.transaction.AccessListHolder;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.AuthorizationListHolder;
import org.hiero.mirror.common.domain.transaction.ItemizedTransfer;
import org.hiero.mirror.common.domain.transaction.ItemizedTransferListHolder;
import org.hiero.mirror.common.domain.transaction.NftTransferListHolder;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.common.domain.tss.LedgerNodeContributionListHolder;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/**
 * JDBC converters for every JSONB column type. A {@link ListHolder} is stored as its bare JSON array, any other type as
 * its JSON object. Types are listed explicitly rather than registering {@link List} or {@link Object} as custom simple
 * types, which makes Spring Data AOT traverse unrelated classpath types (e.g. protobuf internals). Element lists are
 * deserialized through {@link TypeReference}s so no reflection is needed on the holders themselves.
 */
public final class JsonbJdbcConverters {

    private static final Map<Class<?>, Function<String, Object>> READERS = Map.of(
            AccessListHolder.class, json -> AccessListHolder.of(read(json, new TypeReference<List<AccessList>>() {})),
            AuthorizationListHolder.class,
                    json -> AuthorizationListHolder.of(read(json, new TypeReference<List<Authorization>>() {})),
            FixedFeesHolder.class, json -> FixedFeesHolder.of(read(json, new TypeReference<List<FixedFee>>() {})),
            FractionalFeesHolder.class,
                    json -> FractionalFeesHolder.of(read(json, new TypeReference<List<FractionalFee>>() {})),
            ItemizedTransferListHolder.class,
                    json -> ItemizedTransferListHolder.of(read(json, new TypeReference<List<ItemizedTransfer>>() {})),
            LedgerNodeContributionListHolder.class,
                    json -> LedgerNodeContributionListHolder.of(
                            read(json, new TypeReference<List<LedgerNodeContribution>>() {})),
            NftTransferListHolder.class,
                    json -> NftTransferListHolder.of(read(json, new TypeReference<List<NftTransfer>>() {})),
            RoyaltyFeesHolder.class, json -> RoyaltyFeesHolder.of(read(json, new TypeReference<List<RoyaltyFee>>() {})),
            ServiceEndpoint.class, json -> read(json, new TypeReference<ServiceEndpoint>() {}),
            ServiceEndpointsHolder.class,
                    json -> ServiceEndpointsHolder.of(
                            read(json, new TypeReference<List<RegisteredServiceEndpoint>>() {})));

    private JsonbJdbcConverters() {}

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads a JSONB column, exposed by the driver as a {@link PGobject} or, on some paths, a plain string. */
    @ReadingConverter
    public static final class JsonbToObject implements GenericConverter {

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            final var pairs = new HashSet<ConvertiblePair>();
            for (final var type : READERS.keySet()) {
                pairs.add(new ConvertiblePair(PGobject.class, type));
                pairs.add(new ConvertiblePair(String.class, type));
            }
            return pairs;
        }

        @Override
        public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            final var json = source instanceof PGobject pgObject ? pgObject.getValue() : (String) source;
            if (json == null || json.isEmpty()) {
                return null;
            }

            return READERS.get(targetType.getType()).apply(json);
        }
    }

    @WritingConverter
    public static final class ObjectToJsonb implements GenericConverter {

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            final var pairs = new HashSet<ConvertiblePair>();
            for (final var type : READERS.keySet()) {
                pairs.add(new ConvertiblePair(type, PGobject.class));
            }
            return pairs;
        }

        @Override
        public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }

            final var value = source instanceof ListHolder<?> holder ? holder.items() : source;
            try {
                final var pgObject = new PGobject();
                pgObject.setType("jsonb");
                pgObject.setValue(OBJECT_MAPPER.writeValueAsString(value));
                return pgObject;
            } catch (JsonProcessingException | SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
