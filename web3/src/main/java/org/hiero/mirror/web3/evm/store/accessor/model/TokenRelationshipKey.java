// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor.model;

import org.hyperledger.besu.datatypes.Address;

public record TokenRelationshipKey(Address tokenAddress, Address accountAddress) {}
