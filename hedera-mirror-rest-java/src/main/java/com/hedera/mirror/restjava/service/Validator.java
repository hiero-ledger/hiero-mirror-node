// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.restjava.RestJavaProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class Validator {

    private final RestJavaProperties properties;

    public void validateShard(Object id, long shard) {
        long expected = properties.getShard();
        if (shard != expected) {
            throw new IllegalArgumentException("ID %s has an invalid shard. Shard must be %d".formatted(id, expected));
        }
    }
}
