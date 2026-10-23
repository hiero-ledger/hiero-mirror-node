// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors the real {@code ClprConfigUpdate} proto -- the configuration propagated to the peer ledger via a Control
 * message's {@code config_update} oneof case.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
public class ClprConfigUpdate {
    private ClprControlConfiguration configuration;
}
