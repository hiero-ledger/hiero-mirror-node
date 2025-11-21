// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava;

import java.util.Arrays;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.common.EntityIdRangeParameter;

public abstract class RestJavaIntegrationTest extends CommonIntegrationTest {

    protected EntityIdRangeParameter[] paramToArray(EntityIdRangeParameter... param) {
        return Arrays.copyOf(param, param.length);
    }

    protected HookStorage hookStorage(HookStorageChange change) {
        return new HookStorage()
                .toBuilder()
                        .hookId(change.getHookId())
                        .key(change.getKey())
                        .ownerId(change.getOwnerId())
                        .value(change.getValueWritten() != null ? change.getValueWritten() : change.getValueRead())
                        .modifiedTimestamp(change.getConsensusTimestamp())
                        .build();
    }
}
