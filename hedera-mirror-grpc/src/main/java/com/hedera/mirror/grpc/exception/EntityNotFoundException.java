// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.exception;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.MirrorNodeException;

public class EntityNotFoundException extends MirrorNodeException {

    private static final String MESSAGE = "%s does not exist";
    private static final long serialVersionUID = 809036847722840635L;

    public EntityNotFoundException(EntityId entityId) {
        super(String.format(MESSAGE, entityId));
    }
}
