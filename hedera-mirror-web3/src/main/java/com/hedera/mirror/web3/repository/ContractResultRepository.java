// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.contract.ContractResult;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractResultRepository extends CrudRepository<ContractResult, Long> {}
