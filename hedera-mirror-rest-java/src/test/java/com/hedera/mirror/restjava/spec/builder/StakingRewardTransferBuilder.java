// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
