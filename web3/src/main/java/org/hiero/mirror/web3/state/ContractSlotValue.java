// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import lombok.Value;

@Value
public class ContractSlotValue {
    private final byte[] slot;
    private final byte[] value;
}
