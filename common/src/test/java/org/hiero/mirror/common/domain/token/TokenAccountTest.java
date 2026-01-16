// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import static org.junit.jupiter.api.Assertions.*;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;

class TokenAccountTest {

    private static final EntityId FOO_COIN_ID = EntityId.of("0.0.101");
    private static final EntityId ACCOUNT_ID = EntityId.of("0.0.102");

    @Test
    void createValidTokenAccount() {
        var tokenAccount = TokenAccount.builder()
                .accountId(ACCOUNT_ID.getId())
                .associated(false)
                .createdTimestamp(1L)
                .freezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE)
                .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                .tokenId(FOO_COIN_ID.getId())
                .build();

        assertAll(
                () -> assertNotEquals(0, tokenAccount.getCreatedTimestamp()),
                () -> assertEquals(TokenFreezeStatusEnum.NOT_APPLICABLE, tokenAccount.getFreezeStatus()),
                () -> assertEquals(TokenKycStatusEnum.NOT_APPLICABLE, tokenAccount.getKycStatus()));
    }
}
