// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@jakarta.persistence.Entity
@NoArgsConstructor
@SuperBuilder
public class NftAllowance extends AbstractNftAllowance {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
