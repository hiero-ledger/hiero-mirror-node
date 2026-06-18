// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecordFileVerificationRepository extends RecordFileRepository {

    @Query("select f from RecordFile f where f.consensusEnd <= :ts order by f.consensusEnd")
    List<RecordFile> findUpTo(@Param("ts") long ts);
}
