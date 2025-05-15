// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.token.Token;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {}
