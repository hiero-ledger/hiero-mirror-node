// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@RequiredArgsConstructor
class TokenAirdropServiceTest extends RestJavaIntegrationTest {

    private final TokenAirdropService service;
    private static final EntityId RECEIVER = EntityId.of(1000L);
    private static final EntityId SENDER = EntityId.of(1001L);
    private static final EntityId TOKEN_ID = EntityId.of(5000L);

    @ParameterizedTest
    @EnumSource(AirdropRequestType.class)
    void getAirdrops(AirdropRequestType type) {
        var fungibleAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.amount(100L)
                        .receiverAccountId(RECEIVER.getId())
                        .senderAccountId(SENDER.getId())
                        .tokenId(TOKEN_ID.getId()))
                .persist();

        var accountId = type == OUTSTANDING ? SENDER : RECEIVER;
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(accountId))
                .type(type)
                .build();
        var response = service.getAirdrops(request);
        assertThat(response).containsExactly(fungibleAirdrop);
    }

    @ParameterizedTest
    @EnumSource(AirdropRequestType.class)
    void getByAlias(AirdropRequestType type) {
        var entity = domainBuilder.entity().persist();
        var tokenAirdropBuilder = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON);
        var tokenAirdrop = type == OUTSTANDING
                ? tokenAirdropBuilder
                        .customize(a -> a.senderAccountId(entity.getId()))
                        .persist()
                : tokenAirdropBuilder
                        .customize(a -> a.receiverAccountId(entity.getId()))
                        .persist();

        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdAliasParameter(entity.getShard(), entity.getRealm(), entity.getAlias()))
                .type(type)
                .build();
        var response = service.getAirdrops(request);
        assertThat(response).containsExactly(tokenAirdrop);
    }

    @ParameterizedTest
    @EnumSource(AirdropRequestType.class)
    void getByEvmAddress(AirdropRequestType type) {
        var entity = domainBuilder.entity().persist();
        var tokenAirdropBuilder = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON);
        var tokenAirdrop = type == OUTSTANDING
                ? tokenAirdropBuilder
                        .customize(a -> a.senderAccountId(entity.getId()))
                        .persist()
                : tokenAirdropBuilder
                        .customize(a -> a.receiverAccountId(entity.getId()))
                        .persist();
        var request = TokenAirdropRequest.builder()
                .accountId(
                        new EntityIdEvmAddressParameter(entity.getShard(), entity.getRealm(), entity.getEvmAddress()))
                .type(type)
                .build();
        var response = service.getAirdrops(request);
        assertThat(response).containsExactly(tokenAirdrop);
    }

    @ParameterizedTest
    @EnumSource(AirdropRequestType.class)
    void getNotFound(AirdropRequestType type) {
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(EntityId.of(3000L)))
                .type(type)
                .build();
        var response = service.getAirdrops(request);
        assertThat(response).isEmpty();
    }
}
