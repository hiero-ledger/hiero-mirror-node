// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.repository;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.repository.SidecarFileRepository;

@WrbRepository
public interface SidecarFileVerificationRepository extends SidecarFileRepository {

    List<SidecarFile> findAllByConsensusEndLessThanEqualOrderByConsensusEndAscIndexAsc(long consensusEnd);
}
