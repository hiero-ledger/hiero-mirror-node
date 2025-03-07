// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.TokenID;
import org.hyperledger.besu.datatypes.Address;

/**
 * Record containing specific body arguments for HRC precompiles.
 * */
public record HrcParams(TokenID token, Address senderAddress) implements BodyParams {}
