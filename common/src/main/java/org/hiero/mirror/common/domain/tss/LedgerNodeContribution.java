// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
@Validated
public class LedgerNodeContribution {

    @NotEmpty
    private byte[] historyProofKey;

    private long nodeId;

    private long weight;
}
