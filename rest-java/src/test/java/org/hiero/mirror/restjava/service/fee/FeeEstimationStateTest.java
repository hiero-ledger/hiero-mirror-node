// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.restjava.exception.EntityNotFoundException;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;
import org.junit.jupiter.api.Test;

class FeeEstimationStateTest {

    @Test
    void missingFeeScheduleReturnsNull() {
        final var systemEntity = new SystemEntity(new CommonProperties());
        final var fileService = mock(FileService.class);
        final var entityId = systemEntity.simpleFeeScheduleFile();
        final var fileId = FileID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .fileNum(entityId.getNum())
                .build();
        when(fileService.getFeeSchedule(Bound.EMPTY))
                .thenThrow(new EntityNotFoundException("File %s not found".formatted(entityId)));

        final var state = new FeeEstimationState(systemEntity, fileService);
        final var files = state.getReadableStates(com.hedera.node.app.service.file.FileService.NAME)
                .<FileID, File>get(V0490FileSchema.FILES_STATE_ID);

        assertThat(files.get(fileId)).isNull();
    }
}
