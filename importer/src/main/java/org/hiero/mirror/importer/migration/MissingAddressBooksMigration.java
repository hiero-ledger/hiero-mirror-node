// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.AddressBookService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class MissingAddressBooksMigration extends RepeatableMigration {

    private final ObjectProvider<AddressBookService> addressBookServiceProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public MissingAddressBooksMigration(
            ObjectProvider<AddressBookService> addressBookServiceProvider,
            ObjectProvider<DataSource> dataSourceProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.addressBookServiceProvider = addressBookServiceProvider;
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public String getDescription() {
        return "Parse valid but unprocessed addressBook file_data rows into valid addressBooks";
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return true;
        }

        // skip when no address books with service endpoint exist. Allow normal flow migration to do initial population
        long serviceEndpointCount = 0;
        try {
            // Queried via the DataSource directly since this runs inside flyway, where resolving a Spring Data JDBC
            // repository would deadlock on the database initialization ordering
            final var jdbcTemplate = new JdbcTemplate(dataSourceProvider.getObject());
            final var count =
                    jdbcTemplate.queryForObject("select count(*) from address_book_service_endpoint", Long.class);
            serviceEndpointCount = count != null ? count : 0;
        } catch (Exception ex) {
            // catch ERROR: relation "address_book_service_endpoint" does not exist
            // this will occur in migration version before v1.37.1 where service endpoints were not supported by proto
            log.info("Error checking service endpoints: {}", ex.getMessage());
        }
        return serviceEndpointCount < 1;
    }

    @Override
    protected void doMigrate() {
        addressBookServiceProvider.getObject().migrate();
    }
}
