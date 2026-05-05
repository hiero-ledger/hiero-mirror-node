// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;

import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TokenAirdrop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TokenAirdropMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private TokenAirdropMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new TokenAirdropMapperImpl(commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void map(TokenTypeEnum tokenType) {
        var tokenAirdrop = domainBuilder.tokenAirdrop(tokenType).get();
        var to = commonMapper.mapTimestamp(tokenAirdrop.getTimestampLower());

        assertThat(mapper.map(List.of(tokenAirdrop)))
                .first()
                .returns(tokenType == NON_FUNGIBLE_UNIQUE ? null : tokenAirdrop.getAmount(), TokenAirdrop::getAmount)
                .returns(EntityId.of(tokenAirdrop.getReceiverAccountId()).toString(), TokenAirdrop::getReceiverId)
                .returns(EntityId.of(tokenAirdrop.getSenderAccountId()).toString(), TokenAirdrop::getSenderId)
                .returns(
                        tokenType == FUNGIBLE_COMMON ? null : tokenAirdrop.getSerialNumber(),
                        TokenAirdrop::getSerialNumber)
                .returns(EntityId.of(tokenAirdrop.getTokenId()).toString(), TokenAirdrop::getTokenId)
                .satisfies(a -> assertThat(a.getTimestamp())
                        .returns(to, TimestampRange::getFrom)
                        .returns(null, TimestampRange::getTo));
    }

    @Test
    void mapNulls() {
        var tokenAirdrop = new org.hiero.mirror.common.domain.token.TokenAirdrop();
        assertThat(mapper.map(tokenAirdrop))
                .returns(null, TokenAirdrop::getAmount)
                .returns(null, TokenAirdrop::getReceiverId)
                .returns(null, TokenAirdrop::getSenderId)
                .returns(null, TokenAirdrop::getSerialNumber)
                .returns(null, TokenAirdrop::getTokenId)
                .returns(null, TokenAirdrop::getTimestamp);
    }
}
