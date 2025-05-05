// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import com.hedera.hashgraph.sdk.Client;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;

@Getter
public enum HederaNetwork {
    MAINNET("mainnet-public"),
    PREVIEWNET("previewnet"),
    TESTNET("testnet"),
    OTHER("");

    @Getter(lazy = true)
    private final Set<NodeProperties> nodes = nodes();

    private final MirrorNodeProperties mirrorNode;

    HederaNetwork(String network) {
        this.mirrorNode = mirrorNode(network);
    }

    private MirrorNodeProperties mirrorNode(String environment) {
        String host = environment + ".mirrornode.hedera.com";
        MirrorNodeProperties mirrorNodeProperties = new MirrorNodeProperties();
        mirrorNodeProperties.getGrpc().setHost(host);
        mirrorNodeProperties.getRest().setHost(host);
        return mirrorNodeProperties;
    }

    @SneakyThrows
    private Set<NodeProperties> nodes() {
        if (this == OTHER) {
            return Set.of();
        }

        try (var client = Client.forName(name().toLowerCase())) {
            return client.getNetwork().entrySet().stream()
                    .map(e -> new NodeProperties(e.getValue().toString(), e.getKey()))
                    .collect(Collectors.toSet());
        }
    }
}
