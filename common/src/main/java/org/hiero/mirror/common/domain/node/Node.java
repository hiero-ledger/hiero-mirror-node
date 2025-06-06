// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Node extends AbstractNode {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
