// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class TokenAllowanceBuilder extends AbstractEntityBuilder<TokenAllowance, TokenAllowance.TokenAllowanceBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::tokenAllowances;
    }

    @Override
    protected TokenAllowance.TokenAllowanceBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return TokenAllowance.builder()
                .amount(0L)
                .amountGranted(0L)
                .owner(1000L)
                .payerAccountId(EntityId.of(1000L))
                .spender(2000L)
                .timestampRange(Range.atLeast(0L))
                .tokenId(3000L);
    }

    @Override
    protected TokenAllowance getFinalEntity(
            TokenAllowance.TokenAllowanceBuilder<?, ?> builder, Map<String, Object> account) {
        return builder.build();
    }
}
