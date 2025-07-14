// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import lombok.Builder;

@Builder(toBuilder = true)
public record ServiceEndpoint(String domainName, String ipAddressV4, int port) {
    public static final ServiceEndpoint CLEAR = new ServiceEndpoint(null, null, -1);
}
