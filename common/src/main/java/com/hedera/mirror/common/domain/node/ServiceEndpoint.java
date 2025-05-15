// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.node;

import lombok.Builder;

@Builder(toBuilder = true)
public record ServiceEndpoint(String domainName, String ipAddressV4, int port) {}
