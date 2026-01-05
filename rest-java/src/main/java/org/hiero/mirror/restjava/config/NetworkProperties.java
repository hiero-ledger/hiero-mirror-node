// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
    private final Set<Long> unreleasedSupplyAccountIds = createUnreleasedSupplyAccountIds();

    private Set<Long> createUnreleasedSupplyAccountIds() {
        final var commonProperties = CommonProperties.getInstance();
        final var shard = commonProperties.getShard();
        final var realm = commonProperties.getRealm();
        final var accountIds = new TreeSet<Long>();

        for (final var range : unreleasedSupplyAccounts) {
            for (long num = range.from(); num <= range.to(); num++) {
                accountIds.add(EntityId.of(shard, realm, num).getId());
            }
        }

        return accountIds;
    }

    public record AccountRange(@Min(1) long from, @Min(1) long to) {
        public AccountRange {
            if (from > to) {
                throw new IllegalArgumentException("from must be less than or equal to to");
            }
        }
    }

    /**
     * Builds a WHERE clause condition for unreleased supply accounts using range-based predicates.
     * More efficient than using IN clause as it allows PostgreSQL to use range scans on indexes.
     *
     * @param column the column name to use in the WHERE clause (e.g., "id", "account_id")
     * @return a WHERE clause condition string like "(id >= X and id <= Y) or (id = Z) or ..."
     */
    public String buildUnreleasedSupplyAccountsWhereClause(String column) {
        return unreleasedSupplyAccounts.stream()
                .map(range -> {
                    final var from = range.from;
                    final var to = range.to;

                    if (from == to) {
                        return String.format("%s = %d", column, from);
                    } else {
                        return String.format("(%s >= %d and %s <= %d)", column, from, column, to);
                    }
                })
                .collect(java.util.stream.Collectors.joining(" or "));
    }
}
