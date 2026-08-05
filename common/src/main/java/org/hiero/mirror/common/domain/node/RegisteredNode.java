// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class RegisteredNode extends AbstractRegisteredNode {}
