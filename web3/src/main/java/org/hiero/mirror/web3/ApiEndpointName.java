// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ApiEndpointName {
    ACTIONS("/api/v1/contracts/call/actions"),
    CALL("/api/v1/contracts/call"),
    /**
     * Response header defaults merged into the other endpoints. Not a request path.
     */
    DEFAULT(null),
    OPCODES("/api/v1/contracts/results/{transactionIdOrHash}/opcodes"),
    PRESTATE("/api/v1/contracts/results/{transactionIdOrHash}/prestate");

    private static final Map<String, ApiEndpointName> PATHS = EnumSet.allOf(ApiEndpointName.class).stream()
            .filter(endpoint -> endpoint.path != null)
            .collect(Collectors.toMap(ApiEndpointName::getPath, Function.identity()));

    @Getter
    private final String path;

    public static ApiEndpointName fromPath(String path) {
        return PATHS.getOrDefault(path, null);
    }
}
