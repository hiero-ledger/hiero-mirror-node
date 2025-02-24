// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.accessor.model;

import org.hyperledger.besu.datatypes.Address;

public record TokenRelationshipKey(Address tokenAddress, Address accountAddress) {}
