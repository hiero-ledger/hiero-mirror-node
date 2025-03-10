// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ImmutableAccount {
    FEE_COLLECTOR(98),
    ENTITY_STAKE(800);

    private final long num;
}
