// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class TokenBalanceBuilder extends AbstractEntityBuilder<TokenBalance, TokenBalance.TokenBalanceBuilder> {
    private final CommonProperties commonProperties;

    public TokenBalanceBuilder(CommonProperties commonProperties) {
        super();
        this.commonProperties = commonProperties;
    }

    @Override
    protected TokenBalance.TokenBalanceBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return TokenBalance.builder();
    }

    @Override
    protected TokenBalance getFinalEntity(
            TokenBalance.TokenBalanceBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        if (specSetup.balances() == null) {
            return Collections::emptyList;
        }

        return () -> specSetup.balances().stream()
                .map(this::getTokenBalances)
                .flatMap(Collection::stream)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTokenBalances(Map<String, Object> specEntity) {
        var tokens = specEntity.get("tokens");
        var shared = new HashMap<>(specEntity);
        shared.remove("tokens");

        if (tokens instanceof Collection) {
            return ((Collection<?>) tokens)
                    .stream()
                            .map(token -> {
                                if (token instanceof Map) {
                                    var tokenAttributes = (Map<String, Object>) token;
                                    Map<String, Object> merged = new HashMap<>(shared);
                                    merged.putAll(tokenAttributes);
                                    merged.put("id", getId(merged));
                                    return merged;
                                } else {
                                    throw new IllegalArgumentException("Invalid token definition");
                                }
                            })
                            .toList();
        }

        return Collections.emptyList();
    }

    // TODO// Use id builder like contract transaction
    private TokenBalance.Id getId(Map<String, Object> specEntity) {
        var consensusTimestamp =
                specEntity.getOrDefault("timestamp", specEntity.getOrDefault("consensus_timestamp", 1));
        var accountIdVal = specEntity.get("id");
        var tokenNum = Long.parseLong(specEntity.getOrDefault("token_num", 0).toString());
        var tokenRealm = Long.parseLong(specEntity
                .getOrDefault("token_realm_num", specEntity.getOrDefault("token_realm", commonProperties.getRealm()))
                .toString());
        var accountRealm = Long.parseLong(specEntity
                .getOrDefault("realm_num", commonProperties.getRealm())
                .toString());

        var tokenEntityId = EntityId.of(commonProperties.getShard(), tokenRealm, tokenNum);
        var accountEntityId =
                switch (accountIdVal) {
                    case String s -> EntityId.of(s);
                    case Number number ->
                        EntityId.of(commonProperties.getShard(), accountRealm, Long.parseLong(accountIdVal.toString()));
                    default -> throw new IllegalArgumentException("Invalid id type: " + accountIdVal.getClass());
                };

        return new TokenBalance.Id(Long.parseLong("" + consensusTimestamp), accountEntityId, tokenEntityId);
    }
}
