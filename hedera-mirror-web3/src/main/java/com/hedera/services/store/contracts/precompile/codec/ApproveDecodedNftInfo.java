// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;

/**
 * Record used by {@link com.hedera.services.store.contracts.precompile.HTSPrecompiledContract}
 * and {@link com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile}
 * for getting decoded tokenId and serialNumber from transaction input
 * @param tokenId
 * @param serialNumber
 */
public record ApproveDecodedNftInfo(TokenID tokenId, BigInteger serialNumber) {}
