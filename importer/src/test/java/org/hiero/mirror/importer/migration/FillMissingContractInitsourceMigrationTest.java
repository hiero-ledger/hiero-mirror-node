// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
class FillMissingContractInitsourceMigrationTest extends ImporterIntegrationTest {

    @Value("classpath:db/migration/v1/R__fill_missing_contract_initsource.sql")
    private final File migrationSql;

    private final ContractRepository contractRepository;

    @Test
    void empty() {
        migrate();
        assertThat(contractRepository.findAll()).isEmpty();
    }

    @Test
    void noParent() {
        var parent = domainBuilder.contract().customize(c -> c.fileId(null)).persist();

        migrate();

        assertContracts(parent);
    }

    @DisplayName("pre 0.23, child contract created in a contract call transaction")
    @Test
    void pre023() {
        var parent = domainBuilder.contract().persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        var grandchild = domainBuilder.contract().customize(c -> c.fileId(null)).persist();

        insertContractResult(parent, List.of(child.getId()));
        insertContractResult(child, List.of(grandchild.getId()));
        child.setFileId(parent.getFileId());
        grandchild.setFileId(parent.getFileId());

        migrate();

        assertContracts(parent, child, grandchild);
    }

    @DisplayName("pre 0.23, parent is also missing fileId")
    @Test
    void pre023NoParentFileId() {
        var parent = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(child.getId()));

        migrate();

        assertContracts(parent, child);
    }

    @DisplayName("post 0.23, child contract in its own contract create transaction")
    @Test
    void post023SyntheticContractCreate() {
        var parent = domainBuilder.contract().persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child, Collections.emptyList());
        child.setFileId(parent.getFileId());

        migrate();

        assertContracts(parent, child);
    }

    @DisplayName("post 0.26, child contract in its own contract create transaction, parent is created with initcode")
    @Test
    void post026ParentWithInitcode() {
        var parent = domainBuilder
                .contract()
                .customize(c -> c.fileId(null).initcode(domainBuilder.bytes(32)))
                .persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child, Collections.emptyList());
        child.setInitcode(parent.getInitcode());

        migrate();

        assertContracts(parent, child);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void insertContractResult(Contract caller, List<Long> createdContractIds) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.contractId(caller.getId()).createdContractIds(createdContractIds))
                .persist();
    }

    private void assertContracts(Contract... contracts) {
        assertThat(contractRepository.findAll()).containsExactlyInAnyOrder(contracts);
    }
}
