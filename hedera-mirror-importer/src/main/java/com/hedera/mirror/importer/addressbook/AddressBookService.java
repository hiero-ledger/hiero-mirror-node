// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.addressbook;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;

public interface AddressBookService extends ConsensusNodeService {

    /**
     * Retrieves the latest address book from db
     *
     * @return returns AddressBook containing network node details
     */
    AddressBook getCurrent();

    boolean isAddressBook(EntityId entityId);

    AddressBook migrate();

    void update(FileData fileData);
}
