// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class AssessedCustomFeesBuilder
        extends AbstractEntityBuilder<AssessedCustomFee, AssessedCustomFee.AssessedCustomFeeBuilder> {
    @Override
    protected AssessedCustomFee.AssessedCustomFeeBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return AssessedCustomFee.builder().payerAccountId(getScopedEntityId(300L));
    }

    @Override
    protected AssessedCustomFee getFinalEntity(
            AssessedCustomFee.AssessedCustomFeeBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::assessedCustomFees;
    }
}
