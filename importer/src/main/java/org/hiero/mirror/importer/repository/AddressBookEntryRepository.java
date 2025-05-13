// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {}
