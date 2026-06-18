// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.repository.SidecarFileRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SidecarFileVerificationRepository extends SidecarFileRepository {

    @Query("select s from SidecarFile s where s.consensusEnd <= :ts order by s.consensusEnd, s.index")
    List<SidecarFile> findUpTo(@Param("ts") long ts);
}
