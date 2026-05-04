// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("address_book")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AddressBook {

    @Id
    private Long startConsensusTimestamp;

    private Long endConsensusTimestamp;

    @EqualsAndHashCode.Exclude
    @Builder.Default
    @ToString.Exclude
    @MappedCollection(idColumn = "consensus_timestamp")
    private List<AddressBookEntry> entries = new ArrayList<>();

    @ToString.Exclude
    private byte[] fileData;

    private EntityId fileId;

    private Integer nodeCount;
}
