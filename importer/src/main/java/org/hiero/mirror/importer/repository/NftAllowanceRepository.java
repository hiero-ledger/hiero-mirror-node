// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractNftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, AbstractNftAllowance.Id> {}
