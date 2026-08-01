// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

public final class PostgresAirdropStateJdbcConverters {

    private PostgresAirdropStateJdbcConverters() {}

    @WritingConverter
    public static final class TokenAirdropStateEnumToJdbcValue implements Converter<TokenAirdropStateEnum, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(TokenAirdropStateEnum source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.OTHER);
            }
            var pg = new PGobject();
            pg.setType("airdrop_state");
            pg.setValue(source.name());
            return JdbcValue.of(pg, JDBCType.OTHER);
        }
    }

    @ReadingConverter
    public static final class PGobjectToTokenAirdropStateEnum implements Converter<PGobject, TokenAirdropStateEnum> {

        @Override
        public TokenAirdropStateEnum convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return TokenAirdropStateEnum.valueOf(source.getValue());
        }
    }

    @ReadingConverter
    public static final class StringToTokenAirdropStateEnum implements Converter<String, TokenAirdropStateEnum> {

        @Override
        public TokenAirdropStateEnum convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return TokenAirdropStateEnum.valueOf(source);
        }
    }
}
