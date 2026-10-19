// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class HederaNetworkTest {

    @Test
    void normalizeCertHash() {
        assertThat(HederaNetwork.normalizeCertHash("0xABcd")).isEqualTo("abcd");
        assertThat(HederaNetwork.normalizeCertHash("abcd")).isEqualTo("abcd");
        assertThat(HederaNetwork.normalizeCertHash(null)).isNull();
    }

    @Test
    void nodesOther() {
        assertThat(HederaNetwork.OTHER.getNodes()).isEmpty();
    }

    // Host and port must be separate and each endpoint must carry the node's bundled certificate hash
    @ParameterizedTest
    @EnumSource(
            value = HederaNetwork.class,
            names = {"MAINNET", "PREVIEWNET", "TESTNET"})
    void nodes(final HederaNetwork network) {
        final var trustedCertHashes = network.getTrustedCertHashes();
        assertThat(network.getNodes())
                .isNotEmpty()
                .allSatisfy(n -> {
                    assertThat(n.getCertHash()).isEqualTo(trustedCertHashes.get(n.getAccountId()));
                    assertThat(n.getHost()).doesNotContain(":");
                    assertThat(n.getPort()).isIn(50211, 50212);
                })
                .extracting(NodeProperties::getPort)
                .contains(50211, 50212);
    }

    @Test
    void trustedCertHashesOther() {
        assertThat(HederaNetwork.OTHER.getTrustedCertHashes()).isEmpty();
    }

    // Fails if a future SDK moves or drops its bundled address books, which would disable pinning
    @ParameterizedTest
    @EnumSource(
            value = HederaNetwork.class,
            names = {"MAINNET", "PREVIEWNET", "TESTNET"})
    void trustedCertHashes(final HederaNetwork network) {
        assertThat(network.getTrustedCertHashes())
                .isNotEmpty()
                .containsKey("0.0.3")
                .allSatisfy((accountId, certHash) -> assertThat(certHash).matches("[0-9a-f]{96}"));
    }
}
