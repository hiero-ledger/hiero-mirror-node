// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors the real {@code ClprControlMessage} proto's {@code payload} oneof, stored as {@code jsonb} in
 * {@code clpr_message.control_message}. A future oneof variant only needs a new field added here -- no schema
 * change, since the column is already {@code jsonb}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
public class ClprControlMessage {
    private ClprConfigUpdate configUpdate;
}
