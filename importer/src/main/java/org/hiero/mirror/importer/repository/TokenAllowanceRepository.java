// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.springframework.data.repository.CrudRepository;

public interface TokenAllowanceRepository extends CrudRepository<TokenAllowance, AbstractTokenAllowance.Id> {}
