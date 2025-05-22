// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static com.hedera.services.utils.EntityIdUtils.idFromEncodedId;
import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.web3.repository.NftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UniqueTokenDatabaseAccessorTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @InjectMocks
    private UniqueTokenDatabaseAccessor uniqueTokenDatabaseAccessor;

    @Mock
    private NftRepository nftRepository;

    private static final Optional<Long> timestamp = Optional.of(1234L);

    @Test
    void get() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;

        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();

        when(nftRepository.findActiveById(nft.getTokenId(), nft.getSerialNumber()))
                .thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(getNftKey(nft), Optional.empty()))
                .hasValueSatisfying(uniqueToken -> assertThat(uniqueToken)
                        .returns(idFromEntityId(EntityId.of(nft.getTokenId())), UniqueToken::getTokenId)
                        .returns(nft.getId().getSerialNumber(), UniqueToken::getSerialNumber)
                        .returns(
                                new RichInstant(createdTimestampSecs, createdTimestampNanos),
                                UniqueToken::getCreationTime)
                        .returns(idFromEntityId(nft.getAccountId()), UniqueToken::getOwner)
                        .returns(idFromEncodedId(nft.getSpender()), UniqueToken::getSpender)
                        .returns(nft.getMetadata(), UniqueToken::getMetadata));
    }

    @Test
    void getHistorical() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;

        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();

        when(nftRepository.findActiveByIdAndTimestamp(nft.getTokenId(), nft.getSerialNumber(), timestamp.get()))
                .thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(getNftKey(nft), timestamp))
                .hasValueSatisfying(uniqueToken -> assertThat(uniqueToken)
                        .returns(idFromEntityId(EntityId.of(nft.getTokenId())), UniqueToken::getTokenId)
                        .returns(nft.getId().getSerialNumber(), UniqueToken::getSerialNumber)
                        .returns(
                                new RichInstant(createdTimestampSecs, createdTimestampNanos),
                                UniqueToken::getCreationTime)
                        .returns(idFromEntityId(nft.getAccountId()), UniqueToken::getOwner)
                        .returns(idFromEncodedId(nft.getSpender()), UniqueToken::getSpender)
                        .returns(nft.getMetadata(), UniqueToken::getMetadata));
    }

    @Test
    void missingRichInstantWhenNoCreatedTimestamp() {
        Nft nft = domainBuilder.nft().customize(n -> n.createdTimestamp(null)).get();

        when(nftRepository.findActiveById(anyLong(), anyLong())).thenReturn(Optional.of(nft));

        assertThat(uniqueTokenDatabaseAccessor.get(getNftKey(nft), Optional.empty()))
                .hasValueSatisfying(uniqueToken ->
                        assertThat(uniqueToken.getCreationTime()).isEqualTo(RichInstant.MISSING_INSTANT));
    }

    private NftId getNftKey(final Nft nft) {
        final var nftId = nft.getId();
        final var tokenId = EntityId.of(nftId.getTokenId());
        return new NftId(tokenId.getShard(), tokenId.getRealm(), tokenId.getNum(), nftId.getSerialNumber());
    }
}
