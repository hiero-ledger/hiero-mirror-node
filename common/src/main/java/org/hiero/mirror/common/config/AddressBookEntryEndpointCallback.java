// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;

/**
 * Scopes each {@link org.hiero.mirror.common.domain.addressbook.AddressBookEntry}'s service endpoints to its own node.
 *
 * <p>The JPA {@code @OneToMany} joined the endpoints on both {@code consensus_timestamp} and {@code node_id}. Spring
 * Data JDBC's {@code @MappedCollection} only supports a single back-reference column, so the endpoints are loaded by
 * {@code consensus_timestamp} alone, causing every entry in an address book to receive all of that book's endpoints
 * (an embedded composite id on the entry is rejected because the generated back-reference columns
 * {@code address_book_entry_consensus_timestamp}/{@code address_book_entry_node_id} do not exist). The correct set for
 * an entry is exactly the loaded endpoints whose {@code node_id} matches the entry, so this callback restores JPA
 * parity by dropping the cross-node endpoints after conversion, without an additional query.
 */
public class AddressBookEntryEndpointCallback implements AfterConvertCallback<AddressBook> {

    @Override
    public AddressBook onAfterConvert(AddressBook addressBook) {
        var entries = addressBook.getEntries();
        if (entries != null) {
            entries.forEach(entry -> {
                var endpoints = entry.getServiceEndpoints();
                if (endpoints != null) {
                    endpoints.removeIf(endpoint -> endpoint.getNodeId() != entry.getNodeId());
                }
            });
        }

        return addressBook;
    }
}
