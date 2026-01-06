// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.ListUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = {"spring.flyway.target=1.85.1"})
class CustomFeesMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = """
            drop table if exists custom_fee;
            drop table if exists custom_fee_history;
            create table custom_fee (
                all_collectors_are_exempt boolean not null default false,
                amount                    bigint,
                amount_denominator        bigint,
                collector_account_id      bigint,
                created_timestamp         bigint  not null,
                denominating_token_id     bigint,
                maximum_amount            bigint,
                minimum_amount            bigint  not null default 0,
                net_of_transfers          boolean,
                royalty_denominator       bigint,
                royalty_numerator         bigint,
                token_id                  bigint  not null
            );
            create index if not exists custom_fee__token
               on custom_fee (token_id desc, created_timestamp desc);
            """;

    private static final RecursiveComparisonConfiguration COMPARISON_CONFIGURATION =
            RecursiveComparisonConfiguration.builder()
                    .withComparatorForFields(
                            CustomFeesMigrationTest::feeListComparator, "fixedFees", "fractionalFees", "royaltyFees")
                    .withIgnoreCollectionOrder(true)
                    .build();

    private final Map<Long, PostMigrationCustomFee> customFeeState = new HashMap<>();
    private final List<PostMigrationCustomFee> expectedHistoricCustomFees = new ArrayList<>();

    @Value("classpath:db/migration/v1/V1.85.2__custom_fee_aggregate_history.sql")
    private final Resource sql;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void multipleFeesMigration() {
        // given
        var initialTimestamp = 1638304200979990000L;
        var updateTimestamp = 1638304434160161000L;
        long tokenId = 603008;
        long collectorAccountOne = 603009;
        long collectorAccountTwo = 603011;
        long collectorAccountThree = 603013;
        long denominatingTokenId = 603006;
        var initial = PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .createdTimestamp(initialTimestamp)
                .minimumAmount(0L)
                .tokenId(tokenId)
                .build();
        var updateOne = PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .collectorAccountId(collectorAccountOne)
                .createdTimestamp(updateTimestamp)
                .minimumAmount(0L)
                .amount(100L)
                .tokenId(tokenId)
                .build();
        var updateTwo = PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .createdTimestamp(updateTimestamp)
                .minimumAmount(0L)
                .amount(10L)
                .collectorAccountId(collectorAccountTwo)
                .denominatingTokenId(denominatingTokenId)
                .tokenId(tokenId)
                .build();
        var updateThree = PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .createdTimestamp(updateTimestamp)
                .minimumAmount(0L)
                .amount(1L)
                .amountDenominator(10L)
                .collectorAccountId(collectorAccountThree)
                .netOfTransfers(false)
                .tokenId(tokenId)
                .build();

        persistMigrationCustomFees(List.of(initial, updateOne, updateTwo, updateThree));

        // when
        runMigration();

        // then
        assertThat(getAllCustomFees())
                .usingRecursiveComparison(COMPARISON_CONFIGURATION)
                .isEqualTo(customFeeState.values());
        assertThat(getAllHistoricCustomFees())
                .usingRecursiveComparison(COMPARISON_CONFIGURATION)
                .isEqualTo(expectedHistoricCustomFees);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(getAllCustomFees()).isEmpty();
        assertThat(getAllHistoricCustomFees()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        persistMigrationCustomFees(List.of(getFixedFee(), getFractionalFee(), getRoyaltyFee(), getEmptyFee()));

        // when
        runMigration();

        // then
        assertThat(getAllCustomFees())
                .usingRecursiveComparison(COMPARISON_CONFIGURATION)
                .isEqualTo(customFeeState.values());
        assertThat(getAllHistoricCustomFees())
                .usingRecursiveComparison(COMPARISON_CONFIGURATION)
                .isEqualTo(expectedHistoricCustomFees);
    }

    @Test
    void aggregateToSingleCustomFee() {
        // given
        var fixedFee = getFixedFee();
        var fixedFee2 = getFixedFee();
        var fractionalFee = getFractionalFee();
        var fractionalFee2 = getFractionalFee();
        var royaltyFee = getRoyaltyFee();
        var royaltyFee2 = getRoyaltyFee();
        var emptyFee = getEmptyFee();

        // Set all token ids and createdTimestamps to the same value,
        // they will then be aggregated into a single custom fee
        var allCustomFees =
                List.of(fixedFee, fixedFee2, fractionalFee, fractionalFee2, royaltyFee, royaltyFee2, emptyFee);
        allCustomFees.forEach(fee -> {
            fee.setCreatedTimestamp(fixedFee.getCreatedTimestamp());
            fee.setTokenId(fixedFee.getTokenId());
        });

        // Royalty Fee without Fallback Fee
        royaltyFee2.setAmount(null);

        persistMigrationCustomFees(allCustomFees);

        // when
        runMigration();

        // then
        assertThat(getAllCustomFees())
                .usingRecursiveComparison(COMPARISON_CONFIGURATION)
                .isEqualTo(customFeeState.values());
        assertThat(getAllHistoricCustomFees()).isEmpty();
    }

    private static int feeListComparator(Object first, Object second) {
        // The migration script doesn't enforce order on the fixedFees / fractionalFees / royaltyFees jsonb array, thus
        // the elements in any of the three jsonb array are with random order. The function compares the actual and
        // expected list with order ignored
        if (first == null && second == null) {
            return 0;
        }

        if (!(first instanceof List<?> firstList)
                || !(second instanceof List<?> secondList)
                || firstList.size() != secondList.size()) {
            return -1;
        }

        var duplicate = new ArrayList<>(secondList);
        for (Object firstElem : firstList) {
            for (int index = 0; index < duplicate.size(); index++) {
                if (Objects.equals(firstElem, duplicate.get(index))) {
                    duplicate.remove(index);
                    break;
                }
            }
        }

        return duplicate.isEmpty() ? 0 : -1;
    }

    private static <T> List<T> mergeList(List<T> first, List<T> second) {
        if (first == null && second == null) {
            return null;
        }

        return ListUtils.union(
                Objects.requireNonNullElse(first, Collections.emptyList()),
                Objects.requireNonNullElse(second, Collections.emptyList()));
    }

    private Collection<PostMigrationCustomFee> getAllCustomFees() {
        return findEntity(PostMigrationCustomFee.class, "token_id", "custom_fee");
    }

    private Collection<PostMigrationCustomFee> getAllHistoricCustomFees() {
        return findHistory("custom_fee", PostMigrationCustomFee.class);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            ownerJdbcTemplate.execute(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }

    private PreMigrationCustomFee getEmptyFee() {
        return PreMigrationCustomFee.builder()
                .createdTimestamp(domainBuilder.timestamp())
                .minimumAmount(0L)
                .tokenId(domainBuilder.id())
                .build();
    }

    private PreMigrationCustomFee getFixedFee() {
        return PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.number())
                .createdTimestamp(domainBuilder.timestamp())
                .denominatingTokenId(domainBuilder.id())
                .collectorAccountId(domainBuilder.id())
                .tokenId(domainBuilder.id())
                .build();
    }

    private PreMigrationCustomFee getFractionalFee() {
        return PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.number())
                .createdTimestamp(domainBuilder.timestamp())
                .denominatingTokenId(domainBuilder.id())
                .amountDenominator(domainBuilder.number())
                .collectorAccountId(domainBuilder.id())
                .maximumAmount(domainBuilder.number())
                .minimumAmount(1L)
                .netOfTransfers(true)
                .tokenId(domainBuilder.id())
                .build();
    }

    private PreMigrationCustomFee getRoyaltyFee() {
        return PreMigrationCustomFee.builder()
                .allCollectorsAreExempt(false)
                .amount(domainBuilder.number())
                .createdTimestamp(domainBuilder.timestamp())
                .collectorAccountId(domainBuilder.id())
                .denominatingTokenId(domainBuilder.id())
                .royaltyDenominator(10L)
                .royaltyNumerator(1L)
                .tokenId(domainBuilder.id())
                .build();
    }

    private void persistMigrationCustomFees(List<PreMigrationCustomFee> migrationCustomFees) {
        ownerJdbcTemplate.batchUpdate(
                """
                        insert into custom_fee (created_timestamp, token_id, all_collectors_are_exempt, amount,
                          amount_denominator, collector_account_id, denominating_token_id, maximum_amount,
                          minimum_amount, net_of_transfers, royalty_denominator, royalty_numerator)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                migrationCustomFees.stream()
                        .map(customFee -> new Object[] {
                            customFee.getCreatedTimestamp(),
                            customFee.getTokenId(),
                            customFee.isAllCollectorsAreExempt(),
                            customFee.getAmount(),
                            customFee.getAmountDenominator(),
                            customFee.getCollectorAccountId(),
                            customFee.getDenominatingTokenId(),
                            customFee.getMaximumAmount(),
                            customFee.getMinimumAmount(),
                            customFee.getNetOfTransfers(),
                            customFee.getRoyaltyDenominator(),
                            customFee.getRoyaltyNumerator()
                        })
                        .toList());

        // the pre-migration custom fees should be ordered by created timestamp to guarantee the correctness of the
        // merge logic
        for (var migrationCustomFee : migrationCustomFees) {
            var migrated = new PostMigrationCustomFee();
            migrated.setTokenId(migrationCustomFee.getTokenId());
            migrated.setTimestampLower(migrationCustomFee.getCreatedTimestamp());
            var collectorAccountId =
                    EntityIdConverter.INSTANCE.convertToEntityAttribute(migrationCustomFee.getCollectorAccountId());
            var denominatingTokenId =
                    EntityIdConverter.INSTANCE.convertToEntityAttribute(migrationCustomFee.getDenominatingTokenId());
            if (migrationCustomFee.getCollectorAccountId() != null
                    && migrationCustomFee.getAmount() != null
                    && migrationCustomFee.getAmountDenominator() == null
                    && migrationCustomFee.getRoyaltyDenominator() == null) {
                migrated.setFixedFees(List.of(FixedFee.builder()
                        .allCollectorsAreExempt(migrationCustomFee.isAllCollectorsAreExempt())
                        .amount(migrationCustomFee.getAmount())
                        .collectorAccountId(collectorAccountId)
                        .denominatingTokenId(denominatingTokenId)
                        .build()));
            } else if (migrationCustomFee.getAmountDenominator() != null) {
                migrated.setFractionalFees(List.of(FractionalFee.builder()
                        .allCollectorsAreExempt(migrationCustomFee.isAllCollectorsAreExempt())
                        .collectorAccountId(collectorAccountId)
                        .denominator(migrationCustomFee.getAmountDenominator())
                        .maximumAmount(migrationCustomFee.getMaximumAmount())
                        .minimumAmount(migrationCustomFee.getMinimumAmount())
                        .netOfTransfers(migrationCustomFee.getNetOfTransfers())
                        .numerator(migrationCustomFee.getAmount())
                        .build()));
            } else if (migrationCustomFee.getRoyaltyDenominator() != null) {
                var royaltyFee = RoyaltyFee.builder()
                        .allCollectorsAreExempt(migrationCustomFee.isAllCollectorsAreExempt())
                        .collectorAccountId(collectorAccountId)
                        .denominator(migrationCustomFee.getRoyaltyDenominator())
                        .numerator(migrationCustomFee.getRoyaltyNumerator());
                if (migrationCustomFee.getAmount() != null) {
                    royaltyFee.fallbackFee(FallbackFee.builder()
                            .amount(migrationCustomFee.getAmount())
                            .denominatingTokenId(denominatingTokenId)
                            .build());
                }

                migrated.setRoyaltyFees(List.of(royaltyFee.build()));
            }

            customFeeState.merge(migrated.getTokenId(), migrated, (previous, current) -> {
                if (!Objects.equals(previous.getTimestampLower(), current.getTimestampLower())) {
                    previous.setTimestampUpper(current.getTimestampLower());
                    expectedHistoricCustomFees.add(previous);
                    return current;
                }

                previous.setFixedFees(mergeList(previous.getFixedFees(), current.getFixedFees()));
                previous.setFractionalFees(mergeList(previous.getFractionalFees(), current.getFractionalFees()));
                previous.setRoyaltyFees(mergeList(previous.getRoyaltyFees(), current.getRoyaltyFees()));
                return previous;
            });
        }
    }

    @AllArgsConstructor
    @Data
    @Entity
    @NoArgsConstructor
    private static class PostMigrationCustomFee implements History {

        @JdbcTypeCode(SqlTypes.JSON)
        private List<FixedFee> fixedFees;

        @JdbcTypeCode(SqlTypes.JSON)
        private List<FractionalFee> fractionalFees;

        @JdbcTypeCode(SqlTypes.JSON)
        private List<RoyaltyFee> royaltyFees;

        private Range<Long> timestampRange;

        @Id
        private Long tokenId;
    }

    @AllArgsConstructor
    @Builder
    @Data
    private static class PreMigrationCustomFee {
        private boolean allCollectorsAreExempt;
        private Long amount;
        private Long amountDenominator;
        private Long collectorAccountId;
        private long createdTimestamp;
        private Long denominatingTokenId;
        private Long maximumAmount;
        private long minimumAmount;
        private Boolean netOfTransfers;
        private Long royaltyDenominator;
        private Long royaltyNumerator;
        private long tokenId;
    }
}
