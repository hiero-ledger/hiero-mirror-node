// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, AbstractTokenAccount.Id> {}
