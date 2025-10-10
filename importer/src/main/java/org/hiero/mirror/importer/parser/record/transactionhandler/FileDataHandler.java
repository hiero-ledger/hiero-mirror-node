// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.protobuf.ByteString;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.AddressBookService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
final class FileDataHandler {

    private final AddressBookService addressBookService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    void handle(Transaction transaction, ByteString contents) {
        var fileId = transaction.getEntityId();
        var fileData = new FileData();
        fileData.setConsensusTimestamp(transaction.getConsensusTimestamp());
        fileData.setEntityId(fileId);
        fileData.setFileData(toBytes(contents));
        fileData.setTransactionType(transaction.getType());

        // We always store file data for address books since they're used by the address book service
        if (addressBookService.isAddressBook(fileId)) {
            addressBookService.update(fileData);
        } else if (entityProperties.getPersist().isFiles()
                || (entityProperties.getPersist().isSystemFiles() && DomainUtils.isSystemEntity(fileId))) {
            entityListener.onFileData(fileData);
        }
    }
}
