// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.stream.proto.ContractActionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixContractActionZeroAddressRecipientMigrationTest
        extends AbstractAsyncJavaMigrationTest<FixContractActionZeroAddressRecipientMigration> {

    private static final long DAY = 86_400_000_000_000L;

    private final ContractActionRepository contractActionRepository;

    @Getter
    private final FixContractActionZeroAddressRecipientMigration migration;

    @Test
    void empty() {
        runMigration();
        waitForCompletion();

        assertThat(contractActionRepository.findAll()).isEmpty();
        assertThat(progressTableExists()).isFalse();
    }

    @Test
    void migrate() {
        // given - zero address precompile actions in the first and the last batch
        final var first = persistContractAction(DAY, ContractActionType.PRECOMPILE, null, null);
        final var last = persistContractAction(DAY * 5, ContractActionType.PRECOMPILE, null, null);
        final var precompile = persistContractAction(DAY * 2, ContractActionType.PRECOMPILE, EntityId.of(359), null);
        final var targeted = persistContractAction(DAY * 3, ContractActionType.PRECOMPILE, null, new byte[] {1});
        final var notSetCall = persistContractAction(DAY * 3, ContractActionType.CALL, null, null);
        final var failedCreate = persistContractAction(DAY * 4, ContractActionType.CREATE, null, null);

        // when
        runMigration();
        waitForCompletion();

        // then
        first.setRecipientAddress(new byte[20]);
        last.setRecipientAddress(new byte[20]);
        assertThat(contractActionRepository.findAll())
                .containsExactlyInAnyOrder(first, last, precompile, targeted, notSetCall, failedCreate);
        assertThat(progressTableExists()).isFalse();
    }

    private ContractAction persistContractAction(
            final long consensusTimestamp,
            final ContractActionType callType,
            final EntityId recipientContract,
            final byte[] recipientAddress) {
        return domainBuilder
                .contractAction()
                .customize(action -> action.callType(callType.getNumber())
                        .consensusTimestamp(consensusTimestamp)
                        .recipientAccount(null)
                        .recipientAddress(recipientAddress)
                        .recipientContract(recipientContract))
                .persist();
    }

    private boolean progressTableExists() {
        return Boolean.TRUE.equals(jdbcOperations.queryForObject(
                "select to_regclass('fix_contract_action_zero_address_progress_temp') is not null", Boolean.class));
    }
}
