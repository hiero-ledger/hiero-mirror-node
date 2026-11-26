// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;
import static org.hiero.mirror.restjava.jooq.domain.Tables.TOKEN;
import static org.hiero.mirror.restjava.jooq.domain.Tables.TRANSACTION;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
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
