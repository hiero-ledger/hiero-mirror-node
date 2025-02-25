// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class ContractBuilder extends AbstractEntityBuilder<Contract, Contract.ContractBuilder<?, ?>> {
    private static final Map<String, String> ATTRIBUTE_NAME_MAP = Map.of("num", "id");

    public ContractBuilder() {
        super(Map.of(), ATTRIBUTE_NAME_MAP);
    }

    @Override
    protected Contract.ContractBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return Contract.builder();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> Optional.ofNullable(specSetup.contracts()).orElse(List.of()).stream()
                .filter(contract -> !isHistory(contract))
                .toList();
    }

    @Override
    protected Contract getFinalEntity(Contract.ContractBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }
}
