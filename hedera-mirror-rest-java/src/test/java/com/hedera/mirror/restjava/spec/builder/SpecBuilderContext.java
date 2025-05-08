// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import java.util.Map;
import java.util.Optional;

public record SpecBuilderContext(Map<String, Object> specEntity) {

    public boolean isHistory() {
        return Optional.ofNullable(specEntity.get("timestamp_range"))
                .map(range -> !range.toString().endsWith(",)"))
                .orElse(false);
    }
}
