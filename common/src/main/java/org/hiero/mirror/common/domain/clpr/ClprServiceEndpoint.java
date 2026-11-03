// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.Builder;

/**
 * Network address for a CLPR endpoint, mirroring the real {@code ClprServiceEndpoint} proto. Ledger-neutral, unlike
 * this codebase's own {@link org.hiero.mirror.common.domain.node.ServiceEndpoint}.
 */
@Builder
public record ClprServiceEndpoint(String ipAddress, int port) {}
