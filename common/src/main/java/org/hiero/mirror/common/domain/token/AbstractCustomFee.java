// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCustomFee implements History {

    @Id
    private Long entityId;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<FixedFee> fixedFees;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<FractionalFee> fractionalFees;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<RoyaltyFee> royaltyFees;

    private Range<Long> timestampRange;

    public void addFixedFee(@NonNull FixedFee fixedFee) {
        if (this.fixedFees == null) {
            this.fixedFees = new ArrayList<>();
        }

        this.fixedFees.add(fixedFee);
    }

    public void addFractionalFee(@NonNull FractionalFee fractionalFee) {
        if (this.fractionalFees == null) {
            this.fractionalFees = new ArrayList<>();
        }

        this.fractionalFees.add(fractionalFee);
    }

    public void addRoyaltyFee(@NonNull RoyaltyFee royaltyFee) {
        if (this.royaltyFees == null) {
            this.royaltyFees = new ArrayList<>();
        }

        this.royaltyFees.add(royaltyFee);
    }

    @JsonIgnore
    public boolean isEmptyFee() {
        return CollectionUtils.isEmpty(this.fixedFees)
                && CollectionUtils.isEmpty(this.fractionalFees)
                && CollectionUtils.isEmpty(this.royaltyFees);
    }
}
