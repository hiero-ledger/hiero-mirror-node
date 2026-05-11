// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.CustomFee;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Token plus custom fees in Hedera PBJ form for fee estimation (STATE mode).
 */
@NullMarked
public record TokenFeeView(TokenID tokenId, TokenType tokenType, List<CustomFee> customFees) {}
