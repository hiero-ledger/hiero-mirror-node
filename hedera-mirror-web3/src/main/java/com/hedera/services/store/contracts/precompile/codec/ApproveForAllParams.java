// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;
import org.hyperledger.besu.datatypes.Address;

public record ApproveForAllParams(TokenID tokenId, Address senderAddress) implements BodyParams {}
