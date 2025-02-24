// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import org.springframework.data.repository.CrudRepository;

public interface TokenAllowanceRepository extends CrudRepository<TokenAllowance, AbstractTokenAllowance.Id> {}
