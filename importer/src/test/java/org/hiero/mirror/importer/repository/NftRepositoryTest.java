// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NftRepositoryTest extends ImporterIntegrationTest {

    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;

    @Test
    void save() {
        var savedNft = nftRepository.save(domainBuilder.nft().get());
        assertThat(nftRepository.findById(savedNft.getId())).contains(savedNft);
    }

    @Test
    void updateTreasury() {
        // given
        var newTreasury = domainBuilder.entityId();
        var oldTreasury = domainBuilder.entityId();
        long tokenId = domainBuilder.id();

        var tokenAccountNewTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(newTreasury.getId()).balance(1).tokenId(tokenId))
                .persist();
        var tokenAccountOldTreasury = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(oldTreasury.getId()).balance(3).tokenId(tokenId))
                .persist();
        var nft1 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury).tokenId(tokenId))
                .persist();
        var nft2 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury).tokenId(tokenId))
                .persist();
        // The history row should preserve the delegating spender and spender
        var nft3 = domainBuilder
                .nft()
                .customize(n -> n.accountId(oldTreasury)
                        .delegatingSpender(domainBuilder.id())
                        .spender(domainBuilder.id())
                        .tokenId(tokenId))
                .persist();
        // Already owned by new treasury before the update
        var nft4 = domainBuilder
                .nft()
                .customize(n -> n.accountId(newTreasury).tokenId(tokenId))
                .persist();
        // Different token
        var nft5 = domainBuilder.nft().customize(n -> n.accountId(oldTreasury)).persist();
        // Owned by a third account
        var nft6 = domainBuilder.nft().customize(n -> n.tokenId(tokenId)).persist();

        // when
        var updateTimestamp = domainBuilder.timestamp();
        nftRepository.updateTreasury(updateTimestamp, newTreasury.getId(), oldTreasury.getId(), tokenId);

        // then
        tokenAccountOldTreasury.setBalance(0);
        tokenAccountOldTreasury.setBalanceTimestamp(updateTimestamp);
        tokenAccountNewTreasury.setBalance(4);
        tokenAccountNewTreasury.setBalanceTimestamp(updateTimestamp);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);

        var expectedNftList = Stream.concat(
                        Stream.of(nft1, nft2, nft3).map(Nft::toBuilder).map(n -> n.accountId(newTreasury)
                                .delegatingSpender(null)
                                .spender(null)
                                .timestampRange(Range.atLeast(updateTimestamp))
                                .build()),
                        Stream.of(nft4, nft5, nft6))
                .toList();
        // The only change to the history rows is closing the timestamp range
        var expectedNftHistoryList = Stream.of(nft1, nft2, nft3)
                .peek(n -> n.setTimestampUpper(updateTimestamp))
                .toList();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNftList);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(expectedNftHistoryList);
    }
}
