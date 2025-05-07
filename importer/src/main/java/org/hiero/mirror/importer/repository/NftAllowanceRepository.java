// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, AbstractNftAllowance.Id> {}
