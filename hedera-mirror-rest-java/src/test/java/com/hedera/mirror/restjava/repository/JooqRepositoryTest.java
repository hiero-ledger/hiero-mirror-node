// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.service.Bound;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
class JooqRepositoryTest extends RestJavaIntegrationTest {

    private final TokenAirdropRepository repository;

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyBounds(List<Bound> bounds) {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getReceiverAccountId());
        var request = mock(TokenAirdropRequest.class);
        when(request.getAccountId()).thenReturn(new EntityIdNumParameter(entityId));
        when(request.getBounds()).thenReturn(bounds);
        when(request.getLimit()).thenReturn(1);
        when(request.getOrder()).thenReturn(Direction.ASC);
        when(request.getType()).thenReturn(PENDING);

        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
    }
}
