// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("ledger")
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

    @JsonSerialize(using = ObjectToStringSerializer.class)
    private List<LedgerNodeContribution> nodeContributions;
}
