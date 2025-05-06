// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class ContractLogBuilder extends AbstractEntityBuilder<ContractLog, ContractLog.ContractLogBuilder> {
    @Override
    protected ContractLog.ContractLogBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractLog.builder()
                .bloom(HEX_FORMAT.parseHex("0123"))
                .consensusTimestamp(DEFAULT_CONSENSUS_TIMESTAMP)
                .contractId(DEFAULT_CONTRACT_ID)
                .data(HEX_FORMAT.parseHex("0123"))
                .index(0)
                .payerAccountId(DEFAULT_PAYER_ACCOUNT_ID)
                .topic0(HEX_FORMAT.parseHex("97c1fc0a6ed5551bc831571325e9bdb365d06803100dc20648640ba24ce69750"))
                .topic1(HEX_FORMAT.parseHex("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"))
                .topic2(HEX_FORMAT.parseHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"))
                .topic3(HEX_FORMAT.parseHex("e8d47b56e8cdfa95f871b19d4f50a857217c44a95502b0811a350fec1500dd67"))
                .transactionHash(DEFAULT_TRANSACTION_HASH)
                .transactionIndex(0);
    }

    @Override
    protected ContractLog getFinalEntity(ContractLog.ContractLogBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::contractlogs;
    }
}
