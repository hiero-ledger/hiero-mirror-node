// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.springframework.validation.annotation.Validated;

@Builder
@Data
@Validated
public final class LedgerProperties {

    @NotEmpty
    private byte[] historyProofVerificationKey;

    @NotEmpty
    private byte[] ledgerId;

    @Builder.Default
    @NotEmpty
    @Valid
    private List<LedgerNodeContribution> nodeContributions = List.of();

    public Ledger toLedger() {
        return Ledger.builder()
                .historyProofVerificationKey(historyProofVerificationKey)
                .ledgerId(ledgerId)
                .nodeContributions(nodeContributions)
                .build();
    }
}
