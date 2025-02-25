// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;

public record ERCTransferParams(
        int functionId, Address senderAddress, TokenAccessor tokenAccessor, TokenID tokenID, Predicate<Address> exists)
        implements BodyParams {}
