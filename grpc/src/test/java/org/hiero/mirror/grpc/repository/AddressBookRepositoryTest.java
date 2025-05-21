// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AddressBookRepositoryTest extends GrpcIntegrationTest {

    private final AddressBookRepository addressBookRepository;
    private final DomainBuilder domainBuilder;

    @Test
    void findLatestTimestamp() {
        EntityId fileId = EntityId.of(101L);
        assertThat(addressBookRepository.findLatestTimestamp(fileId.getId())).isEmpty();

        domainBuilder.addressBook().customize(a -> a.fileId(EntityId.of(999L))).persist();
        assertThat(addressBookRepository.findLatestTimestamp(fileId.getId())).isEmpty();

        AddressBook addressBook2 =
                domainBuilder.addressBook().customize(a -> a.fileId(fileId)).persist();
        assertThat(addressBookRepository.findLatestTimestamp(fileId.getId()))
                .get()
                .isEqualTo(addressBook2.getStartConsensusTimestamp());

        AddressBook addressBook3 =
                domainBuilder.addressBook().customize(a -> a.fileId(fileId)).persist();
        assertThat(addressBookRepository.findLatestTimestamp(fileId.getId()))
                .get()
                .isEqualTo(addressBook3.getStartConsensusTimestamp());
    }
}
