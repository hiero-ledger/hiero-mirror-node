// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable
public class Ledger {

    private long consensusTimestamp;

    @Id
    @ToString.Include
    private byte[] ledgerId;
}
