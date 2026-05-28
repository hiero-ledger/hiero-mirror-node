// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/** JDBC converters for token columns stored as {@code smallint} or PostgreSQL enum types. */
public final class PostgresTokenJdbcConverters {

    private PostgresTokenJdbcConverters() {}

    @WritingConverter
    public static final class TokenFreezeStatusEnumToJdbcValue implements Converter<TokenFreezeStatusEnum, JdbcValue> {

        @Override
        public JdbcValue convert(TokenFreezeStatusEnum source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.SMALLINT);
            }
            return JdbcValue.of((short) source.getDbId(), JDBCType.SMALLINT);
        }
    }

    /**
     * Some Spring Data JDBC write paths don't honor {@link JdbcValue} and will bind enums as strings.
     * Providing an explicit numeric converter ensures the driver binds a number for {@code smallint} columns.
     */
    @WritingConverter
    public static final class TokenFreezeStatusEnumToInteger implements Converter<TokenFreezeStatusEnum, Integer> {

        @Override
        public Integer convert(TokenFreezeStatusEnum source) {
            return source == null ? null : source.getDbId();
        }
    }

    @ReadingConverter
    public static final class IntegerToTokenFreezeStatusEnum implements Converter<Integer, TokenFreezeStatusEnum> {

        @Override
        public TokenFreezeStatusEnum convert(Integer source) {
            return source == null ? null : TokenFreezeStatusEnum.fromId(source);
        }
    }

    @WritingConverter
    public static final class TokenKycStatusEnumToJdbcValue implements Converter<TokenKycStatusEnum, JdbcValue> {

        @Override
        public JdbcValue convert(TokenKycStatusEnum source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.SMALLINT);
            }
            return JdbcValue.of((short) source.getDbId(), JDBCType.SMALLINT);
        }
    }

    @WritingConverter
    public static final class TokenKycStatusEnumToInteger implements Converter<TokenKycStatusEnum, Integer> {

        @Override
        public Integer convert(TokenKycStatusEnum source) {
            return source == null ? null : source.getDbId();
        }
    }

    @ReadingConverter
    public static final class IntegerToTokenKycStatusEnum implements Converter<Integer, TokenKycStatusEnum> {

        @Override
        public TokenKycStatusEnum convert(Integer source) {
            return source == null ? null : TokenKycStatusEnum.fromId(source);
        }
    }

    @WritingConverter
    public static final class TokenPauseStatusEnumToJdbcValue implements Converter<TokenPauseStatusEnum, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(TokenPauseStatusEnum source) {
            return toNamedEnum("token_pause_status", source == null ? null : source.name());
        }
    }

    @ReadingConverter
    public static final class PGobjectToTokenPauseStatusEnum implements Converter<PGobject, TokenPauseStatusEnum> {

        @Override
        public TokenPauseStatusEnum convert(PGobject source) {
            return fromNamedEnum(source, TokenPauseStatusEnum.class);
        }
    }

    @ReadingConverter
    public static final class StringToTokenPauseStatusEnum implements Converter<String, TokenPauseStatusEnum> {

        @Override
        public TokenPauseStatusEnum convert(String source) {
            return source == null || source.isEmpty() ? null : TokenPauseStatusEnum.valueOf(source);
        }
    }

    @WritingConverter
    public static final class TokenSupplyTypeEnumToJdbcValue implements Converter<TokenSupplyTypeEnum, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(TokenSupplyTypeEnum source) {
            return toNamedEnum("token_supply_type", source == null ? null : source.name());
        }
    }

    @ReadingConverter
    public static final class PGobjectToTokenSupplyTypeEnum implements Converter<PGobject, TokenSupplyTypeEnum> {

        @Override
        public TokenSupplyTypeEnum convert(PGobject source) {
            return fromNamedEnum(source, TokenSupplyTypeEnum.class);
        }
    }

    @ReadingConverter
    public static final class StringToTokenSupplyTypeEnum implements Converter<String, TokenSupplyTypeEnum> {

        @Override
        public TokenSupplyTypeEnum convert(String source) {
            return source == null || source.isEmpty() ? null : TokenSupplyTypeEnum.valueOf(source);
        }
    }

    @WritingConverter
    public static final class TokenTypeEnumToJdbcValue implements Converter<TokenTypeEnum, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(TokenTypeEnum source) {
            return toNamedEnum("token_type", source == null ? null : source.name());
        }
    }

    @ReadingConverter
    public static final class PGobjectToTokenTypeEnum implements Converter<PGobject, TokenTypeEnum> {

        @Override
        public TokenTypeEnum convert(PGobject source) {
            return fromNamedEnum(source, TokenTypeEnum.class);
        }
    }

    @ReadingConverter
    public static final class StringToTokenTypeEnum implements Converter<String, TokenTypeEnum> {

        @Override
        public TokenTypeEnum convert(String source) {
            return source == null || source.isEmpty() ? null : TokenTypeEnum.valueOf(source);
        }
    }

    @SneakyThrows
    private static JdbcValue toNamedEnum(String pgType, String value) {
        if (value == null) {
            return JdbcValue.of(null, JDBCType.OTHER);
        }
        var pg = new PGobject();
        pg.setType(pgType);
        pg.setValue(value);
        return JdbcValue.of(pg, JDBCType.OTHER);
    }

    private static <E extends Enum<E>> E fromNamedEnum(PGobject source, Class<E> type) {
        if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
            return null;
        }
        return Enum.valueOf(type, source.getValue());
    }
}
