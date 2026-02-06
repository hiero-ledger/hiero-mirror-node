// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.tss.Ledger;
import org.springframework.data.repository.CrudRepository;

public interface LedgerRepository extends CrudRepository<Ledger, String> {}
