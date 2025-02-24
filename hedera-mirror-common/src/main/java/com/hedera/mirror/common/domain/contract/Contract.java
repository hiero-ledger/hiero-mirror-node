// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.contract;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@Entity
@NoArgsConstructor
@SuperBuilder
public class Contract {

    @Column(updatable = false)
    private EntityId fileId;

    @Id
    private Long id;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] initcode;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] runtimeBytecode;
}
