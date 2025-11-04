// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.hiero.mirror.restjava.common.RangeOperator.GT;
import static org.hiero.mirror.restjava.common.RangeOperator.GTE;
import static org.hiero.mirror.restjava.common.RangeOperator.LT;
import static org.hiero.mirror.restjava.common.RangeOperator.LTE;
import static org.hiero.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.common.EntityIdNumParameter;
import org.hiero.mirror.restjava.common.EntityIdRangeParameter;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

@NullUnmarked
@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends RestJavaIntegrationTest {

    private final NftAllowanceRepository nftAllowanceRepository;

    private Map<Tuple, NftAllowance> nftAllowances;
    private List<TestSpec> testSpecs;

    private List<Long> owners;
    private List<Long> spenders;
    private List<Long> tokenIds;

    private static EntityIdRangeParameter fromIndex(RangeOperator operator, List<Long> entityIds, int index) {
        return new EntityIdRangeParameter(operator, EntityId.of(entityIds.get(index)));
    }

    @Test
    void findAll() {

        // given
        setupNftAllowances();
        populateTestSpecs();

        // when, then
        assertNftAllowances();
    }

    @Test
    void findAllNoMatch() {
        // given
        setupNftAllowances();

        // when, then
        assertThat(nftAllowanceRepository.findAll(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(2) + 1)))
                                .ownerOrSpenderIds(new Bound(null, false, Constants.ACCOUNT_ID, NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(10)
                                .order(Direction.ASC)
                                .build(),
                        EntityId.of(owners.get(2) + 1)))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(EQ, EntityId.of(spenders.get(2) + 1))),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(10)
                                .order(Direction.ASC)
                                .build(),
                        EntityId.of(owners.get(0))))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(EQ, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(EQ, EntityId.of(tokenIds.get(2) + 1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(10)
                                .order(Direction.ASC)
                                .build(),
                        EntityId.of(owners.get(0))))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(GT, EntityId.of(spenders.get(2)))),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(GT, EntityId.of(tokenIds.get(0)))),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(10)
                                .order(Direction.ASC)
                                .build(),
                        EntityId.of(owners.get(0))))
                .isEmpty();

        // when, then
        assertThat(nftAllowanceRepository.findAll(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(LT, EntityId.of(spenders.get(0)))),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(LT, EntityId.of(tokenIds.get(2)))),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(10)
                                .order(Direction.ASC)
                                .build(),
                        EntityId.of(owners.get(0))))
                .isEmpty();
    }

    private void setupNftAllowances() {
        // Set up 3 (owners) x 3 (spenders) x 3 (tokens) NFT allowances
        var entityIds =
                IntStream.range(0, 9).mapToLong(x -> domainBuilder.id()).boxed().toList();
        owners = entityIds.subList(0, 3);
        spenders = entityIds.subList(3, 6);
        tokenIds = entityIds.subList(6, 9);
        nftAllowances = new HashMap<>();

        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            long owner = owners.get(ownerIndex);
            for (int spenderIndex = 0; spenderIndex < spenders.size(); spenderIndex++) {
                long spender = spenders.get(spenderIndex);
                for (int tokenIndex = 0; tokenIndex < tokenIds.size(); tokenIndex++) {
                    long tokenId = tokenIds.get(tokenIndex);
                    // true if sum of index is even, otherwise false
                    boolean approvedForAll = (ownerIndex + spenderIndex + tokenIndex) % 2 == 0;
                    var nftAllowance = domainBuilder
                            .nftAllowance()
                            .customize(n -> n.approvedForAll(approvedForAll)
                                    .owner(owner)
                                    .spender(spender)
                                    .tokenId(tokenId))
                            .persist();
                    nftAllowances.put(new Tuple(ownerIndex, spenderIndex, tokenIndex), nftAllowance);
                }
            }
        }
    }

    private void populateTestSpecs() {
        testSpecs = List.of(
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(null, false, Constants.ACCOUNT_ID, NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(0, 0, 0), new Tuple(0, 0, 2), new Tuple(0, 1, 1), new Tuple(0, 2, 0)),
                        "given owner 0, no bounds, limit 4, and asc, expect (0, 0), (0, 2), (1, 1), and, (2, 0)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(null, false, Constants.ACCOUNT_ID, NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.DESC)
                                .build(),
                        List.of(new Tuple(0, 2, 2), new Tuple(0, 2, 0), new Tuple(0, 1, 1), new Tuple(0, 0, 2)),
                        "given owner 0, no bounds, limit 4, and desc, expect (2, 2), (2, 0), (1, 1), and (0, 2)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(false)
                                .accountId(new EntityIdNumParameter(EntityId.of(spenders.get(1))))
                                .ownerOrSpenderIds(new Bound(null, false, Constants.ACCOUNT_ID, NFT_ALLOWANCE.OWNER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(0, 1, 1), new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(2, 1, 1)),
                        "given spender 1, no bounds, limit 4, and asc, expect (0, 1), (1, 0), (1, 2), and (2, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(false)
                                .accountId(new EntityIdNumParameter(EntityId.of(spenders.get(1))))
                                .ownerOrSpenderIds(new Bound(null, false, Constants.ACCOUNT_ID, NFT_ALLOWANCE.OWNER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.DESC)
                                .build(),
                        List.of(new Tuple(2, 1, 1), new Tuple(1, 1, 2), new Tuple(1, 1, 0), new Tuple(0, 1, 1)),
                        "given spender 1, no bounds, limit 4, and desc, expect (2, 1), (1, 2), (1, 0), and (0, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(EQ, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1)),
                        "given owner 1, spender 0, limit 4, and asc, expect (0, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1), new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(1, 2, 1)),
                        "given owner 1, spender >= 0, limit 4, and asc, expect (0, 1), (1, 0), (1, 2), and (2, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GTE, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1), new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(1, 2, 1)),
                        "given owner 1, spender >= 0, token >= 0, limit 4, and asc, expect (0, 1), (1, 0), (1, 2), and (2, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GT, tokenIds, 1)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(1, 2, 1)),
                        "given owner 1, spender >= 0, token > 1, limit 4, and asc, expect (1, 0), (1, 2), and (2, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(LTE, spenders, 2)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(LT, tokenIds, 2)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.DESC)
                                .build(),
                        List.of(new Tuple(1, 2, 1), new Tuple(1, 1, 2), new Tuple(1, 1, 0), new Tuple(1, 0, 1)),
                        "given owner 1, spender <= 2, token < 2, limit 4, and desc, expect (2, 1), (1, 2), (1, 0), and (0, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(LTE, spenders, 2), fromIndex(GTE, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(LT, tokenIds, 2), fromIndex(GT, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1), new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(1, 2, 1)),
                        "given owner 1, limit 4, and asc, expect (0, 0) < (0, 1), (1, 0), (1, 2), (2, 1) < (2, 2)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(LTE, spenders, 2), fromIndex(GT, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(LTE, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(6)
                                .order(Direction.DESC)
                                .build(),
                        List.of(new Tuple(1, 1, 2), new Tuple(1, 1, 0)),
                        "given owner 1, limit 4, and desc, expect (2, 0) >= (1, 2), (1, 0) > (0, *)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(EQ, spenders, 0)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(EQ, tokenIds, 1)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1)),
                        "given owner 1, limit 4, and order asc, expect (0, 1) = (0, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 1), fromIndex(LTE, spenders, 1)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(EQ, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 1, 0)),
                        "given owner 1, 1 <= spender <= 1, token 0, limit 4, and asc, expect (1, 0)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 1), fromIndex(LTE, spenders, 1)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GTE, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 1, 0), new Tuple(1, 1, 2)),
                        "given owner 1, 1 <= spender <= 1, token >= 0, limit 4, and asc, expect (1, 0) and (1, 2)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GT, spenders, 0), fromIndex(LT, spenders, 2)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GTE, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 1, 0), new Tuple(1, 1, 2)),
                        "given owner 1, 0 < spender < 2, token >= 0, limit 4, and asc, expect (1, 0) and (1, 2)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(1))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(GT, EntityId.of(spenders.get(0) - 1)),
                                                new EntityIdRangeParameter(LT, EntityId.of(spenders.get(2) + 1))),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(null, false, Constants.TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(1, 0, 1), new Tuple(1, 1, 0), new Tuple(1, 1, 2), new Tuple(1, 2, 1)),
                        "given owner 1, -1 < spender < 3, limit 4, and asc, expect (0, 1), (1, 0), (1, 2), and (2, 1))"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0), fromIndex(LTE, spenders, 2)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(EQ, tokenIds, 0)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(0, 0, 0), new Tuple(0, 2, 0)),
                        "given owner  0, 0 <= spender <= 2, token 0, limit 4, and asc, expect (0, 0) and (2, 0)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0), fromIndex(LTE, spenders, 2)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GTE, tokenIds, 1), fromIndex(LTE, tokenIds, 1)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(0, 0, 2), new Tuple(0, 1, 1), new Tuple(0, 2, 0)),
                        "given owner 0, limit 4, and asc, expect (0, 1) <= (0, 2), (1, 1), (2, 0) <= (2, 1)"),
                new TestSpec(
                        NftAllowanceRequest.builder()
                                .isOwner(true)
                                .accountId(new EntityIdNumParameter(EntityId.of(owners.get(0))))
                                .ownerOrSpenderIds(new Bound(
                                        paramToArray(fromIndex(GTE, spenders, 0), fromIndex(LTE, spenders, 2)),
                                        false,
                                        Constants.ACCOUNT_ID,
                                        NFT_ALLOWANCE.SPENDER))
                                .tokenIds(new Bound(
                                        paramToArray(fromIndex(GT, tokenIds, 0), fromIndex(LT, tokenIds, 2)),
                                        false,
                                        Constants.TOKEN_ID,
                                        NFT_ALLOWANCE.TOKEN_ID))
                                .limit(4)
                                .order(Direction.ASC)
                                .build(),
                        List.of(new Tuple(0, 0, 2), new Tuple(0, 1, 1), new Tuple(0, 2, 0)),
                        "given owner 0, limit 4, and asc, expect (0, 0) < (0, 2), (1, 1), (2, 0) < (2, 2)"));
    }

    private void assertNftAllowances() {
        var softAssertion = new SoftAssertions();
        for (var testSpec : testSpecs) {
            var request = testSpec.request();
            var expected = testSpec.expected().stream().map(nftAllowances::get).toList();
            softAssertion
                    .assertThat(nftAllowanceRepository.findAll(
                            request, ((EntityIdNumParameter) request.getAccountId()).id()))
                    .as(testSpec.description())
                    .containsExactlyElementsOf(expected);
        }

        softAssertion.assertAll();
    }

    private record TestSpec(NftAllowanceRequest request, List<Tuple> expected, String description) {}

    private record Tuple(int ownerIndex, int spenderIndex, int tokenIndex) {}
}
