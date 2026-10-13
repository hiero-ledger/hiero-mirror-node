// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import com.google.common.collect.Range;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
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

    private Range<Long> timestampRange;
}
