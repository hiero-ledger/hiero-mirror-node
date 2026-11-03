// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import org.hiero.mirror.common.domain.History;

public interface ClprPendingCommitment extends History {

    Long getCreatedTimestamp();

    void setCreatedTimestamp(Long createdTimestamp);

    ClprPendingCommitmentStatus getStatus();

    void setStatus(ClprPendingCommitmentStatus status);
}
