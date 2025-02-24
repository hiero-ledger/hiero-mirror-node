// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.AddressBookRepository;
import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;

@EnabledIfV1
@Tag("migration")
class Fix102AddressBookMigrationTest extends ImporterIntegrationTest {

    @Resource
    private AddressBookRepository addressBookRepository;

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.31.0__fix_address_book_102.sql")
    private File sql;

    @Test
    void latest102WithNonNullTimestamp() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 100L);
        AddressBook addressBook2 = addressBook(101, 101, null);
        AddressBook addressBook3 = addressBook(101, 201, null);

        runMigration();

        assertEndTimestamp(addressBook1, null);
        assertEndTimestamp(addressBook2, 200L);
        assertEndTimestamp(addressBook3, null);
    }

    @Test
    void previous102WithIncorrectTimestamp() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 200L);
        AddressBook addressBook2 = addressBook(102, 101, 300L);
        AddressBook addressBook3 = addressBook(101, 201, null);
        AddressBook addressBook4 = addressBook(101, 301, null);

        runMigration();

        assertEndTimestamp(addressBook1, 100L);
        assertEndTimestamp(addressBook2, null);
        assertEndTimestamp(addressBook3, 300L);
        assertEndTimestamp(addressBook4, null);
    }

    @Test
    void noChanges() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 100L);
        AddressBook addressBook2 = addressBook(102, 101, null);
        AddressBook addressBook3 = addressBook(101, 201, 300L);
        AddressBook addressBook4 = addressBook(101, 301, null);

        runMigration();

        assertEndTimestamp(addressBook1, 100L);
        assertEndTimestamp(addressBook2, null);
        assertEndTimestamp(addressBook3, 300L);
        assertEndTimestamp(addressBook4, null);
    }

    private AddressBook addressBook(long fileId, long startConsensusTimestamp, Long endConsensusTimestamp) {
        AddressBook addressBook = new AddressBook();
        addressBook.setEndConsensusTimestamp(endConsensusTimestamp);
        addressBook.setFileData(new byte[] {});
        addressBook.setFileId(EntityId.of(0, 0, fileId));
        addressBook.setStartConsensusTimestamp(startConsensusTimestamp);
        return addressBookRepository.save(addressBook);
    }

    private void runMigration() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(sql, "UTF-8"));
    }

    private void assertEndTimestamp(AddressBook addressBook, Long endConsensusTimestamp) {
        assertThat(addressBookRepository.findById(addressBook.getStartConsensusTimestamp()))
                .get()
                .extracting(AddressBook::getEndConsensusTimestamp)
                .isEqualTo(endConsensusTimestamp);
    }
}
