// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.token.AbstractCustomFee;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.CustomFeeHistory;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class CustomFeeBuilder
        extends AbstractEntityBuilder<AbstractCustomFee, AbstractCustomFee.AbstractCustomFeeBuilder<?, ?>> {

    @Override
    protected AbstractCustomFee.AbstractCustomFeeBuilder<?, ?> getEntityBuilder(SpecBuilderContext builderContext) {
        return builderContext.isHistory() ? CustomFeeHistory.builder() : CustomFee.builder();
    }

    @Override
    protected AbstractCustomFee getFinalEntity(
            AbstractCustomFee.AbstractCustomFeeBuilder<?, ?> builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return () -> {
            var tokens = specSetup.tokens();
            if (tokens == null) {
                return List.of();
            }

            return tokens.stream()
                    .map(this::getTokenCustomFees)
                    .flatMap(Collection::stream)
                    .map(fee -> toCustomFee(fee, new SpecBuilderContext(fee)))
                    .toList();
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toCustomFee(Map<String, Object> entityAttributes, SpecBuilderContext context) {
        var fixedFees = Optional.ofNullable((List<Map<String, Object>>) entityAttributes.get("fixed_fees"))
                .map(list -> list.stream()
                        .map(fixedFee -> {
                            var builder = FixedFee.builder();
                            customizeWithSpec(builder, fixedFee, context);
                            return builder.build();
                        })
                        .toList())
                .orElse(null);

        var fractionalFees = Optional.ofNullable((List<Map<String, Object>>) entityAttributes.get("fractional_fees"))
                .map(list -> list.stream()
                        .map(fractionalFee -> {
                            var builder = FractionalFee.builder();
                            customizeWithSpec(builder, fractionalFee, context);
                            return builder.build();
                        })
                        .toList())
                .orElse(null);

        var royaltyFees = Optional.ofNullable((List<Map<String, Object>>) entityAttributes.get("royalty_fees"))
                .map(list -> list.stream()
                        .map(royaltyFee -> {
                            var builder = RoyaltyFee.builder();
                            var fallbackFee = (Map<String, Object>) royaltyFee.get("fallback_fee");
                            if (fallbackFee != null) {
                                var fallbackBuilder = FallbackFee.builder();
                                customizeWithSpec(fallbackBuilder, fallbackFee, new SpecBuilderContext(fallbackFee));
                                builder.fallbackFee(fallbackBuilder.build());
                                royaltyFee = new HashMap<>(royaltyFee);
                                royaltyFee.put("fallback_fee", fallbackBuilder.build());
                            }
                            customizeWithSpec(builder, royaltyFee, context);
                            return builder.build();
                        })
                        .toList())
                .orElse(null);

        Map<String, Object> customFee = new HashMap<>(entityAttributes);
        customFee.put("fixed_fees", fixedFees);
        customFee.put("fractional_fees", fractionalFees);
        customFee.put("royalty_fees", royaltyFees);

        return customFee;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTokenCustomFees(Map<String, Object> entityAttributes) {
        if (entityAttributes.get("custom_fees") == null) {
            Map<String, Object> customFee = new HashMap<>();
            customFee.put("entity_id", entityAttributes.get("token_id"));
            customFee.put(
                    "timestamp_range",
                    Range.atLeast(Long.parseLong(entityAttributes
                            .getOrDefault("created_timestamp", 0)
                            .toString())));
            return List.of(customFee);
        } else {
            return (List<Map<String, Object>>) entityAttributes.get("custom_fees");
        }
    }
}
