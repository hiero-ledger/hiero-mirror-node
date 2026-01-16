// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
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

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBook {
    // consensusTimestamp + 1ns of transaction containing final fileAppend operation
    @Id
    private Long startConsensusTimestamp;

    // consensusTimestamp of transaction containing final fileAppend operation of next address book
    private Long endConsensusTimestamp;

    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @JoinColumn(name = "consensusTimestamp")
    private List<AddressBookEntry> entries = new ArrayList<>();

    @ToString.Exclude
    private byte[] fileData;

    private EntityId fileId;

    private Integer nodeCount;
}
