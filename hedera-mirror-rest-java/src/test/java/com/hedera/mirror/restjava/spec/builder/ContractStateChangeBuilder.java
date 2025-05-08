// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class ContractStateChangeBuilder
        extends AbstractEntityBuilder<ContractStateChange, ContractStateChange.ContractStateChangeBuilder> {

    @Override
    protected ContractStateChange.ContractStateChangeBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractStateChange.builder()
                .consensusTimestamp(DEFAULT_CONSENSUS_TIMESTAMP)
                .contractId(DEFAULT_CONTRACT_ID.getId())
                .payerAccountId(getScopedEntityId(2))
                .slot(HEX_FORMAT.parseHex("01"))
                .valueRead(HEX_FORMAT.parseHex("0101"))
                .valueWritten(HEX_FORMAT.parseHex("a1a1"));
    }

    @Override
    protected ContractStateChange getFinalEntity(
            ContractStateChange.ContractStateChangeBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::contractStateChanges;
    }
}
