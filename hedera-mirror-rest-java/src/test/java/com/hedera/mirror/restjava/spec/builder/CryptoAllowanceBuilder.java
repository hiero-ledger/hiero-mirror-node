// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class CryptoAllowanceBuilder
        extends AbstractEntityBuilder<CryptoAllowance, CryptoAllowance.CryptoAllowanceBuilder<?, ?>> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::cryptoAllowances;
    }

    @Override
    protected CryptoAllowance.CryptoAllowanceBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return CryptoAllowance.builder()
                .amount(0L)
                .amountGranted(0L)
                .owner(1000L)
                .payerAccountId(EntityId.of(101L))
                .spender(2000L)
                .timestampRange(Range.atLeast(0L));
    }

    @Override
    protected CryptoAllowance getFinalEntity(
            CryptoAllowance.CryptoAllowanceBuilder<?, ?> builder, Map<String, Object> account) {
        return builder.build();
    }
}
