// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.repository;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.repository.RecordFileRepository;

@WrbRepository
public interface RecordFileVerificationRepository extends RecordFileRepository {

    List<RecordFile> findAllByConsensusEndLessThanEqualOrderByConsensusEndAsc(long consensusEnd);
}
