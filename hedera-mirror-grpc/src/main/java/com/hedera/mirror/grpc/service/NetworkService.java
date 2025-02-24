// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.service;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

public interface NetworkService {

    Flux<AddressBookEntry> getNodes(@Valid AddressBookFilter addressBookFilter);
}
