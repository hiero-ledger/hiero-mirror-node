// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class StakingRewardTransferBuilder
        extends AbstractEntityBuilder<StakingRewardTransfer, StakingRewardTransfer.StakingRewardTransferBuilder> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::stakingRewardTransfers;
    }

    @Override
    protected StakingRewardTransfer.StakingRewardTransferBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return StakingRewardTransfer.builder().accountId(1001L).amount(100L).payerAccountId(EntityId.of(950L));
    }

    @Override
    protected StakingRewardTransfer getFinalEntity(
            StakingRewardTransfer.StakingRewardTransferBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
