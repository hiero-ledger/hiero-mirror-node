// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class ContractActionBuilder extends AbstractEntityBuilder<ContractAction, ContractAction.ContractActionBuilder> {
    @Override
    protected ContractAction.ContractActionBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractAction.builder()
                .callDepth(1)
                .callOperationType(1)
                .callType(1)
                .caller(getScopedEntityId(8001))
                .callerType(EntityType.CONTRACT)
                .consensusTimestamp(DEFAULT_CONSENSUS_TIMESTAMP)
                .gas(10000L)
                .gasUsed(5000L)
                .index(1)
                .payerAccountId(DEFAULT_PAYER_ACCOUNT_ID)
                .resultDataType(11)
                .value(100L);
    }

    @Override
    protected ContractAction getFinalEntity(
            ContractAction.ContractActionBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::contractactions;
    }
}
