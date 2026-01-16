// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAirdrop.Id;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropRepository extends CrudRepository<TokenAirdrop, Id>, TokenAirdropRepositoryCustom {}
