// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pre-parsed IPv4 / CIDR allow-list. Matching uses integer arithmetic only. */
final class IpWhitelist {

    static final IpWhitelist EMPTY = new IpWhitelist(Set.of(), List.of());

    private final Set<String> exactIps;
    private final List<Cidr> cidrs;

    private IpWhitelist(Set<String> exactIps, List<Cidr> cidrs) {
        this.exactIps = exactIps;
        this.cidrs = cidrs;
    }

    static IpWhitelist parse(Collection<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }

        var exactIps = new HashSet<String>();
        var cidrs = new ArrayList<Cidr>();

        for (var entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int slash = entry.indexOf('/');
            if (slash < 0) {
                if (parseIpv4(entry) >= 0) {
                    exactIps.add(entry);
                }
                continue;
            }
            var ipv4 = parseIpv4(entry.substring(0, slash));
            int prefix;
            try {
                prefix = Integer.parseInt(entry.substring(slash + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (ipv4 >= 0 && prefix >= 0 && prefix <= 32) {
                long mask = prefix == 0 ? 0L : 0xFFFFFFFFL << (32 - prefix);
                cidrs.add(new Cidr(ipv4 & mask, mask));
            }
        }

        if (exactIps.isEmpty() && cidrs.isEmpty()) {
            return EMPTY;
        }
        return new IpWhitelist(Set.copyOf(exactIps), List.copyOf(cidrs));
    }

    boolean isEmpty() {
        return this == EMPTY;
    }

    boolean contains(String ip) {
        if (isEmpty() || ip == null || ip.isEmpty()) {
            return false;
        }
        if (exactIps.contains(ip)) {
            return true;
        }
        var ipv4 = parseIpv4(ip);
        if (ipv4 < 0) {
            return false;
        }
        for (int i = 0, n = cidrs.size(); i < n; i++) {
            if (cidrs.get(i).contains(ipv4)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return unsigned IPv4 address, or {@code -1} if {@code ip} is not dotted-decimal IPv4
     */
    static long parseIpv4(String ip) {
        long value = 0L;
        int octet = 0;
        int digits = 0;
        int octets = 0;
        int length = ip.length();
        for (int i = 0; i < length; i++) {
            char c = ip.charAt(i);
            if (c == '.') {
                if (digits == 0 || octets == 3) {
                    return -1L;
                }
                value = (value << 8) | octet;
                octet = 0;
                digits = 0;
                octets++;
            } else if (c >= '0' && c <= '9') {
                octet = octet * 10 + (c - '0');
                if (octet > 255) {
                    return -1L;
                }
                digits++;
            } else {
                return -1L;
            }
        }
        if (digits == 0 || octets != 3) {
            return -1L;
        }
        return (value << 8) | octet;
    }

    private record Cidr(long network, long mask) {
        boolean contains(long ip) {
            return (ip & mask) == network;
        }
    }
}
