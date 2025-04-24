// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class AccountBalanceBuilder extends AbstractEntityBuilder<AccountBalance, AccountBalance.AccountBalanceBuilder> {
    private final CommonProperties commonProperties;

    public AccountBalanceBuilder(CommonProperties commonProperties) {
        super();
        this.commonProperties = commonProperties;
    }

    @Override
    protected AccountBalance.AccountBalanceBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return AccountBalance.builder();
    }

    @Override
    protected AccountBalance getFinalEntity(
            AccountBalance.AccountBalanceBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        if (specSetup.balances() == null) {
            return Collections::emptyList;
        }

        var balances = new ArrayList<Map<String, Object>>();

        for (var balance : specSetup.balances()) {
            var timestamp = Long.parseLong(balance.getOrDefault("timestamp", 0).toString());
            var shard = commonProperties.getShard();
            var realm = Long.parseLong(balance.getOrDefault("realm_num", commonProperties.getRealm())
                    .toString());
            var num = Long.parseLong(balance.getOrDefault("id", 0).toString());

            var copy = new HashMap<>(balance);
            copy.put("id", new AccountBalance.Id(timestamp, EntityId.of(shard, realm, num)));
            balances.add(copy);
        }
        return () -> balances;
    }
}
