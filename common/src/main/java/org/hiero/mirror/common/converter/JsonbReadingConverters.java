// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
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
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * JDBC reading converters for JSONB and PostgreSQL array columns so Spring Data JDBC maps them as simple values instead of
 * relational aggregates (which would otherwise trigger join generation and fail with {@code IdColumnInfo must not be
 * null}).
 */
public final class JsonbReadingConverters {

    private JsonbReadingConverters() {}

    @ReadingConverter
    public static final class PgobjectToRegisteredServiceEndpointList
            implements Converter<PGobject, List<RegisteredServiceEndpoint>> {
        @Override
        public List<RegisteredServiceEndpoint> convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(
                        source.getValue(), new TypeReference<List<RegisteredServiceEndpoint>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToServiceEndpointsHolder implements Converter<PGobject, ServiceEndpointsHolder> {

        private static final PgobjectToRegisteredServiceEndpointList LIST =
                new PgobjectToRegisteredServiceEndpointList();

        @Override
        public ServiceEndpointsHolder convert(PGobject source) {
            return ServiceEndpointsHolder.of(LIST.convert(source));
        }
    }

    @ReadingConverter
    public static final class PgobjectToServiceEndpoint implements Converter<PGobject, ServiceEndpoint> {
        @Override
        public ServiceEndpoint convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(source.getValue(), ServiceEndpoint.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToServiceEndpoint implements Converter<String, ServiceEndpoint> {
        @Override
        public ServiceEndpoint convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(source, ServiceEndpoint.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Some JDBC paths expose JSON/JSONB as a plain string.
     */
    @ReadingConverter
    public static final class StringToRegisteredServiceEndpointList
            implements Converter<String, List<RegisteredServiceEndpoint>> {
        @Override
        public List<RegisteredServiceEndpoint> convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(source, new TypeReference<List<RegisteredServiceEndpoint>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToItemizedTransferListHolder
            implements Converter<PGobject, ItemizedTransferListHolder> {

        @Override
        public ItemizedTransferListHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<ItemizedTransfer>>() {});
                return ItemizedTransferListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToItemizedTransferListHolder
            implements Converter<String, ItemizedTransferListHolder> {

        @Override
        public ItemizedTransferListHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<ItemizedTransfer>>() {});
                return ItemizedTransferListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToNftTransferListHolder implements Converter<PGobject, NftTransferListHolder> {

        @Override
        public NftTransferListHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<NftTransfer>>() {});
                return NftTransferListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToNftTransferListHolder implements Converter<String, NftTransferListHolder> {

        @Override
        public NftTransferListHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<NftTransfer>>() {});
                return NftTransferListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToLedgerNodeContributionListHolder
            implements Converter<PGobject, LedgerNodeContributionListHolder> {

        @Override
        public LedgerNodeContributionListHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(
                        source.getValue(), new TypeReference<List<LedgerNodeContribution>>() {});
                return LedgerNodeContributionListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToLedgerNodeContributionListHolder
            implements Converter<String, LedgerNodeContributionListHolder> {

        @Override
        public LedgerNodeContributionListHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<LedgerNodeContribution>>() {});
                return LedgerNodeContributionListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToAccessListHolder implements Converter<PGobject, AccessListHolder> {

        @Override
        public AccessListHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<AccessList>>() {});
                return AccessListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToAccessListHolder implements Converter<String, AccessListHolder> {

        @Override
        public AccessListHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<AccessList>>() {});
                return AccessListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToAuthorizationListHolder
            implements Converter<PGobject, AuthorizationListHolder> {

        @Override
        public AuthorizationListHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<Authorization>>() {});
                return AuthorizationListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToAuthorizationListHolder implements Converter<String, AuthorizationListHolder> {

        @Override
        public AuthorizationListHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<Authorization>>() {});
                return AuthorizationListHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToFixedFeesHolder implements Converter<PGobject, FixedFeesHolder> {

        @Override
        public FixedFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<FixedFee>>() {});
                return FixedFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToFixedFeesHolder implements Converter<String, FixedFeesHolder> {

        @Override
        public FixedFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<FixedFee>>() {});
                return FixedFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToFractionalFeesHolder implements Converter<PGobject, FractionalFeesHolder> {

        @Override
        public FractionalFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<FractionalFee>>() {});
                return FractionalFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToFractionalFeesHolder implements Converter<String, FractionalFeesHolder> {

        @Override
        public FractionalFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<FractionalFee>>() {});
                return FractionalFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToRoyaltyFeesHolder implements Converter<PGobject, RoyaltyFeesHolder> {

        @Override
        public RoyaltyFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<RoyaltyFee>>() {});
                return RoyaltyFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToRoyaltyFeesHolder implements Converter<String, RoyaltyFeesHolder> {

        @Override
        public RoyaltyFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<RoyaltyFee>>() {});
                return RoyaltyFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
