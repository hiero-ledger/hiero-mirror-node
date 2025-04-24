// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import java.util.Map;

public record SpecBuilderContext(boolean isHistory, Map<String, Object> specEntity) {}
