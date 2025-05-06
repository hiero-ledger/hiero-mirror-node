// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class TransactionHashBuilder
        extends AbstractEntityBuilder<TransactionHash, TransactionHash.TransactionHashBuilder> {
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
        return specSetup::transactionhashes;
    }
}
