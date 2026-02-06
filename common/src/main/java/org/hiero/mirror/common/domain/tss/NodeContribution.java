// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
public class NodeContribution {

    private byte[] historyProofKey;

    private long nodeId;

    private long weight;
}
