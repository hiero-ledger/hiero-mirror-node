// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.entity.AbstractNftAllowance.Id;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id>, NftAllowanceRepositoryCustom {}
