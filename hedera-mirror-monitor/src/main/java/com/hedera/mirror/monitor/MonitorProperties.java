// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Named
@Data
@EnableConfigurationProperties(CommonProperties.class)
@Validated
@ConfigurationProperties("hedera.mirror.monitor")
public class MonitorProperties {

    private final AccountIdValidator accountIdValidator;

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

    @PostConstruct
    void init() {
        operator.setAccountId(accountIdValidator.validate(operator.getAccountId()));
    }
}
