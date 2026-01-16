// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.util.UtilityTest.ALIAS_ECDSA_SECP256K1;
import static org.hiero.mirror.importer.util.UtilityTest.EVM_ADDRESS;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

@RequiredArgsConstructor
@Tag("migration")
class AccountEvmAddressMigrationTest extends ImporterIntegrationTest {

    private final JdbcOperations jdbcOperations;
    private final AccountEvmAddressMigration migration;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(1);
    }

    @Test
    void noAliases() throws Exception {
        insertEntity(1L, null, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isNull();
    }

    @Test
    void evmAddressAlreadySet() throws Exception {
        insertEntity(1L, ALIAS_ECDSA_SECP256K1, EVM_ADDRESS);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
    }

    @Test
    void aliasEd25519() throws Exception {
        byte[] aliasEd25519 = Hex.decode("1220000038746a20d630ceb81a24bd43798159108ec144e185c1c60a5e39fb933e2a");
        insertEntity(1L, aliasEd25519, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isNull();
    }

    @Test
    void evmAddressSet() throws Exception {
        insertEntity(1L, ALIAS_ECDSA_SECP256K1, null);
        insertEntity(2L, ALIAS_ECDSA_SECP256K1, null);
        migration.doMigrate();
        assertThat(findEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
        assertThat(findEvmAddress(2L)).isEqualTo(EVM_ADDRESS);
    }

    @Test
    void history() throws Exception {
        insertEntityHistory(1L, ALIAS_ECDSA_SECP256K1, null);
        insertEntityHistory(2L, ALIAS_ECDSA_SECP256K1, EVM_ADDRESS);
        migration.doMigrate();
        assertThat(findHistoryEvmAddress(1L)).isEqualTo(EVM_ADDRESS);
        assertThat(findHistoryEvmAddress(2L)).isEqualTo(EVM_ADDRESS);
    }

    private byte[] findEvmAddress(long id) {
        return jdbcOperations.queryForObject("select evm_address from entity where id = ?", byte[].class, id);
    }

    private byte[] findHistoryEvmAddress(long id) {
        return jdbcOperations.queryForObject("select evm_address from entity_history where id = ?", byte[].class, id);
    }

    private void insertEntity(long id, byte[] alias, byte[] evmAddress) {
        doInsertEntity(false, id, alias, evmAddress);
    }

    private void insertEntityHistory(long id, byte[] alias, byte[] evmAddress) {
        doInsertEntity(true, id, alias, evmAddress);
    }

    private void doInsertEntity(boolean history, long id, byte[] alias, byte[] evmAddress) {
        String suffix = history ? "_history" : "";
        String sql = String.format(
                "insert into entity%s (alias, created_timestamp, evm_address, id, num, realm, "
                        + "shard, timestamp_range, type) values (?,1,?,?,?,0,0,'[1,)','ACCOUNT')",
                suffix);
        jdbcOperations.update(sql, alias, evmAddress, id, id);
    }
}
