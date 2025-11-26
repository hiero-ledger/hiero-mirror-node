// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java.network")
public class NetworkProperties {

    @NotNull
    private List<AccountRange> unreleasedSupplyAccounts = List.of(
            new AccountRange(2, 2),
            new AccountRange(42, 42),
            new AccountRange(44, 71),
            new AccountRange(73, 87),
            new AccountRange(99, 100),
            new AccountRange(200, 349),
            new AccountRange(400, 750));

    /**
     * Get the unreleased supply account IDs as a list of entity IDs.
     *
     * @return List of EntityId objects representing unreleased supply accounts
     */
    public List<Long> getUnreleasedSupplyAccountIds() {
        final var commonProperties = CommonProperties.getInstance();
        final var shard = commonProperties.getShard();
        final var realm = commonProperties.getRealm();
        final var accountIds = new ArrayList<Long>();

        for (final var range : unreleasedSupplyAccounts) {
            for (long num = range.from(); num <= range.to(); num++) {
                accountIds.add(EntityId.of(shard, realm, num).getId());
            }
        }

        return accountIds;
    }

    public record AccountRange(long from, long to) {}
}
