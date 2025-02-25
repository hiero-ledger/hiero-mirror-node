// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.token.AbstractTokenAirdrop.Id;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropRepository extends CrudRepository<TokenAirdrop, Id>, TokenAirdropRepositoryCustom {}
