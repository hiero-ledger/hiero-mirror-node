// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractLedger implements History {

    @Id
    private byte[] ledgerId;

    // Informational only, not used in TSS signature verification
    @Builder.Default
    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<LedgerNodeContribution> nodeContributions = List.of();

    private Range<Long> timestampRange;
}
