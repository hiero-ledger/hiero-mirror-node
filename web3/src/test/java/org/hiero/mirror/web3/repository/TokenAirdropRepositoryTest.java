// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@RequiredArgsConstructor
class TokenAirdropRepositoryTest extends Web3IntegrationTest {

    private final TokenAirdropRepository tokenAirdropRepository;

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void testFindByIdPendingSuccess(final TokenTypeEnum tokenType) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRange = Range.atLeast(domainBuilder.timestamp());

        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdrop(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.PENDING)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        final var expected = TokenAirdrop.builder()
                .senderAccountId(senderId)
                .receiverAccountId(receiverId)
                .tokenId(tokenId)
                .state(TokenAirdropStateEnum.PENDING)
                .serialNumber(serialNumber)
                .amount(amount)
                .timestampRange(timestampRange)
                .build();

        assertThat(tokenAirdropRepository
                        .findById(senderId, receiverId, tokenId, serialNumber)
                        .get())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON, CLAIMED
            FUNGIBLE_COMMON, CANCELLED
            NON_FUNGIBLE_UNIQUE, CLAIMED
            NON_FUNGIBLE_UNIQUE, CANCELLED
            """)
    void testFindByIdClaimedOrCancelledEmpty(final TokenTypeEnum tokenType, final TokenAirdropStateEnum state) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRange = Range.atLeast(domainBuilder.timestamp());

        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdrop(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(state)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        assertThat(tokenAirdropRepository.findById(senderId, receiverId, tokenId, serialNumber))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void testFindByIdPendingSuccessHistorical(final TokenTypeEnum tokenType) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRange = Range.atLeast(domainBuilder.timestamp());

        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdropHistory(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.PENDING)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        final var expected = TokenAirdrop.builder()
                .senderAccountId(senderId)
                .receiverAccountId(receiverId)
                .tokenId(tokenId)
                .state(TokenAirdropStateEnum.PENDING)
                .serialNumber(serialNumber)
                .amount(amount)
                .timestampRange(timestampRange)
                .build();

        assertThat(tokenAirdropRepository
                        .findByIdAndTimestamp(
                                senderId, receiverId, tokenId, serialNumber, timestampRange.lowerEndpoint())
                        .get())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON, CLAIMED
            FUNGIBLE_COMMON, CANCELLED
            NON_FUNGIBLE_UNIQUE, CLAIMED
            NON_FUNGIBLE_UNIQUE, CANCELLED
            """)
    void testFindByIdClaimedOrCancelledEmptyHistorical(
            final TokenTypeEnum tokenType, final TokenAirdropStateEnum state) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRange = Range.atLeast(domainBuilder.timestamp());

        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdropHistory(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(state)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        assertThat(tokenAirdropRepository.findByIdAndTimestamp(
                        senderId, receiverId, tokenId, serialNumber, timestampRange.lowerEndpoint()))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void testFindByIdAndTimestampReturnsCorrectValueForTimestamp(final TokenTypeEnum tokenType) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRangeHistorical = Range.atLeast(domainBuilder.timestamp());
        final var timestampRange = Range.atLeast(timestampRangeHistorical.lowerEndpoint() + 100L);

        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdrop(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.CLAIMED)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        domainBuilder
                .tokenAirdropHistory(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.PENDING)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRangeHistorical))
                .persist();

        final var expected = TokenAirdrop.builder()
                .senderAccountId(senderId)
                .receiverAccountId(receiverId)
                .tokenId(tokenId)
                .state(TokenAirdropStateEnum.PENDING)
                .amount(amount)
                .serialNumber(serialNumber)
                .timestampRange(timestampRangeHistorical)
                .build();

        assertThat(tokenAirdropRepository
                        .findByIdAndTimestamp(
                                senderId, receiverId, tokenId, serialNumber, timestampRangeHistorical.lowerEndpoint())
                        .get())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void testFindByIdAndTimestampReturnsCorrectValueForTimestampTwoHistoricalEntries(final TokenTypeEnum tokenType) {
        final var senderId = domainBuilder.entityId().getId();
        final var receiverId = domainBuilder.entityId().getId();
        final var tokenId = domainBuilder.entityId().getId();
        final var timestampRangeHistorical = Range.atLeast(domainBuilder.timestamp());
        final var timestampRange = Range.atLeast(timestampRangeHistorical.lowerEndpoint() + 100L);

        final var amountLatest = 2L;
        long amount;
        long serialNumber;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            amount = 1L;
            serialNumber = 0L;
        } else {
            amount = 0L;
            serialNumber = 123L;
        }

        domainBuilder
                .tokenAirdropHistory(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.PENDING)
                        .amount(amount)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRangeHistorical))
                .persist();

        domainBuilder
                .tokenAirdropHistory(tokenType)
                .customize(ta -> ta.senderAccountId(senderId)
                        .receiverAccountId(receiverId)
                        .tokenId(tokenId)
                        .state(TokenAirdropStateEnum.PENDING)
                        .amount(amountLatest)
                        .serialNumber(serialNumber)
                        .timestampRange(timestampRange))
                .persist();

        final var expected = TokenAirdrop.builder()
                .senderAccountId(senderId)
                .receiverAccountId(receiverId)
                .tokenId(tokenId)
                .state(TokenAirdropStateEnum.PENDING)
                .amount(amount)
                .serialNumber(serialNumber)
                .timestampRange(timestampRangeHistorical)
                .build();

        final var expectedLatest = expected.toBuilder()
                .timestampRange(timestampRange)
                .amount(amountLatest)
                .build();

        assertThat(tokenAirdropRepository
                        .findByIdAndTimestamp(
                                senderId, receiverId, tokenId, serialNumber, timestampRangeHistorical.lowerEndpoint())
                        .get())
                .isEqualTo(expected);
        assertThat(tokenAirdropRepository
                        .findByIdAndTimestamp(
                                senderId, receiverId, tokenId, serialNumber, timestampRange.lowerEndpoint())
                        .get())
                .isEqualTo(expectedLatest);
    }
}
