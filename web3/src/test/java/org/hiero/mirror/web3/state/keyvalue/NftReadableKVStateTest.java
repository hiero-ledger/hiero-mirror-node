// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Collections;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
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
class NftReadableKVStateTest {

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1252L).build();
    private static final Optional<Long> timestamp = Optional.of(1726231985623004672L);
    private static final EntityId spender = EntityId.of(1L, 2L, 3L);
    private static final EntityId treasury = EntityId.of(1L, 2L, 4L);
    private static final NftID NFT_ID = new NftID(TOKEN_ID, 1L);
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private NftReadableKVState nftReadableKVState;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenRepository tokenRepository;

    private DomainBuilder domainBuilder;
    private Entity entity;

    @Spy
    private ContractCallContext contractCallContext;

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
        entity = domainBuilder
                .entity()
                .customize(e -> e.id(TOKEN_ID.tokenNum()).type(EntityType.TOKEN).createdTimestamp(timestamp.get()))
                .get();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void getNftMappedValuesWithTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        Nft nftDomain = setupNft(timestamp);
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getSpender()),
                        com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesWithTreasuryAsOwner() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        Nft nftDomain = setupNftWithOwner(timestamp);
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getSpender()),
                        com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesWithoutTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        Nft nftDomain = setupNft(Optional.empty());
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getSpender()),
                        com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesEmptyTokenId() {
        NftID nftId = new NftID(null, NFT_ID.serialNumber());
        assertThat(nftReadableKVState.readFromDataSource(nftId)).isNull();
    }

    @Test
    void getNftMappedValuesMissingSpenderWithoutTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        Nft nftDomain = setupNftMissingSpender(Optional.empty());
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesMissingSpenderWithTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        Nft nftDomain = setupNftMissingSpender(timestamp);
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).satisfies(nft -> assertThat(nft)
                .returns(NFT_ID, com.hedera.hapi.node.state.token.Nft::nftId)
                .returns(
                        EntityIdUtils.toAccountId(nftDomain.getAccountId()),
                        com.hedera.hapi.node.state.token.Nft::ownerId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::spenderId)
                .returns(
                        convertToTimestamp(nftDomain.getCreatedTimestamp()),
                        com.hedera.hapi.node.state.token.Nft::mintTime)
                .returns(Bytes.wrap(nftDomain.getMetadata()), com.hedera.hapi.node.state.token.Nft::metadata)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerPreviousNftId)
                .returns(null, com.hedera.hapi.node.state.token.Nft::ownerNextNftId));
    }

    @Test
    void getNftMappedValuesMissingEntity() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        assertThat(nftReadableKVState.readFromDataSource(NFT_ID)).isNull();
    }

    @Test
    void testIterateFromDataSource() {
        assertThat(nftReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void testSize() {
        assertThat(nftReadableKVState.size()).isZero();
    }

    private Nft setupNft(Optional<Long> timestamp, final EntityId spender, final EntityId owner) {
        final var databaseNft = domainBuilder
                .nft()
                .customize(t -> {
                    t.tokenId(entity.getId()).serialNumber(NFT_ID.serialNumber());
                    if (spender != null) {
                        t.spender(spender.getId());
                    }
                    if (owner != null) {
                        t.accountId(owner);
                    }
                })
                .get();

        final var databaseToken = domainBuilder
                .token()
                .customize(t -> {
                    t.tokenId(entity.getId()).treasuryAccountId(treasury);
                    if (owner != null) {
                        t.treasuryAccountId(owner);
                    }
                })
                .get();

        if (timestamp.isPresent()) {
            long ts = timestamp.get();
            databaseNft.setCreatedTimestamp(ts);
            when(tokenRepository.findByTokenIdAndTimestamp(entity.getId(), ts)).thenReturn(Optional.of(databaseToken));
            when(nftRepository.findActiveByIdAndTimestamp(entity.getId(), NFT_ID.serialNumber(), ts))
                    .thenReturn(Optional.of(databaseNft));
        } else {
            when(tokenRepository.findById(entity.getId())).thenReturn(Optional.of(databaseToken));
            when(nftRepository.findActiveById(entity.getId(), NFT_ID.serialNumber()))
                    .thenReturn(Optional.of(databaseNft));
        }

        return databaseNft;
    }

    private Nft setupNft(Optional<Long> timestamp) {
        return setupNft(timestamp, spender, null);
    }

    private Nft setupNftWithOwner(Optional<Long> timestamp) {
        return setupNft(timestamp, null, treasury);
    }

    private Nft setupNftMissingSpender(Optional<Long> timestamp) {
        return setupNft(timestamp, null, null);
    }
}
