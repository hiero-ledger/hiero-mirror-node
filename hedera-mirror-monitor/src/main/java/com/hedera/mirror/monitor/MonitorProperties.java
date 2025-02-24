// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.monitor")
public class MonitorProperties {

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
}
