// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropRepository extends CrudRepository<TokenAirdrop, AbstractTokenAirdrop.Id> {}
