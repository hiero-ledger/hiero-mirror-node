// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.Transaction;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    Optional<Transaction> findByPayerAccountIdAndValidStartNs(EntityId payerAccountId, long validStartNs);
}
