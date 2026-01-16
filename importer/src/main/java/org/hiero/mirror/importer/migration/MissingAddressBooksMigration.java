// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.AddressBookService;
import org.hiero.mirror.importer.repository.AddressBookServiceEndpointRepository;
import org.springframework.beans.factory.ObjectProvider;

@Named
public class MissingAddressBooksMigration extends RepeatableMigration {

    private final ObjectProvider<AddressBookService> addressBookServiceProvider;
    private final ObjectProvider<AddressBookServiceEndpointRepository> addressBookServiceEndpointRepositoryProvider;

    public MissingAddressBooksMigration(
            ObjectProvider<AddressBookService> addressBookServiceProvider,
            ObjectProvider<AddressBookServiceEndpointRepository> addressBookServiceEndpointRepositoryProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.addressBookServiceProvider = addressBookServiceProvider;
        this.addressBookServiceEndpointRepositoryProvider = addressBookServiceEndpointRepositoryProvider;
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
            serviceEndpointCount =
                    addressBookServiceEndpointRepositoryProvider.getObject().count();
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
