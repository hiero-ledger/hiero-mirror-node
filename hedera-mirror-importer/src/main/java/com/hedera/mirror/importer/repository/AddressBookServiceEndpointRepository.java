// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookServiceEndpointRepository
        extends CrudRepository<AddressBookServiceEndpoint, AddressBookServiceEndpoint.Id> {}
