// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractLedger implements History {

    @Id
    private byte[] ledgerId;

    // Informational only, not used in TSS signature verification
    @Builder.Default
    @Column("node_contributions")
    @JsonIgnore
    private LedgerNodeContributionListHolder nodeContributionsColumn = LedgerNodeContributionListHolder.of(List.of());

    private Range<Long> timestampRange;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<LedgerNodeContribution> getNodeContributions() {
        return nodeContributionsColumn == null ? null : nodeContributionsColumn.items();
    }

    public void setNodeContributions(List<LedgerNodeContribution> nodeContributions) {
        this.nodeContributionsColumn = LedgerNodeContributionListHolder.of(nodeContributions);
    }

    public abstract static class AbstractLedgerBuilder<
            C extends AbstractLedger, B extends AbstractLedgerBuilder<C, B>> {

        public B nodeContributions(List<LedgerNodeContribution> nodeContributions) {
            this.nodeContributionsColumn$value = LedgerNodeContributionListHolder.of(nodeContributions);
            this.nodeContributionsColumn$set = true;
            return self();
        }
    }
}
