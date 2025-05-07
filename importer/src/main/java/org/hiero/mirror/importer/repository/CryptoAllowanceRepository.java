// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.entity.AbstractCryptoAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import org.springframework.data.repository.CrudRepository;

public interface CryptoAllowanceRepository extends CrudRepository<CryptoAllowance, AbstractCryptoAllowance.Id> {}
