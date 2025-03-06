// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.subscribe.rest.RestApiClient;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.Duration;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
public class ShardRealmProbe {

    private final MonitorProperties properties;
    private final RestApiClient restApiClient;

    /**
     * Probes the network's shard and realm. Correct the configured operator account id if possible and needed.
     */
    @PostConstruct
    void init() {
        var node = restApiClient.getNodes().blockFirst(Duration.ofSeconds(30));
        if (node == null) {
            throw new IllegalStateException("Unable to get network nodes");
        }

        var nodeAccountId = EntityId.of(node.getNodeAccountId());
        var validator = new AccountIdValidator(nodeAccountId.getShard(), nodeAccountId.getRealm());
        var operator = properties.getOperator();
        String accountId = validator.validate(operator.getAccountId());
        if (!operator.getAccountId().equals(accountId)) {
            log.info("Set operator account id to {} with shard / realm to match the network", accountId);
            properties.getOperator().setAccountId(accountId);
        }

        TransactionSupplier.ACCOUNT_ID_VALIDATOR.set(validator);
    }
}
