// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
public class ContractResultBuilder
        extends AbstractEntityBuilder<ContractResult, ContractResult.ContractResultBuilder<?, ?>> {
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of();

    public ContractResultBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected ContractResult.ContractResultBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractResult.builder()
                .amount(0L)
                .consensusTimestamp(1234510001L)
                .errorMessage("")
                .functionParameters(HexFormat.of().parseHex("010102020303"))
                .gasLimit(1000L)
                .payerAccountId(EntityId.of(DEFAULT_PAYER_ACCOUNT_ID))
                .senderId(EntityId.of(DEFAULT_SENDER_ID))
                .transactionHash(
                        HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"))
                .transactionIndex(1)
                .transactionNonce(0)
                .transactionResult(22);
    }

    @Override
    protected ContractResult getFinalEntity(
            ContractResult.ContractResultBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::contractresults;
    }
}
