// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import static org.hiero.mirror.monitor.OperatorProperties.DEFAULT_OPERATOR_ACCOUNT_ID;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import org.hiero.mirror.common.CommonProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@EnableConfigurationProperties(CommonProperties.class)
@Validated
@ConfigurationProperties("hiero.mirror.monitor")
public class MonitorProperties {

    @Resource
    private CommonProperties commonProperties;

    @Nullable
    @Valid
    private MirrorNodeProperties mirrorNode;

    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @NotNull
    @Valid
    private Set<NodeProperties> nodes = new LinkedHashSet<>();

    @NotNull
    @Valid
    private OperatorProperties operator = new OperatorProperties();

    @NotNull
    @Valid
    private NodeValidationProperties nodeValidation = new NodeValidationProperties();

    public MirrorNodeProperties getMirrorNode() {
        return Objects.requireNonNullElseGet(this.mirrorNode, network::getMirrorNode);
    }

    /**
     * Checks if the operator account id has matching shard and realm. In case of mismatch, if the operator account id
     * is the default, updates its shard and realm, otherwise throws exception.
     */
    @PostConstruct
    void init() {
        var accountId = operator.getAccountId().split("\\.", 3);
        if (accountId == null || accountId.length != 3) {
            throw new IllegalArgumentException("Invalid operator account ID");
        }

        long shard = commonProperties.getShard();
        long realm = commonProperties.getRealm();

        if (Long.valueOf(accountId[0]) == shard && Long.valueOf(accountId[1]) == realm) {
            return;
        }

        if (DEFAULT_OPERATOR_ACCOUNT_ID.equals(operator.getAccountId())) {
            operator.setAccountId("%d.%d.%s".formatted(shard, realm, accountId[2]));
        } else {
            throw new IllegalArgumentException(
                    "Operator account id %s has invalid shard/realm, expect shard=%d and realm=%d"
                            .formatted(accountId, shard, realm));
        }
    }
}
