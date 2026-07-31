// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@Upsertable
public class Ledger {

    private long consensusTimestamp;

    private byte[] historyProofVerificationKey;

    @Id
    @ToString.Include
    private byte[] ledgerId;

    @JsonIgnore
    @Column("node_contributions")
    private LedgerNodeContributionListHolder nodeContributionsColumn;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<LedgerNodeContribution> getNodeContributions() {
        return nodeContributionsColumn == null ? null : nodeContributionsColumn.items();
    }

    public void setNodeContributions(List<LedgerNodeContribution> value) {
        this.nodeContributionsColumn = LedgerNodeContributionListHolder.of(value);
    }

    public abstract static class LedgerBuilder<C extends Ledger, B extends LedgerBuilder<C, B>> {

        public B nodeContributions(List<LedgerNodeContribution> value) {
            this.nodeContributionsColumn = LedgerNodeContributionListHolder.of(value);
            return self();
        }
    }
}
