// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.AbstractToken;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenHistory;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
public class TokenBuilder extends AbstractEntityBuilder<AbstractToken, AbstractToken.AbstractTokenBuilder<?, ?>> {
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> PARAMETER_CONVERTERS =
            Map.of("metadata", RAW_BYTES_CONVERTER, "supplyKey", HEX_OR_BASE64_CONVERTER);

    public TokenBuilder() {
        super(PARAMETER_CONVERTERS);
    }

    @Override
    protected AbstractToken.AbstractTokenBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        var builder = builderContext.isHistory() ? TokenHistory.builder() : Token.builder();
        return builder.createdTimestamp(0L)
                .decimals(1000)
                .freezeDefault(false)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .initialSupply(1000000L)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .maxSupply(9223372036854775807L)
                .metadata(new byte[0])
                .name("Token name")
                .pauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE)
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .totalSupply(1000000L)
                .treasuryAccountId(EntityId.of("0.0.98"))
                .type(TokenTypeEnum.FUNGIBLE_COMMON);
    }

    @Override
    protected AbstractToken getFinalEntity(
            AbstractToken.AbstractTokenBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        var token = builder.build();

        if (TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(token.getType())) {
            token.setDecimals(0);
            token.setInitialSupply(0L);
        }
        if (token.getTimestampRange() == null) {
            token.setTimestampRange(Range.atLeast(token.getCreatedTimestamp()));
        }

        return token;
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::tokens;
    }
}
