// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Named
public class TransactionHashBuilder
        extends AbstractEntityBuilder<TransactionHash, TransactionHash.TransactionHashBuilder> {
    private static final Map<String, BiFunction<Object, SpecBuilderContext, Object>> METHOD_PARAMETER_CONVERTERS =
            Map.of("hash", HEX_OR_BASE64_CONVERTER);

    public TransactionHashBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected TransactionHash.TransactionHashBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return TransactionHash.builder();
    }

    @Override
    protected TransactionHash getFinalEntity(
            TransactionHash.TransactionHashBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> {
            List<Map<String, Object>> transactionHashes = new ArrayList<>(
                    Optional.ofNullable(specSetup.transactionhashes()).orElse(Collections.emptyList()));
            var generated = Optional.ofNullable(specSetup.transactions()).orElse(Collections.emptyList()).stream()
                    .map(transaction -> Map.of(
                            "consensus_timestamp", transaction.get("consensus_timestamp"),
                            "hash", transaction.getOrDefault("transaction_hash", DEFAULT_TRANSACTION_HASH),
                            "payer_account_id",
                                    transaction.getOrDefault("payer_account_id", transaction.get("payerAccountId"))))
                    .toList();
            transactionHashes.addAll(generated);

            return transactionHashes;
        };
    }
}
