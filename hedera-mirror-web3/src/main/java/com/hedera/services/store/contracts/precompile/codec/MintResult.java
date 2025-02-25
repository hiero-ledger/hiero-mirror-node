// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import java.util.List;

public record MintResult(long totalSupply, List<Long> serialNumbers) implements RunResult {}
