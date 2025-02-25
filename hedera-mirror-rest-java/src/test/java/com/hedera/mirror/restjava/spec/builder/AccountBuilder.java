// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class AccountBuilder extends EntityBuilder {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::accounts;
    }

    @Override
    protected AbstractEntity.AbstractEntityBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return super.getEntityBuilder(builderContext)
                .maxAutomaticTokenAssociations(0)
                .publicKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f")
                .type(EntityType.ACCOUNT);
    }
}
