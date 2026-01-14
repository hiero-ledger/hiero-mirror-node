// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static org.hiero.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;
import static org.hiero.mirror.restjava.jooq.domain.tables.TokenAirdrop.TOKEN_AIRDROP;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import org.hiero.mirror.restjava.parameter.EntityIdNumParameter;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Sort.Direction;

@NullUnmarked
@RequiredArgsConstructor
class TokenAirdropRepositoryTest extends RestJavaIntegrationTest {

    private final TokenAirdropRepository repository;
    private final Long defaultReceiver = 100L;
    private final EntityIdParameter defaultAccountId = new EntityIdNumParameter(EntityId.of(defaultReceiver));
    // Setup objects for triple condition test
    private Map<ExpectedIndex, TokenAirdrop> airdrops;
    private List<TokenAirdropRepositoryTest.TestSpec> testSpecs;
    private List<Long> senders;
    private List<Long> serialNumbers;
    private List<Long> defaultTokenIds;

    private static Stream<Arguments> provideArguments() {
        return Stream.of(
                Arguments.of(Direction.ASC, OUTSTANDING),
                Arguments.of(Direction.DESC, OUTSTANDING),
                Arguments.of(Direction.ASC, PENDING),
                Arguments.of(Direction.DESC, PENDING));
    }

    @Test
    void findById() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        assertThat(repository.findById(tokenAirdrop.getId())).get().isEqualTo(tokenAirdrop);
    }

    @Test
    void findBySenderId() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .build();
        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
    }

    @Test
    void findByReceiverId() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getReceiverAccountId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .type(PENDING)
                .build();
        assertThat(repository.findAll(request, entityId)).contains(tokenAirdrop);
    }

    @Test
    void noMatch() {
        var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
        var entityId = EntityId.of(tokenAirdrop.getSenderAccountId());
        var request = TokenAirdropRequest.builder()
                .accountId(new EntityIdNumParameter(entityId))
                .entityIds(new Bound(
                        paramToArray(new EntityIdRangeParameter(RangeOperator.GT, tokenAirdrop.getReceiverAccountId())),
                        true,
                        Constants.ACCOUNT_ID,
                        TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID))
                .build();
        assertThat(repository.findAll(request, entityId)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideArguments")
    void fungibleTokensConditionalClausesByDirection(Direction order, AirdropRequestType type) {
        // Setup
        var sender = domainBuilder.entity().get();
        var receiver = domainBuilder.entity().get();
        var tokenId = domainBuilder.entityNum(5000).getId();

        var receiverSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).receiverAccountId(receiver.getId()))
                .persist();
        var receiverSpecifiedAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId()).receiverAccountId(receiver.getId()))
                .persist();
        var tokenReceiverSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .tokenId(tokenId))
                .persist();
        var tokenReceiverSpecifiedAirdrop2 = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a -> a.senderAccountId(sender.getId())
                        .receiverAccountId(receiver.getId())
                        .tokenId(tokenId + 1))
                .persist();
        var senderTokenSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a ->
                        a.senderAccountId(sender.getId()).receiverAccountId(1).tokenId(tokenId))
                .persist();
        var receiverTokenSpecifiedAirdrop = domainBuilder
                .tokenAirdrop(FUNGIBLE_COMMON)
                .customize(a ->
                        a.senderAccountId(1).receiverAccountId(receiver.getId()).tokenId(tokenId))
                .persist();

        // Default asc ordering by receiver, tokenId for outstanding airdrops
        var allOutstandingAirdrops = List.of(
                senderTokenSpecifiedAirdrop,
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        // Default asc ordering by sender, tokenId for pending airdrops
        var allPendingAirdrops = List.of(
                receiverTokenSpecifiedAirdrop,
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var outstandingReceiverSpecifiedAirdrops = List.of(
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);
        var pendingSenderSpecifiedAirdrops = List.of(
                receiverSpecifiedAirdrop,
                receiverSpecifiedAirdrop2,
                tokenReceiverSpecifiedAirdrop,
                tokenReceiverSpecifiedAirdrop2);

        var tokenReceiverSpecifiedAirdrops = List.of(tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);
        var outstandingTokenSpecifiedAirdrops =
                List.of(senderTokenSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);
        var pendingTokenSpecifiedAirdrops =
                List.of(receiverTokenSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop, tokenReceiverSpecifiedAirdrop2);

        var accountEntityId = type == OUTSTANDING ? sender.toEntityId() : receiver.toEntityId();
        var accountId = new EntityIdNumParameter(accountEntityId);
        var entity = type == OUTSTANDING ? receiver : sender;
        var entityIds = new Bound(
                paramToArray(new EntityIdRangeParameter(RangeOperator.EQ, entity.getId())),
                true,
                Constants.ACCOUNT_ID,
                type == OUTSTANDING ? TOKEN_AIRDROP.RECEIVER_ACCOUNT_ID : TOKEN_AIRDROP.SENDER_ACCOUNT_ID);
        var tokenIds = new Bound(
                paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, tokenId)),
                true,
                Constants.TOKEN_ID,
                TOKEN_AIRDROP.TOKEN_ID);

        var expectedResult = type == OUTSTANDING ? allOutstandingAirdrops : allPendingAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();

        // When
        var request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When receiver id condition for Outstanding Airdrops
        //   or sender id condition for Pending Airdrops
        expectedResult = type == OUTSTANDING ? outstandingReceiverSpecifiedAirdrops : pendingSenderSpecifiedAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .entityIds(entityIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id and receiver or sender condition
        expectedResult =
                order.isAscending() ? tokenReceiverSpecifiedAirdrops : tokenReceiverSpecifiedAirdrops.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .entityIds(entityIds)
                .order(order)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id condition as primary sort field and with receiver id
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .entityIds(entityIds)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);

        // When token id condition but no receiver id for outstanding airdrops nor receiver id for pending airdrops
        expectedResult = type == OUTSTANDING ? outstandingTokenSpecifiedAirdrops : pendingTokenSpecifiedAirdrops;
        expectedResult = order.isAscending() ? expectedResult : expectedResult.reversed();
        request = TokenAirdropRequest.builder()
                .accountId(accountId)
                .order(order)
                .tokenIds(tokenIds)
                .type(type)
                .build();
        // Then
        assertThat(repository.findAll(request, accountEntityId)).containsExactlyElementsOf(expectedResult);
    }

    @Test
    void findTripleConditions() {
        // given
        setupTripleConditionAirdrops();
        populateTripleConditionTestSpecs();

        // when, then
        assertTokenAirdrop();
    }

    private void setupTripleConditionAirdrops() {
        // Set up 3 (senders) x 3 (tokens) x 3 (serial number) airdrops
        // Receiver is always the same, so these are tested with Pending type Token Airdrop Requests
        var entityIds =
                IntStream.range(0, 9).mapToLong(x -> domainBuilder.id()).boxed().toList();
        senders = entityIds.subList(0, 3);
        defaultTokenIds = entityIds.subList(3, 6);
        serialNumbers = entityIds.subList(6, 9);
        airdrops = new HashMap<>();

        for (int senderIndex = 0; senderIndex < senders.size(); senderIndex++) {
            long sender = senders.get(senderIndex);
            for (int tokenIndex = 0; tokenIndex < defaultTokenIds.size(); tokenIndex++) {
                long tokenId = defaultTokenIds.get(tokenIndex);
                for (int serialIndex = 0; serialIndex < defaultTokenIds.size(); serialIndex++) {
                    long serial = serialNumbers.get(serialIndex);
                    var airdrop = domainBuilder
                            .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                            .customize(a -> a.receiverAccountId(defaultReceiver)
                                    .senderAccountId(sender)
                                    .serialNumber(serial)
                                    .tokenId(tokenId))
                            .persist();
                    airdrops.put(new ExpectedIndex(senderIndex, tokenIndex, serialIndex), airdrop);
                }
            }
        }
    }

    private void populateTripleConditionTestSpecs() {
        testSpecs = List.of(
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(0))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 0),
                                new ExpectedIndex(0, 0, 1),
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0)),
                        "given sender >= 0, token >=0, serial >=0, limit 4, expect (0, 0, 0), (0, 0, 1), (0, 0, 2), and, (0, 1, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(8)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(0))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0),
                                new ExpectedIndex(0, 1, 1),
                                new ExpectedIndex(0, 1, 2),
                                new ExpectedIndex(0, 2, 0),
                                new ExpectedIndex(0, 2, 1),
                                new ExpectedIndex(0, 2, 2),
                                new ExpectedIndex(1, 0, 0)),
                        "given sender >=0, token >=0, serial >=2, limit 8, expect (0, 0, 2), (0, 1, 0), (0, 1, 1), (0, 1, 2), (0, 2, 0), (0, 2, 1), (0, 2, 2), and, (1, 0, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(2))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(2))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(new ExpectedIndex(2, 2, 2)),
                        "given sender >=2, token >=2, serial >=2, limit 4, expect (2, 2, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(2))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(0))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(2, 0, 2),
                                new ExpectedIndex(2, 1, 0),
                                new ExpectedIndex(2, 1, 1),
                                new ExpectedIndex(2, 1, 2)),
                        "given sender >=2, token >=0, serial >=2, limit 4, expect (2, 0, 2), (2, 1, 0), (2, 1, 1), and, (2, 1, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(0))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(1, 0, 2),
                                new ExpectedIndex(1, 1, 0),
                                new ExpectedIndex(1, 1, 1),
                                new ExpectedIndex(1, 1, 2)),
                        "given sender >=1, token >=0, serial >=2, limit 4, expect (1, 0, 2), (1, 1, 0), (1, 1, 1), and, (1, 1, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(2))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(1, 2, 1),
                                new ExpectedIndex(1, 2, 2),
                                new ExpectedIndex(2, 0, 0),
                                new ExpectedIndex(2, 0, 1)),
                        "given sender >=1, token >=2, serial >=1, limit 4, expect (1, 2, 1), (1, 2, 2), (2, 0, 0), and, (2, 0, 1)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(2))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.EQ, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(1, 2, 0),
                                new ExpectedIndex(2, 0, 0),
                                new ExpectedIndex(2, 1, 0),
                                new ExpectedIndex(2, 2, 0)),
                        "given sender >=1, token >=2, serial =0, limit 4, expect (1, 2, 0) and (2, 0, 0) and (2, 1, 0) and (2, 2, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .order(Direction.DESC)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(2))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.EQ, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(2, 2, 0),
                                new ExpectedIndex(2, 1, 0),
                                new ExpectedIndex(2, 0, 0),
                                new ExpectedIndex(1, 2, 0)),
                        "given order = desc, sender >=1, token >=2, serial =0, limit 4, expect (2, 2, 0) and (2, 1, 0) and (2, 0, 0) and (1, 2, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.GTE, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 1, 0),
                                new ExpectedIndex(0, 1, 1),
                                new ExpectedIndex(0, 1, 2),
                                new ExpectedIndex(0, 2, 0)),
                        "given sender >=0, token >=1, serial >=0, limit 4, expect (0, 1, 0), (0, 1, 1), (0, 1, 2), and, (0, 2, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.EQ, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 1, 0),
                                new ExpectedIndex(0, 1, 1),
                                new ExpectedIndex(0, 1, 2),
                                new ExpectedIndex(0, 2, 0),
                                new ExpectedIndex(0, 2, 1),
                                new ExpectedIndex(0, 2, 2)),
                        "given sender =0, token >=1, serial >=0, expect (0, 1, 0), (0, 1, 1), (0, 1, 2), (0, 2, 0), (0, 2, 1), and, (0, 2, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.LT, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.EQ, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(new ExpectedIndex(0, 1, 0), new ExpectedIndex(0, 1, 1), new ExpectedIndex(0, 1, 2)),
                        "given sender <1, token =1, serial >=1, expect (0, 1, 0), (0, 1, 1), and, (0, 1, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.EQ, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 1, 1),
                                new ExpectedIndex(0, 1, 2),
                                new ExpectedIndex(1, 1, 1),
                                new ExpectedIndex(1, 1, 2)),
                        "given token =1, serial >=1, expect (0, 1, 1), (0, 1, 2), (1, 1, 1), and, (1, 1, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.LT, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 0),
                                new ExpectedIndex(0, 0, 1),
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0)),
                        "given sender <1, serial >=1, limit 4, expect (0, 0, 0), (0, 0, 1), (0, 0, 2), and (0, 1, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.LT, senders.get(2)),
                                                new EntityIdRangeParameter(RangeOperator.GT, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.LTE, defaultTokenIds.get(1)),
                                                new EntityIdRangeParameter(RangeOperator.GT, defaultTokenIds.get(0))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1)),
                                                new EntityIdRangeParameter(RangeOperator.LTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(new ExpectedIndex(1, 1, 1), new ExpectedIndex(1, 1, 2)),
                        "given 0 < sender < 2 , 0 < token <= 1, 1 <= serial <= 2, expect (1, 1, 1), (1, 1, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.LT, senders.get(1)),
                                                new EntityIdRangeParameter(RangeOperator.GTE, senders.get(0))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.EQ, defaultTokenIds.get(2))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(1)),
                                                new EntityIdRangeParameter(RangeOperator.LTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(new ExpectedIndex(0, 2, 1), new ExpectedIndex(0, 2, 2)),
                        "given 0 <= sender <1, token =1, 1 <= serial <=2, expect (0, 2, 1), and, (0, 2, 2)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.LT, senders.get(2))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.LTE, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GT, serialNumbers.get(0))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 0),
                                new ExpectedIndex(0, 0, 1),
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0)),
                        "given sender <2, token <=1, serial >0, limit 4, expect (0, 0, 0), (0, 0, 1), (0, 0, 2), and, (0, 1, 0)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .limit(8)
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 0),
                                new ExpectedIndex(0, 0, 1),
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0),
                                new ExpectedIndex(0, 1, 1),
                                new ExpectedIndex(0, 1, 2),
                                new ExpectedIndex(0, 2, 0),
                                new ExpectedIndex(0, 2, 1)),
                        "given limit 8, expect (0, 0, 0), (0, 0, 1), (0, 0, 2), (0, 1, 0), (0, 1, 1), (0, 1, 2), (0, 2, 0), and, (0, 2, 1)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .limit(8)
                                .order(Direction.DESC)
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(2, 2, 2),
                                new ExpectedIndex(2, 2, 1),
                                new ExpectedIndex(2, 2, 0),
                                new ExpectedIndex(2, 1, 2),
                                new ExpectedIndex(2, 1, 1),
                                new ExpectedIndex(2, 1, 0),
                                new ExpectedIndex(2, 0, 2),
                                new ExpectedIndex(2, 0, 1)),
                        "given limit 8, order desc expect (2, 2, 2), (2, 2, 1), (2, 2, 0), (2, 1, 2), (2, 1, 1), (2, 1, 0), (2, 0, 2), and, (2, 0, 1)"),
                new TokenAirdropRepositoryTest.TestSpec(
                        TokenAirdropRequest.builder()
                                .accountId(defaultAccountId)
                                .entityIds(new Bound(
                                        paramToArray(new EntityIdRangeParameter(RangeOperator.LT, senders.get(1))),
                                        true,
                                        Constants.ACCOUNT_ID,
                                        TOKEN_AIRDROP.SENDER_ACCOUNT_ID))
                                .limit(4)
                                .tokenIds(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GT, defaultTokenIds.get(1))),
                                        false,
                                        Constants.TOKEN_ID,
                                        TOKEN_AIRDROP.TOKEN_ID))
                                .serialNumbers(new Bound(
                                        paramToArray(
                                                new EntityIdRangeParameter(RangeOperator.GTE, serialNumbers.get(2))),
                                        false,
                                        Constants.SERIAL_NUMBER,
                                        TOKEN_AIRDROP.SERIAL_NUMBER))
                                .type(PENDING)
                                .build(),
                        List.of(
                                new ExpectedIndex(0, 0, 0),
                                new ExpectedIndex(0, 0, 1),
                                new ExpectedIndex(0, 0, 2),
                                new ExpectedIndex(0, 1, 0)),
                        "given sender <1, token >1, serial >=2, limit 4, expect (0, 0, 0), (0, 0, 1), (0, 0, 2), and, (0, 1, 0)"));
    }

    private void assertTokenAirdrop() {
        var softAssertion = new SoftAssertions();
        for (var testSpec : testSpecs) {
            var request = testSpec.request();
            var expected = testSpec.expected().stream().map(airdrops::get).toList();
            softAssertion
                    .assertThat(repository.findAll(request, ((EntityIdNumParameter) request.getAccountId()).id()))
                    .as(testSpec.description())
                    .containsExactlyElementsOf(expected);
        }

        softAssertion.assertAll();
    }

    private record TestSpec(TokenAirdropRequest request, List<ExpectedIndex> expected, String description) {}

    private record ExpectedIndex(int senderIndex, int tokenIndex, int serialIndex) {}
}
