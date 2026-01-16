// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import org.hiero.mirror.restjava.parameter.EntityIdAliasParameter;
import org.hiero.mirror.restjava.parameter.EntityIdEvmAddressParameter;
import org.hiero.mirror.restjava.parameter.EntityIdNumParameter;
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
