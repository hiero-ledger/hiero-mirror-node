// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;

public record TokenUpdateWrapper(
        TokenID tokenID,
        String name,
        String symbol,
        AccountID treasury,
        String memo,
        List<TokenKeyWrapper> tokenKeys,
        TokenExpiryWrapper expiry) {}
