// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprLedgerConfigurationRepository extends CrudRepository<ClprLedgerConfiguration, Long> {

    @Query(
            nativeQuery = true,
            value = "select * from clpr_ledger_configuration order by consensus_timestamp desc limit 1")
    @Nullable
    ClprLedgerConfiguration findLatest();
}
