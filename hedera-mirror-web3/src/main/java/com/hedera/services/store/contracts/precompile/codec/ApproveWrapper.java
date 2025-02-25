// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;

public record ApproveWrapper(
        TokenID tokenId,
        AccountID spender,
        BigInteger amount,
        BigInteger serialNumber,
        boolean isFungible,
        boolean isErcApprove) {}
