// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PrecompileMapper {

    private static final Map<Integer, Precompile> abiConstantToPrecompile = new HashMap<>();

    public PrecompileMapper(final Set<Precompile> precompiles) {
        for (final Precompile precompile : precompiles) {
            for (final Integer selector : precompile.getFunctionSelectors()) {
                abiConstantToPrecompile.put(selector, precompile);
            }
        }
    }

    public Optional<Precompile> lookup(final int functionSelector) {
        final var precompile = abiConstantToPrecompile.get(functionSelector);
        return Optional.ofNullable(precompile);
    }
}
