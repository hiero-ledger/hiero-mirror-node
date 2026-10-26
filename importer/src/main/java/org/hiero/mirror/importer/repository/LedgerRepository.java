// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface LedgerRepository extends CrudRepository<Ledger, byte[]> {

    @Query(nativeQuery = true, value = "select * from ledger order by lower(timestamp_range) desc limit 1")
    Optional<Ledger> findLatest();
}
