// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.AbstractEntityStake;
import com.hedera.mirror.common.domain.entity.EntityStake;
import com.hedera.mirror.common.domain.entity.EntityStakeHistory;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
public class EntityStakeBuilder
        extends AbstractEntityBuilder<AbstractEntityStake, AbstractEntityStake.AbstractEntityStakeBuilder<?, ?>> {
    private static final long SECONDS_PER_DAY = 86400;

    @Override
    protected AbstractEntityStake.AbstractEntityStakeBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        var builder = builderContext.isHistory() ? EntityStakeHistory.builder() : EntityStake.builder();
        return builder.endStakePeriod(1)
                .pendingReward(0)
                .stakedNodeIdStart(1)
                .stakedToMe(0)
                .stakeTotalStart(0);
    }

    @Override
    protected AbstractEntityStake getFinalEntity(
            AbstractEntityStake.AbstractEntityStakeBuilder<?, ?> builder, Map<String, Object> entityAttributes) {

        var entityStake = builder.build();
        if (entityStake.getTimestampRange() == null) {
            var seconds = SECONDS_PER_DAY * (entityStake.getEndStakePeriod() + 1);
            var lowerBound = seconds * DomainUtils.NANOS_PER_SECOND + 1;
            entityStake.setTimestampRange(Range.atLeast(lowerBound));
        }
        return entityStake;
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::entityStakes;
    }
}
