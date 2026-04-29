// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("contract")
@NoArgsConstructor
@SuperBuilder
public class Contract {

    // Removed @Convert. Mapping is handled by your global EntityIdConverter bean.
    // updatable = false is a JPA concept; Spring Data JDBC maps this via standard @Column.
    @Column("file_id")
    private EntityId fileId;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] initcode;

    @ToString.Exclude
    private byte[] runtimeBytecode;
}
