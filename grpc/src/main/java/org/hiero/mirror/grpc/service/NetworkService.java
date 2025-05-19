// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import jakarta.validation.Valid;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.grpc.domain.AddressBookFilter;
import reactor.core.publisher.Flux;

public interface NetworkService {

    Flux<AddressBookEntry> getNodes(@Valid AddressBookFilter addressBookFilter);
}
