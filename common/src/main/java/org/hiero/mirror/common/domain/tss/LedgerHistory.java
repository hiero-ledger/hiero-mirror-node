// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Table
public class LedgerHistory extends AbstractLedger {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
