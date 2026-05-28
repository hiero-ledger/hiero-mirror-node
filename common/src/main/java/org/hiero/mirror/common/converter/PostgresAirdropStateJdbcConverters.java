// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.token.PostgresAirdropState;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class PostgresAirdropStateJdbcConverters {

    private PostgresAirdropStateJdbcConverters() {}

    @WritingConverter
    public static final class PostgresAirdropStateToPGobject implements Converter<PostgresAirdropState, PGobject> {

        @Override
        @SneakyThrows
        public PGobject convert(PostgresAirdropState source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("airdrop_state");
            pg.setValue(source.getState().name());
            return pg;
        }
    }

    @ReadingConverter
    public static final class PGobjectToPostgresAirdropState implements Converter<PGobject, PostgresAirdropState> {

        @Override
        public PostgresAirdropState convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return PostgresAirdropState.of(TokenAirdropStateEnum.valueOf(source.getValue()));
        }
    }

    @ReadingConverter
    public static final class StringToPostgresAirdropState implements Converter<String, PostgresAirdropState> {

        @Override
        public PostgresAirdropState convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return PostgresAirdropState.of(TokenAirdropStateEnum.valueOf(source));
        }
    }
}
