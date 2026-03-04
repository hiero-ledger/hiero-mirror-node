// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Authorization {

    private String address;
    private String chainId;
    private Long nonce;
    private String r;
    private String s;
    private Integer yParity;
}
