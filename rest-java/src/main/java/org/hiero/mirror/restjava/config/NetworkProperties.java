// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.StringJoiner;
import lombok.Data;
import lombok.Getter;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java.network")
public class NetworkProperties {

    @NotEmpty
    private List<AccountRange> unreleasedSupplyAccounts = List.of(
            new AccountRange(2, 2),
            new AccountRange(42, 42),
            new AccountRange(44, 71),
            new AccountRange(73, 87),
            new AccountRange(99, 100),
            new AccountRange(200, 349),
            new AccountRange(400, 750));

    @Getter(lazy = true)
    private final RangeBounds unreleasedSupplyRangeBounds = createUnreleasedSupplyRangeBounds();

    private RangeBounds createUnreleasedSupplyRangeBounds() {
        final var commonProperties = CommonProperties.getInstance();
        final var shard = commonProperties.getShard();
        final var realm = commonProperties.getRealm();

        final var lowerBoundJoiner = new StringJoiner(",");
        final var upperBoundJoiner = new StringJoiner(",");

        for (final var range : unreleasedSupplyAccounts) {
            final long from = EntityId.of(shard, realm, range.from()).getId();
            final long to = EntityId.of(shard, realm, range.to()).getId();

            lowerBoundJoiner.add(Long.toString(from));
            upperBoundJoiner.add(Long.toString(to));
        }

        return new RangeBounds(lowerBoundJoiner.toString(), upperBoundJoiner.toString());
    }

    public record AccountRange(@Min(1) long from, @Min(1) long to) {
        public AccountRange {
            if (from > to) {
                throw new IllegalArgumentException("from must be less than or equal to to");
            }
        }
    }

    public record RangeBounds(String lowerBounds, String upperBounds) {}
}
