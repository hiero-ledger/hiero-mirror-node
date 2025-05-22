// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class TokenAccountBuilder extends AbstractEntityBuilder<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::tokenAccounts;
    }

    @Override
    protected TokenAccount.TokenAccountBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return TokenAccount.builder()
                .accountId(0L)
                .associated(Boolean.TRUE)
                .automaticAssociation(Boolean.FALSE)
                .balance(0L)
                .balanceTimestamp(0L)
                .createdTimestamp(0L)
                .tokenId(0L);
    }

    @Override
    protected TokenAccount getFinalEntity(TokenAccount.TokenAccountBuilder<?, ?> builder, Map<String, Object> account) {
        var entity = builder.build();
        if (entity.getTimestampRange() == null) {
            builder.timestampRange(Range.atLeast(entity.getCreatedTimestamp()));
            entity = builder.build();
        }
        return entity;
    }
}
