// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.springframework.data.repository.CrudRepository;

public interface AccountBalanceRepository
        extends CrudRepository<AccountBalance, AccountBalance.Id>, AccountBalanceRepositoryCustom {}
