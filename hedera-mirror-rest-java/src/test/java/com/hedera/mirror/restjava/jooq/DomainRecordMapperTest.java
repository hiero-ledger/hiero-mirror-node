// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.jooq;

import static com.hedera.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;
import static com.hedera.mirror.restjava.jooq.domain.Tables.TOKEN;
import static com.hedera.mirror.restjava.jooq.domain.Tables.TRANSACTION;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class DomainRecordMapperTest extends RestJavaIntegrationTest {

    private final DSLContext dslContext;

    @Test
    void nftAllowance() {
        var expected = List.of(
                domainBuilder.nftAllowance().persist(),
                domainBuilder.nftAllowance().persist());
        var actual = dslContext.selectFrom(NFT_ALLOWANCE).fetchInto(NftAllowance.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void token() {
        var expected = List.of(
                domainBuilder.token().persist(),
                domainBuilder
                        .token()
                        .customize(t -> t.decimals(0).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                        .persist());
        var actual = dslContext.selectFrom(TOKEN).fetchInto(Token.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void transaction() {
        var expected = List.of(
                domainBuilder.transaction().persist(),
                domainBuilder
                        .transaction()
                        .customize(t -> t.nftTransfer(
                                List.of(domainBuilder.nftTransfer().get())))
                        .persist());
        var actual = dslContext.selectFrom(TRANSACTION).fetchInto(Transaction.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }
}
