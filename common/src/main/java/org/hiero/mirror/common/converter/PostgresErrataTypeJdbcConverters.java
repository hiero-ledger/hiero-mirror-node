// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class PostgresErrataTypeJdbcConverters {

    private PostgresErrataTypeJdbcConverters() {}

    /**
     * Some Spring Data JDBC code paths bind enums as VARCHAR unless we explicitly provide a PGobject. Returning a
     * {@link PGobject} ensures the driver sends the parameter as PostgreSQL enum (OTHER). This must be the only
     * writing converter so the resolved column type is PGobject and null values also bind as OTHER instead of the
     * enum default VARCHAR.
     */
    @WritingConverter
    public static final class ErrataTypeToPGobject implements Converter<ErrataType, PGobject> {

        @Override
        @SneakyThrows
        public PGobject convert(ErrataType source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("errata_type");
            pg.setValue(source.name());
            return pg;
        }
    }

    @ReadingConverter
    public static final class PGobjectToErrataType implements Converter<PGobject, ErrataType> {

        @Override
        public ErrataType convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return ErrataType.valueOf(source.getValue());
        }
    }

    @ReadingConverter
    public static final class StringToErrataType implements Converter<String, ErrataType> {

        @Override
        public ErrataType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return ErrataType.valueOf(source);
        }
    }
}
