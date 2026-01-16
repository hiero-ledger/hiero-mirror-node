// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import java.util.Collections;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.TokenAirdropRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirdropsReadableKVStateTest {

    @InjectMocks
    private AirdropsReadableKVState airdropsReadableKVState;

    @Mock
    private TokenAirdropRepository tokenAirdropRepository;

    @Spy
    private ContractCallContext contractCallContext;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    private DomainBuilder domainBuilder;

    private static final Optional<Long> timestamp = Optional.of(1726231985623004672L);

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void sizeIsAlwaysZero() {
        assertThat(airdropsReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(airdropsReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void fungibleTokenLatestBlockHappyPath() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var tokenId =
                TokenID.newBuilder().shardNum(1L).realmNum(2L).tokenNum(3L).build();
        final var tokenAirdrop = TokenAirdrop.builder().amount(3L).build();
        final var pendingAirdropValue =
                PendingAirdropValue.newBuilder().amount(3L).build();

        when(tokenAirdropRepository.findById(
                        toEntityId(senderId).getId(),
                        toEntityId(receiverId).getId(),
                        toEntityId(tokenId).getId(),
                        0L))
                .thenReturn(Optional.of(tokenAirdrop));

        final var expected = AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(pendingAirdropValue)
                .build();
        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .fungibleTokenType(tokenId)
                        .build()))
                .isEqualTo(expected);
    }

    @Test
    void fungibleTokenHistoricalBlockHappyPath() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var tokenId =
                TokenID.newBuilder().shardNum(1L).realmNum(2L).tokenNum(3L).build();
        final var tokenAirdrop = TokenAirdrop.builder().amount(3L).build();
        final var pendingAirdropValue =
                PendingAirdropValue.newBuilder().amount(3L).build();

        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenAirdropRepository.findByIdAndTimestamp(
                        toEntityId(senderId).getId(),
                        toEntityId(receiverId).getId(),
                        toEntityId(tokenId).getId(),
                        0L,
                        timestamp.get()))
                .thenReturn(Optional.of(tokenAirdrop));

        final var expected = AccountPendingAirdrop.newBuilder()
                .pendingAirdropValue(pendingAirdropValue)
                .build();
        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .fungibleTokenType(tokenId)
                        .build()))
                .isEqualTo(expected);
    }

    @Test
    void fungibleTokenAirdropNotFoundReturnsNull() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var tokenId =
                TokenID.newBuilder().shardNum(1L).realmNum(2L).tokenNum(3L).build();

        when(tokenAirdropRepository.findById(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .fungibleTokenType(tokenId)
                        .build()))
                .isNull();
    }

    @Test
    void fungibleTokenAirdropNotFoundHistoricalReturnsNull() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var tokenId =
                TokenID.newBuilder().shardNum(1L).realmNum(2L).tokenNum(3L).build();

        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenAirdropRepository.findByIdAndTimestamp(anyLong(), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .fungibleTokenType(tokenId)
                        .build()))
                .isNull();
    }

    @Test
    void nftLatestBlockHappyPath() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var nftId = NftID.newBuilder()
                .tokenId(TokenID.newBuilder()
                        .shardNum(1L)
                        .realmNum(2L)
                        .tokenNum(3L)
                        .build())
                .serialNumber(4L)
                .build();
        final var tokenAirdrop = TokenAirdrop.builder().build();

        when(tokenAirdropRepository.findById(
                        toEntityId(senderId).getId(),
                        toEntityId(receiverId).getId(),
                        toEntityId(nftId.tokenId()).getId(),
                        4L))
                .thenReturn(Optional.of(tokenAirdrop));

        final var expected = AccountPendingAirdrop.DEFAULT;
        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .nonFungibleToken(nftId)
                        .build()))
                .isEqualTo(expected);
    }

    @Test
    void nftHistoricalBlockHappyPath() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var nftId = NftID.newBuilder()
                .tokenId(TokenID.newBuilder()
                        .shardNum(1L)
                        .realmNum(2L)
                        .tokenNum(3L)
                        .build())
                .serialNumber(4L)
                .build();
        final var tokenAirdrop = TokenAirdrop.builder().build();

        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenAirdropRepository.findByIdAndTimestamp(
                        toEntityId(senderId).getId(),
                        toEntityId(receiverId).getId(),
                        toEntityId(nftId.tokenId()).getId(),
                        4L,
                        timestamp.get()))
                .thenReturn(Optional.of(tokenAirdrop));

        final var expected = AccountPendingAirdrop.DEFAULT;
        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .nonFungibleToken(nftId)
                        .build()))
                .isEqualTo(expected);
    }

    @Test
    void nftAirdropNotFoundReturnsNull() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var nftId = NftID.newBuilder()
                .tokenId(TokenID.newBuilder()
                        .shardNum(1L)
                        .realmNum(2L)
                        .tokenNum(3L)
                        .build())
                .serialNumber(4L)
                .build();

        when(tokenAirdropRepository.findById(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .nonFungibleToken(nftId)
                        .build()))
                .isNull();
    }

    @Test
    void nftAirdropNotFoundHistoricalReturnsNull() {
        final var senderId = toAccountId(domainBuilder.entityId());
        final var receiverId = toAccountId(domainBuilder.entityId());
        final var nftId = NftID.newBuilder()
                .tokenId(TokenID.newBuilder()
                        .shardNum(1L)
                        .realmNum(2L)
                        .tokenNum(3L)
                        .build())
                .serialNumber(4L)
                .build();

        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(tokenAirdropRepository.findByIdAndTimestamp(anyLong(), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThat(airdropsReadableKVState.get(PendingAirdropId.newBuilder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .nonFungibleToken(nftId)
                        .build()))
                .isNull();
    }
}
