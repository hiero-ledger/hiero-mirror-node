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
public class RegisteredNode extends AbstractRegisteredNode {}
