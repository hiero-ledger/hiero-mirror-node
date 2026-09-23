// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.NftRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixNftTreasuryChangeAllowanceMigrationTest extends ImporterIntegrationTest {

    private final FixNftTreasuryChangeAllowanceMigration migration;
    private final NftRepository nftRepository;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(nftRepository.findAll()).isEmpty();
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void noTreasuryChange() {
        // given
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var spender = domainBuilder.entityId();
        domainBuilder
                .token()
                .customize(t ->
                        t.tokenId(tokenId.getId()).treasuryAccountId(treasury).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        var nft = domainBuilder
                .nft()
                .customize(n -> n.accountId(treasury).spender(spender.getId()).tokenId(tokenId.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertThat(nftRepository.findAll()).containsExactly(nft);
    }

    @Test
    void singleTreasuryChange() {
        // given
        var tokenId = domainBuilder.entityId();
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var thirdParty = domainBuilder.entityId();
        var spender = domainBuilder.entityId();
        var delegatingSpender = domainBuilder.entityId();

        long createTimestamp = domainBuilder.timestamp();
        long changeTimestamp = createTimestamp + 100;

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(oldTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(createTimestamp, changeTimestamp)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(newTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(changeTimestamp)))
                .persist();

        // nft1 had a real spender/delegatingSpender before the change, wrongly nulled by the bug
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(oldTreasury)
                        .createdTimestamp(createTimestamp)
                        .delegatingSpender(delegatingSpender.getId())
                        .serialNumber(1)
                        .spender(spender.getId())
                        .timestampRange(Range.closedOpen(createTimestamp, changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();
        var nft1 = domainBuilder
                .nft()
                .customize(n -> n.accountId(newTreasury)
                        .createdTimestamp(createTimestamp)
                        .delegatingSpender(null)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();

        // nft2 legitimately had no spender before the change, should remain null
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(oldTreasury)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(2)
                        .timestampRange(Range.closedOpen(createTimestamp, changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();
        var nft2 = domainBuilder
                .nft()
                .customize(n -> n.accountId(newTreasury)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(2)
                        .timestampRange(Range.atLeast(changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();

        // nft3 was transferred to a third party before the treasury change, never touched by the bulk move
        var nft3 = domainBuilder
                .nft()
                .customize(n -> n.accountId(thirdParty)
                        .createdTimestamp(createTimestamp)
                        .spender(spender.getId())
                        .serialNumber(3)
                        .timestampRange(Range.atLeast(createTimestamp + 50))
                        .tokenId(tokenId.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        var expectedNft1 = nft1.toBuilder()
                .delegatingSpender(delegatingSpender.getId())
                .spender(spender.getId())
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, nft2, nft3);
    }

    @Test
    void chainedTreasuryChangesPropagateChronologically() {
        // given: two sequential treasury changes for the same nft, both wrongly nulled the spender in the buggy
        // past. The fix must apply oldest-event-first so the second event picks up the value restored by the
        // first, rather than propagating the still-null value forward.
        var tokenId = domainBuilder.entityId();
        var treasury1 = domainBuilder.entityId();
        var treasury2 = domainBuilder.entityId();
        var treasury3 = domainBuilder.entityId();
        var spender = domainBuilder.entityId();

        long createTimestamp = domainBuilder.timestamp();
        long firstChangeTimestamp = createTimestamp + 100;
        long secondChangeTimestamp = firstChangeTimestamp + 100;

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(treasury1)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(createTimestamp, firstChangeTimestamp)))
                .persist();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(treasury2)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(firstChangeTimestamp, secondChangeTimestamp)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(treasury3)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(secondChangeTimestamp)))
                .persist();

        // the nft's real spender before either treasury change
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(treasury1)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(1)
                        .spender(spender.getId())
                        .timestampRange(Range.closedOpen(createTimestamp, firstChangeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();
        // wrongly nulled at the first treasury change, as the old buggy code would have produced
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(treasury2)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.closedOpen(firstChangeTimestamp, secondChangeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();
        // wrongly nulled again at the second treasury change; this is the current row
        var nft = domainBuilder
                .nft()
                .customize(n -> n.accountId(treasury3)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(secondChangeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        var expectedNft = nft.toBuilder().spender(spender.getId()).build();
        assertThat(nftRepository.findAll()).containsExactly(expectedNft);
    }

    @Test
    void eventsSpanningMultipleBatches() {
        // given: force a small batch size so the 4 treasury-change events below are fetched across 3 keyset-paginated
        // queries (batch1: [tokenA, tokenChain@1st], batch2: [tokenChain@2nd, tokenB], batch3: empty). tokenChain's
        // two sequential changes deliberately straddle the batch1/batch2 boundary, to prove pagination doesn't miss
        // or misorder an event at the boundary.
        migration.eventBatchSize = 2;

        long tokenACreate = domainBuilder.timestamp();
        long tokenAChange = tokenACreate + 10;
        long tokenChainCreate = tokenACreate + 20;
        long tokenChainChange1 = tokenACreate + 30;
        long tokenChainChange2 = tokenACreate + 40;
        long tokenBCreate = tokenACreate + 50;
        long tokenBChange = tokenACreate + 60;

        // tokenA: single treasury change
        var tokenAId = domainBuilder.entityId();
        var tokenAOldTreasury = domainBuilder.entityId();
        var tokenANewTreasury = domainBuilder.entityId();
        var spenderA = domainBuilder.entityId();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenAId.getId())
                        .treasuryAccountId(tokenAOldTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(tokenACreate, tokenAChange)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenAId.getId())
                        .treasuryAccountId(tokenANewTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(tokenAChange)))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(tokenAOldTreasury)
                        .createdTimestamp(tokenACreate)
                        .serialNumber(1)
                        .spender(spenderA.getId())
                        .timestampRange(Range.closedOpen(tokenACreate, tokenAChange))
                        .tokenId(tokenAId.getId()))
                .persist();
        var nftA = domainBuilder
                .nft()
                .customize(n -> n.accountId(tokenANewTreasury)
                        .createdTimestamp(tokenACreate)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(tokenAChange))
                        .tokenId(tokenAId.getId()))
                .persist();

        // tokenChain: two sequential treasury changes for the same nft, split across the batch boundary
        var tokenChainId = domainBuilder.entityId();
        var tokenChainTreasury1 = domainBuilder.entityId();
        var tokenChainTreasury2 = domainBuilder.entityId();
        var tokenChainTreasury3 = domainBuilder.entityId();
        var spenderChain = domainBuilder.entityId();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenChainId.getId())
                        .treasuryAccountId(tokenChainTreasury1)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(tokenChainCreate, tokenChainChange1)))
                .persist();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenChainId.getId())
                        .treasuryAccountId(tokenChainTreasury2)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(tokenChainChange1, tokenChainChange2)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenChainId.getId())
                        .treasuryAccountId(tokenChainTreasury3)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(tokenChainChange2)))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(tokenChainTreasury1)
                        .createdTimestamp(tokenChainCreate)
                        .serialNumber(1)
                        .spender(spenderChain.getId())
                        .timestampRange(Range.closedOpen(tokenChainCreate, tokenChainChange1))
                        .tokenId(tokenChainId.getId()))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(tokenChainTreasury2)
                        .createdTimestamp(tokenChainCreate)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.closedOpen(tokenChainChange1, tokenChainChange2))
                        .tokenId(tokenChainId.getId()))
                .persist();
        var nftChain = domainBuilder
                .nft()
                .customize(n -> n.accountId(tokenChainTreasury3)
                        .createdTimestamp(tokenChainCreate)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(tokenChainChange2))
                        .tokenId(tokenChainId.getId()))
                .persist();

        // tokenB: single treasury change
        var tokenBId = domainBuilder.entityId();
        var tokenBOldTreasury = domainBuilder.entityId();
        var tokenBNewTreasury = domainBuilder.entityId();
        var spenderB = domainBuilder.entityId();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenBId.getId())
                        .treasuryAccountId(tokenBOldTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(tokenBCreate, tokenBChange)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenBId.getId())
                        .treasuryAccountId(tokenBNewTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(tokenBChange)))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(tokenBOldTreasury)
                        .createdTimestamp(tokenBCreate)
                        .serialNumber(1)
                        .spender(spenderB.getId())
                        .timestampRange(Range.closedOpen(tokenBCreate, tokenBChange))
                        .tokenId(tokenBId.getId()))
                .persist();
        var nftB = domainBuilder
                .nft()
                .customize(n -> n.accountId(tokenBNewTreasury)
                        .createdTimestamp(tokenBCreate)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(tokenBChange))
                        .tokenId(tokenBId.getId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        var expectedNftA = nftA.toBuilder().spender(spenderA.getId()).build();
        var expectedNftChain =
                nftChain.toBuilder().spender(spenderChain.getId()).build();
        var expectedNftB = nftB.toBuilder().spender(spenderB.getId()).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNftA, expectedNftChain, expectedNftB);
    }

    @Test
    void repeatableMigration() {
        // given
        var tokenId = domainBuilder.entityId();
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var spender = domainBuilder.entityId();

        long createTimestamp = domainBuilder.timestamp();
        long changeTimestamp = createTimestamp + 100;

        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(oldTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.closedOpen(createTimestamp, changeTimestamp)))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenId.getId())
                        .treasuryAccountId(newTreasury)
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .timestampRange(Range.atLeast(changeTimestamp)))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(oldTreasury)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(1)
                        .spender(spender.getId())
                        .timestampRange(Range.closedOpen(createTimestamp, changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();
        domainBuilder
                .nft()
                .customize(n -> n.accountId(newTreasury)
                        .createdTimestamp(createTimestamp)
                        .serialNumber(1)
                        .spender(null)
                        .timestampRange(Range.atLeast(changeTimestamp))
                        .tokenId(tokenId.getId()))
                .persist();

        // when
        migration.doMigrate();
        var firstPassNfts = nftRepository.findAll();

        migration.doMigrate();
        var secondPassNfts = nftRepository.findAll();

        // then
        assertThat(firstPassNfts).extracting("spender").containsExactly(spender.getId());
        assertThat(firstPassNfts).containsExactlyInAnyOrderElementsOf(secondPassNfts);
    }
}
