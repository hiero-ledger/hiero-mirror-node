// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.importer.migration.FixSidecarMigrationDeletedContractMigration.BATCH_SIZE;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.assertj.core.groups.Tuple;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
final class FixSidecarMigrationDeletedContractMigrationTest extends ImporterIntegrationTest {

    private final EntityRepository entityRepository;
    private final FixSidecarMigrationDeletedContractMigration migration;

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void chain() {
        // given two sidecar migration revisions without their transactions
        final long id = persistHistory(CONTRACT, false, 100, 200).getId();
        persistHistory(id, CONTRACT, true, 200, 300);
        persistHistory(id, CONTRACT, false, 300, 400);
        persistCurrent(id, CONTRACT, false, 400);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertHistory(tuple(id, false, Range.closedOpen(100L, 200L)));
    }

    @Test
    void chainEndedBySystemUndelete() {
        // given a system undelete after the sidecar migration revision
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistHistory(id, CONTRACT, false, 300, 400);
        persistHistory(id, CONTRACT, false, 400, 500);
        persistCurrent(id, CONTRACT, false, 500);
        persistSystemUndelete(id, 400);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, false, Range.atLeast(500L)));
        assertHistory(tuple(id, true, Range.closedOpen(200L, 400L)), tuple(id, false, Range.closedOpen(400L, 500L)));
    }

    @Test
    void current() {
        // given
        final long id = persistHistory(CONTRACT, false, 100, 200).getId();
        persistHistory(id, CONTRACT, true, 200, 300);
        persistCurrent(id, CONTRACT, false, 300);
        persistTransaction(300, TransactionType.CONTRACTUPDATEINSTANCE);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertHistory(tuple(id, false, Range.closedOpen(100L, 200L)));
    }

    @Test
    void deletedAgainAfterSystemUndelete() {
        // given a system undelete, a second delete and a sidecar migration revision
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistHistory(id, CONTRACT, false, 300, 400);
        persistHistory(id, CONTRACT, true, 400, 500);
        persistCurrent(id, CONTRACT, false, 500);
        persistSystemUndelete(id, 300);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(400L)));
        assertHistory(tuple(id, true, Range.closedOpen(200L, 300L)), tuple(id, false, Range.closedOpen(300L, 400L)));
    }

    @Test
    void currentDeletedAgain() {
        // given a delete after the sidecar migration revision
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistHistory(id, CONTRACT, false, 300, 400);
        persistCurrent(id, CONTRACT, true, 400);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void deletedTwice() {
        // given two deletes each followed by a sidecar migration revision
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistHistory(id, CONTRACT, false, 300, 400);
        persistHistory(id, CONTRACT, true, 400, 500);
        persistCurrent(id, CONTRACT, false, 500);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void manyContracts() {
        // given more contracts than a batch
        final var expected = new ArrayList<Tuple>();
        for (int i = 0; i <= BATCH_SIZE; i++) {
            final long deletedTimestamp = 1000L + i * 10L;
            final long undeletedTimestamp = deletedTimestamp + 5;
            final long id = persistHistory(CONTRACT, true, deletedTimestamp, undeletedTimestamp)
                    .getId();
            persistCurrent(id, CONTRACT, false, undeletedTimestamp);
            expected.add(tuple(id, true, Range.atLeast(deletedTimestamp)));
        }

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll())
                .extracting(AbstractEntity::getId, AbstractEntity::getDeleted, AbstractEntity::getTimestampRange)
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void notContract() {
        // given
        final var deleted = persistHistory(ACCOUNT, true, 200, 300);
        final var current = persistCurrent(deleted.getId(), ACCOUNT, false, 300);

        // when
        migration.doMigrate();

        // then
        assertUntouched(deleted, current);
    }

    @Test
    void nonZeroShardAndRealm() {
        // given
        final long id = EntityId.of(1, 2, domainBuilder.number()).getId();
        persistHistory(id, CONTRACT, true, 200, 300);
        persistCurrent(id, CONTRACT, false, 300);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void notDeleted() {
        // given
        final var previous = persistHistory(CONTRACT, false, 200, 300);
        final var current = persistCurrent(previous.getId(), CONTRACT, false, 300);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, current);
    }

    @Test
    void nullDeleted() {
        // given
        final var previous = persistHistory(CONTRACT, null, 200, 300);
        final var current = persistCurrent(previous.getId(), CONTRACT, false, 300);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, current);
    }

    @Test
    void repeatable() {
        // given
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistHistory(id, CONTRACT, false, 300, 400);
        persistCurrent(id, CONTRACT, false, 400);
        migration.doMigrate();

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void systemUndelete() {
        // given
        final var deleted = persistHistory(CONTRACT, true, 200, 300);
        final var current = persistCurrent(deleted.getId(), CONTRACT, false, 300);
        persistSystemUndelete(deleted.getId(), 300);

        // when
        migration.doMigrate();

        // then
        assertUntouched(deleted, current);
    }

    @Test
    void systemUndeleteBeforeDeletion() {
        // given
        final long id = persistHistory(CONTRACT, false, 100, 200).getId();
        persistHistory(id, CONTRACT, true, 200, 300);
        persistCurrent(id, CONTRACT, false, 300);
        persistSystemUndelete(id, 150);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertHistory(tuple(id, false, Range.closedOpen(100L, 200L)));
    }

    @Test
    void systemUndeleteOfOtherEntity() {
        // given
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistCurrent(id, CONTRACT, false, 300);
        persistSystemUndelete(domainBuilder.id(), 300);

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void systemUndeleteFailed() {
        // given a failed system undelete at the sidecar migration revision
        final long id = persistHistory(CONTRACT, true, 200, 300).getId();
        persistCurrent(id, CONTRACT, false, 300);
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(300L)
                        .entityId(EntityId.of(id))
                        .result(ResponseCodeEnum.INVALID_SIGNATURE_VALUE)
                        .type(TransactionType.SYSTEMUNDELETE.getProtoId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertCurrent(tuple(id, true, Range.atLeast(200L)));
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    private void assertCurrent(final Tuple... expected) {
        assertThat(entityRepository.findAll())
                .extracting(AbstractEntity::getId, AbstractEntity::getDeleted, AbstractEntity::getTimestampRange)
                .containsExactlyInAnyOrder(expected);
    }

    private void assertHistory(final Tuple... expected) {
        assertThat(findHistory(EntityHistory.class))
                .extracting(AbstractEntity::getId, AbstractEntity::getDeleted, AbstractEntity::getTimestampRange)
                .containsExactlyInAnyOrder(expected);
    }

    private void assertUntouched(final AbstractEntity history, final AbstractEntity current) {
        assertCurrent(tuple(current.getId(), current.getDeleted(), current.getTimestampRange()));
        assertHistory(tuple(history.getId(), history.getDeleted(), history.getTimestampRange()));
    }

    private AbstractEntity persistCurrent(
            final long id, final EntityType type, final boolean deleted, final long lowerTimestamp) {
        return domainBuilder
                .entity(id, lowerTimestamp)
                .customize(e -> e.deleted(deleted)
                        .timestampRange(Range.atLeast(lowerTimestamp))
                        .type(type))
                .persist();
    }

    private AbstractEntity persistHistory(
            final EntityType type, final Boolean deleted, final long lowerTimestamp, final long upperTimestamp) {
        return persistHistory(domainBuilder.id(), type, deleted, lowerTimestamp, upperTimestamp);
    }

    private AbstractEntity persistHistory(
            final long id,
            final EntityType type,
            final Boolean deleted,
            final long lowerTimestamp,
            final long upperTimestamp) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.deleted(deleted)
                        .id(id)
                        .timestampRange(Range.closedOpen(lowerTimestamp, upperTimestamp))
                        .type(type))
                .persist();
    }

    private void persistSystemUndelete(final long id, final long consensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .entityId(EntityId.of(id))
                        .type(TransactionType.SYSTEMUNDELETE.getProtoId()))
                .persist();
    }

    private void persistTransaction(final long consensusTimestamp, final TransactionType type) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).type(type.getProtoId()))
                .persist();
    }
}
