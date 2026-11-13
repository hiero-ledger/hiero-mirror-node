// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprLedgerConfigurationRepository extends CrudRepository<ClprLedgerConfiguration, Long> {

    @Query(
            nativeQuery = true,
            value = "select * from clpr_ledger_configuration order by consensus_timestamp desc limit 1")
    Optional<ClprLedgerConfiguration> findLatest();
}
