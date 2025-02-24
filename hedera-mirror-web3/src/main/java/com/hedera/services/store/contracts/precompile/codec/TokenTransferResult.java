// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;

public record TokenTransferResult(boolean isFungible, TokenID tokenID) implements RunResult {}
