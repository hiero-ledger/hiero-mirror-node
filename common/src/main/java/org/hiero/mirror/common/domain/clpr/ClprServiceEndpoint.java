// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.Builder;

/**
 * Network address for a CLPR endpoint, mirroring the real {@code ClprServiceEndpoint} proto. Ledger-neutral by
 * design (no field in this type is Hiero-specific), so it's an IP/port pair rather than this codebase's own
 * {@link org.hiero.mirror.common.domain.node.ServiceEndpoint}, which also carries a Hiero domain name.
 */
@Builder
public record ClprServiceEndpoint(String ipAddress, int port) {}
