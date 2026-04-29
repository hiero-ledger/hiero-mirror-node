// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface NftHistoryRepository extends CrudRepository<NftHistory, AbstractNft.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from nft_history where timestamp_range << int8range(?1, null)")
    @Transactional
    int prune(long consensusTimestamp);
}
