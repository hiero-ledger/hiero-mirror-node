// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table
@NoArgsConstructor
@SuperBuilder
public class Contract {

    @Column("file_id")
    @InsertOnlyProperty
    private EntityId fileId;

    @Id
    private Long id;

    @InsertOnlyProperty
    @ToString.Exclude
    private byte[] initcode;

    @InsertOnlyProperty
    @ToString.Exclude
    private byte[] runtimeBytecode;
}
