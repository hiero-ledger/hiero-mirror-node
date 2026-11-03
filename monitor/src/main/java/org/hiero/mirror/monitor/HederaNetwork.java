// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Endpoint;
import com.hedera.hashgraph.sdk.NodeAddress;
import com.hedera.hashgraph.sdk.NodeAddressBook;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

@Getter
public enum HederaNetwork {
    MAINNET("mainnet-public"),
    PREVIEWNET("previewnet"),
    TESTNET("testnet"),
    OTHER("");

    // One entry per endpoint in the address book bundled with the SDK, carrying its bundled certificate hash
    @Getter(lazy = true)
    private final List<NodeProperties> nodes = nodes();

    // Node account ID -> certificate hash from the address book bundled with the SDK. Independent of the mirror node,
    // so it is the trust anchor for nodes discovered via the mirror node REST API. Empty for OTHER.
    @Getter(lazy = true)
    private final Map<String, String> trustedCertHashes = trustedCertHashes();

    private final MirrorNodeProperties mirrorNode;

    HederaNetwork(String network) {
        this.mirrorNode = mirrorNode(network);
    }

    public static String normalizeCertHash(final String certHash) {
        return StringUtils.lowerCase(Strings.CS.removeStart(certHash, "0x"));
    }

    @SneakyThrows
    private List<NodeAddress> loadAddressBook() {
        if (this == OTHER) {
            return List.of();
        }

        // Same resource the SDK uses for Client.forMainnet() etc.
        final var resource = "/addressbook/" + name().toLowerCase() + ".pb";
        try (final var inputStream = Objects.requireNonNull(
                Client.class.getResourceAsStream(resource), "Missing SDK address book " + resource)) {
            return NodeAddressBook.fromBytes(ByteString.readFrom(inputStream)).getNodeAddresses().stream()
                    .filter(n -> n.getAccountId() != null)
                    .toList();
        }
    }

    private MirrorNodeProperties mirrorNode(String environment) {
        String host = environment + ".mirrornode.hedera.com";
        MirrorNodeProperties mirrorNodeProperties = new MirrorNodeProperties();
        mirrorNodeProperties.getGrpc().setHost(host);
        mirrorNodeProperties.getRest().setHost(host);
        return mirrorNodeProperties;
    }

    private List<NodeProperties> nodes() {
        return loadAddressBook().stream()
                .flatMap(n -> n.getAddresses().stream().map(e -> toNodeProperties(n, e)))
                .toList();
    }

    private static String toCertHash(final NodeAddress nodeAddress) {
        final var certHash = nodeAddress.getCertHash();
        return certHash == null ? "" : normalizeCertHash(certHash.toStringUtf8());
    }

    @SneakyThrows
    private static NodeProperties toNodeProperties(final NodeAddress nodeAddress, final Endpoint endpoint) {
        // Host and port are kept separate; Endpoint.toString() would put both into the host
        final var host = StringUtils.isNotBlank(endpoint.getDomainName())
                ? endpoint.getDomainName()
                : InetAddress.getByAddress(endpoint.getAddress()).getHostAddress();
        final var certHash = toCertHash(nodeAddress);
        final var nodeProperties = new NodeProperties(nodeAddress.getAccountId().toString(), host);
        nodeProperties.setCertHash(certHash.isEmpty() ? null : certHash);
        nodeProperties.setNodeId(nodeAddress.getNodeId());
        nodeProperties.setPort(endpoint.getPort());
        return nodeProperties;
    }

    private Map<String, String> trustedCertHashes() {
        return loadAddressBook().stream()
                .filter(n -> !toCertHash(n).isEmpty())
                .collect(Collectors.toUnmodifiableMap(
                        n -> n.getAccountId().toString(), HederaNetwork::toCertHash, (a, _) -> a));
    }
}
