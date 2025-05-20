// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

public record SetApprovalForAllWrapper(TokenID tokenId, AccountID to, boolean approved) {}
