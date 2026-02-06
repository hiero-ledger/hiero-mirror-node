// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@Upsertable
public class Ledger {

    private long consensusTimestamp;

    private byte[] historyProofVerificationKey;

    private byte[] ledgerId;

    @Id
    @ToString.Include
    private String network;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<NodeContribution> nodeContributions;
}
