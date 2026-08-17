// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class IpWhitelistTest {

    @Test
    void empty() {
        var whitelist = IpWhitelist.parse(Set.of());
        assertThat(whitelist.isEmpty()).isTrue();
        assertThat(whitelist.contains("10.0.0.1")).isFalse();
    }

    @Test
    void exactIp() {
        var whitelist = IpWhitelist.parse(Set.of("10.0.0.1"));
        assertThat(whitelist.contains("10.0.0.1")).isTrue();
        assertThat(whitelist.contains("10.0.0.2")).isFalse();
    }

    @Test
    void ipv4Cidr() {
        var whitelist = IpWhitelist.parse(Set.of("10.244.0.0/16"));
        assertThat(whitelist.contains("10.244.1.20")).isTrue();
        assertThat(whitelist.contains("10.244.255.255")).isTrue();
        assertThat(whitelist.contains("10.245.0.1")).isFalse();
        assertThat(whitelist.contains("10.243.255.255")).isFalse();
    }

    @Test
    void ipv4CidrSlash32() {
        var whitelist = IpWhitelist.parse(Set.of("192.168.1.10/32"));
        assertThat(whitelist.contains("192.168.1.10")).isTrue();
        assertThat(whitelist.contains("192.168.1.11")).isFalse();
    }

    @Test
    void ignoresInvalidEntries() {
        var whitelist = IpWhitelist.parse(Set.of("not-an-ip", "10.0.0.0/99", "10.0.0.1/abc", ""));
        assertThat(whitelist.isEmpty()).isTrue();
        assertThat(whitelist.contains("10.0.0.1")).isFalse();
    }

    @Test
    void parseIpv4RejectsInvalid() {
        assertThat(IpWhitelist.parseIpv4("10.0.0")).isEqualTo(-1L);
        assertThat(IpWhitelist.parseIpv4("10.0.0.256")).isEqualTo(-1L);
        assertThat(IpWhitelist.parseIpv4("10.0.0.1.1")).isEqualTo(-1L);
        assertThat(IpWhitelist.parseIpv4("localhost")).isEqualTo(-1L);
        assertThat(IpWhitelist.parseIpv4("10.244.1.20")).isEqualTo((10L << 24) | (244L << 16) | (1L << 8) | 20L);
    }
}
