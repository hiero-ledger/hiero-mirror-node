// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.token.AbstractTokenAirdrop.Id;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropRepository extends CrudRepository<TokenAirdrop, Id>, TokenAirdropRepositoryCustom {

    // The composite primary key is mapped as an embedded id, which Spring Data JDBC can't bind for derived findById,
    // so query the key columns explicitly
    @Override
    @Query("""
            select * from token_airdrop
            where receiver_account_id = :#{#id.receiverAccountId}
              and sender_account_id = :#{#id.senderAccountId}
              and serial_number = :#{#id.serialNumber}
              and token_id = :#{#id.tokenId}
            """)
    Optional<TokenAirdrop> findById(Id id);
}
