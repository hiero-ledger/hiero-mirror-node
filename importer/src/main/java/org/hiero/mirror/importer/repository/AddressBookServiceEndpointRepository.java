// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookServiceEndpointRepository
        extends CrudRepository<AddressBookServiceEndpoint, AddressBookServiceEndpoint.Id> {}
