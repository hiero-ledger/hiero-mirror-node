// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class ContractStateBuilder extends AbstractEntityBuilder<ContractState, ContractState.ContractStateBuilder> {
    @Override
    protected ContractState.ContractStateBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractState.builder()
                .createdTimestamp(1664365660048674966L)
                .modifiedTimestamp(1664365660048674966L)
                .slot("0000000000000000000000000000000000000000000000000000000000000001".getBytes())
                .value("01".getBytes());
    }

    @Override
    protected ContractState getFinalEntity(
            ContractState.ContractStateBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::contractStates;
    }
}
