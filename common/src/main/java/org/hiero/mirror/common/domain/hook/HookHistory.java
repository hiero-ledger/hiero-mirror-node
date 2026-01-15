// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.hook;

import jakarta.persistence.IdClass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@jakarta.persistence.Entity
@IdClass(AbstractHook.Id.class)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class HookHistory extends AbstractHook {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
