// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.SQLException;
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
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.ItemizedTransfer;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Explicit JSONB {@link Converter}s for each stored shape. Avoids registering {@link List} or {@link Object} as custom
 * simple types, which makes Spring Data AOT traverse unrelated classpath types (e.g. protobuf internals).
 */
public final class JsonbWritingConverters {

    private JsonbWritingConverters() {}

    private static PGobject toJsonb(Object source) {
        if (source == null) {
            return null;
        }
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(OBJECT_MAPPER.writeValueAsString(source));
            return jsonObject;
        } catch (JsonProcessingException | SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @WritingConverter
    public static final class FixedFeeList implements Converter<List<FixedFee>, PGobject> {
        @Override
        public PGobject convert(List<FixedFee> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class FractionalFeeList implements Converter<List<FractionalFee>, PGobject> {
        @Override
        public PGobject convert(List<FractionalFee> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class RoyaltyFeeList implements Converter<List<RoyaltyFee>, PGobject> {
        @Override
        public PGobject convert(List<RoyaltyFee> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class RegisteredServiceEndpointList
            implements Converter<List<RegisteredServiceEndpoint>, PGobject> {
        @Override
        public PGobject convert(List<RegisteredServiceEndpoint> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class ServiceEndpointsHolderToJsonb implements Converter<ServiceEndpointsHolder, PGobject> {

        private static final RegisteredServiceEndpointList LIST = new RegisteredServiceEndpointList();

        @Override
        public PGobject convert(ServiceEndpointsHolder source) {
            if (source == null) {
                return null;
            }
            return LIST.convert(source.items());
        }
    }

    @WritingConverter
    public static final class ItemizedTransferList implements Converter<List<ItemizedTransfer>, PGobject> {
        @Override
        public PGobject convert(List<ItemizedTransfer> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class NftTransferList implements Converter<List<NftTransfer>, PGobject> {
        @Override
        public PGobject convert(List<NftTransfer> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class AuthorizationList implements Converter<List<Authorization>, PGobject> {
        @Override
        public PGobject convert(List<Authorization> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class LedgerNodeContributionList implements Converter<List<LedgerNodeContribution>, PGobject> {
        @Override
        public PGobject convert(List<LedgerNodeContribution> source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class ServiceEndpointSingle implements Converter<ServiceEndpoint, PGobject> {
        @Override
        public PGobject convert(ServiceEndpoint source) {
            return toJsonb(source);
        }
    }

    @WritingConverter
    public static final class FixedFeesHolderToJsonb implements Converter<FixedFeesHolder, PGobject> {

        private static final FixedFeeList DELEGATE = new FixedFeeList();

        @Override
        public PGobject convert(FixedFeesHolder source) {
            if (source == null) {
                return null;
            }
            return DELEGATE.convert(source.items());
        }
    }

    @WritingConverter
    public static final class FractionalFeesHolderToJsonb implements Converter<FractionalFeesHolder, PGobject> {

        private static final FractionalFeeList DELEGATE = new FractionalFeeList();

        @Override
        public PGobject convert(FractionalFeesHolder source) {
            if (source == null) {
                return null;
            }
            return DELEGATE.convert(source.items());
        }
    }

    @WritingConverter
    public static final class RoyaltyFeesHolderToJsonb implements Converter<RoyaltyFeesHolder, PGobject> {

        private static final RoyaltyFeeList DELEGATE = new RoyaltyFeeList();

        @Override
        public PGobject convert(RoyaltyFeesHolder source) {
            if (source == null) {
                return null;
            }
            return DELEGATE.convert(source.items());
        }
    }
}
