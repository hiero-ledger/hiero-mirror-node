// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class ContractTransactionHashBuilder
        extends AbstractEntityBuilder<ContractTransactionHash, ContractTransactionHash.ContractTransactionHashBuilder> {
    @Override
    protected ContractTransactionHash.ContractTransactionHashBuilder getEntityBuilder(
            SpecBuilderContext builderContext) {
        return ContractTransactionHash.builder();
    }

    @Override
    protected ContractTransactionHash getFinalEntity(
            ContractTransactionHash.ContractTransactionHashBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> specSetup.contractresults() == null
                ? List.of()
                : specSetup.contractresults().stream()
                        .map(entity -> {
                            Map<String, Object> contractTransactionHash = new HashMap<>();
                            contractTransactionHash.put(
                                    "consensus_timestamp",
                                    entity.getOrDefault("consensus_timestamp", DEFAULT_CONSENSUS_TIMESTAMP));
                            contractTransactionHash.put("entity_id", entity.getOrDefault("contract_id", 0));
                            contractTransactionHash.put(
                                    "hash", entity.getOrDefault("transaction_hash", DEFAULT_CONTRACT_TRANSACTION_HASH));
                            contractTransactionHash.put(
                                    "payer_account_id",
                                    entity.getOrDefault("payer_account_id", DEFAULT_PAYER_ACCOUNT_ID.getId()));
                            contractTransactionHash.put(
                                    "transaction_result", entity.getOrDefault("transaction_result", 22));
                            return contractTransactionHash;
                        })
                        .toList();
    }
}
