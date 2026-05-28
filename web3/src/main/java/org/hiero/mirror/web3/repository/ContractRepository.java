// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import org.hiero.mirror.common.domain.contract.Contract;
import org.springframework.data.repository.CrudRepository;

public interface ContractRepository extends CrudRepository<Contract, Long> {}
