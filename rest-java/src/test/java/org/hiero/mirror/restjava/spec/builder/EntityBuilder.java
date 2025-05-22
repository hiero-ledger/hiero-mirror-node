// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class EntityBuilder extends AbstractEntityBuilder<AbstractEntity, AbstractEntity.AbstractEntityBuilder<?, ?>> {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "alias", BASE32_CONVERTER,
            "evmAddress", HEX_OR_BASE64_CONVERTER,
            "key", HEX_OR_BASE64_CONVERTER);

    EntityBuilder() {
        super(METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {

        return () -> {
            var entities = specSetup.entities();

            if (entities == null) {
                entities = new ArrayList<>();
            }

            var contracts = specSetup.contracts();

            if (contracts != null) {
                for (var contract : contracts) {
                    var num = contract.getOrDefault("num", 0L);
                    var contractEntity = new HashMap<String, Object>();
                    contractEntity.put("max_automatic_token_associations", 0);
                    contractEntity.put("memo", "contract memo");
                    contractEntity.put("num", num);
                    contractEntity.put("type", EntityType.CONTRACT);
                    contractEntity.put("receiver_sig_required", null);

                    contractEntity.putAll(contract);
                    entities.add(contractEntity);
                }
            }

            return entities;
        };
    }

    @Override
    protected AbstractEntity.AbstractEntityBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        var builder = builderContext.isHistory() ? EntityHistory.builder() : Entity.builder();
        return builder.declineReward(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .memo("entity memo")
                .num(0L)
                .realm(0L)
                .receiverSigRequired(Boolean.FALSE)
                .shard(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.atLeast(0L))
                .type(EntityType.ACCOUNT);
    }

    @Override
    protected AbstractEntity getFinalEntity(
            AbstractEntity.AbstractEntityBuilder<?, ?> builder, Map<String, Object> account) {
        var entity = builder.build();
        if (entity.getId() == null) {
            entity.setId(entity.toEntityId().getId());

            // Work around entity builder resetting the public key when setting key
            var publicKeyOverride = account.get("public_key");
            if (entity.getPublicKey() == null && publicKeyOverride != null) {
                entity.setPublicKey(publicKeyOverride.toString());
            }
        }
        return entity;
    }
}
