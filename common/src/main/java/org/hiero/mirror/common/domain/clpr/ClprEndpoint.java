// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An endpoint participating in CLPR syncs. Stored as a {@code jsonb} array element on
 * {@code clpr_channel.endpoint_manifest} and {@code clpr_endpoint_manifest.endpoints}. A Lombok class rather than a
 * record since {@code byte[]} record components get identity-based, not value-based, {@code equals()}/
 * {@code hashCode()}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
public class ClprEndpoint {
    private ClprServiceEndpoint serviceEndpoint;
    private byte[] tlsCertificate;
    private byte[] accountId;
}
