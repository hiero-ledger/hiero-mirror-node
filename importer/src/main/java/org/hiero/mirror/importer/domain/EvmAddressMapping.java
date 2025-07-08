// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class EvmAddressMapping {
    private byte[] evmAddress;
    private long id;
}
