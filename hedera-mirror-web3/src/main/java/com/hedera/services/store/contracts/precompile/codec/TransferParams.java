// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;

public record TransferParams(int functionId, Predicate<Address> exists) implements BodyParams {}
