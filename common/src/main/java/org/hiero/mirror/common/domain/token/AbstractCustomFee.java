// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.jspecify.annotations.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.util.CollectionUtils;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCustomFee implements History {

    @Id
    private Long entityId;

    @JsonIgnore
    @Column("fixed_fees")
    @UpsertColumn(shouldCoalesce = false)
    private FixedFeesHolder fixedFeesColumn;

    @JsonIgnore
    @Column("fractional_fees")
    @UpsertColumn(shouldCoalesce = false)
    private FractionalFeesHolder fractionalFeesColumn;

    @JsonIgnore
    @Column("royalty_fees")
    @UpsertColumn(shouldCoalesce = false)
    private RoyaltyFeesHolder royaltyFeesColumn;

    // Persisted via RangeToPGobjectWritingConverter / PGobjectToRangeReadingConverter
    private Range<Long> timestampRange;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<FixedFee> getFixedFees() {
        return fixedFeesColumn == null ? null : fixedFeesColumn.items();
    }

    public void setFixedFees(List<FixedFee> value) {
        this.fixedFeesColumn = FixedFeesHolder.of(value);
    }

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<FractionalFee> getFractionalFees() {
        return fractionalFeesColumn == null ? null : fractionalFeesColumn.items();
    }

    public void setFractionalFees(List<FractionalFee> value) {
        this.fractionalFeesColumn = FractionalFeesHolder.of(value);
    }

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<RoyaltyFee> getRoyaltyFees() {
        return royaltyFeesColumn == null ? null : royaltyFeesColumn.items();
    }

    public void setRoyaltyFees(List<RoyaltyFee> value) {
        this.royaltyFeesColumn = RoyaltyFeesHolder.of(value);
    }

    public void addFixedFee(@NonNull FixedFee fixedFee) {
        var list = new ArrayList<FixedFee>();
        if (getFixedFees() != null) {
            list.addAll(getFixedFees());
        }
        list.add(fixedFee);
        setFixedFees(list);
    }

    public void addFractionalFee(@NonNull FractionalFee fractionalFee) {
        var list = new ArrayList<FractionalFee>();
        if (getFractionalFees() != null) {
            list.addAll(getFractionalFees());
        }
        list.add(fractionalFee);
        setFractionalFees(list);
    }

    public void addRoyaltyFee(@NonNull RoyaltyFee royaltyFee) {
        var list = new ArrayList<RoyaltyFee>();
        if (getRoyaltyFees() != null) {
            list.addAll(getRoyaltyFees());
        }
        list.add(royaltyFee);
        setRoyaltyFees(list);
    }

    @JsonIgnore
    public boolean isEmptyFee() {
        return CollectionUtils.isEmpty(getFixedFees())
                && CollectionUtils.isEmpty(getFractionalFees())
                && CollectionUtils.isEmpty(getRoyaltyFees());
    }

    public abstract static class AbstractCustomFeeBuilder<
            C extends AbstractCustomFee, B extends AbstractCustomFeeBuilder<C, B>> {

        public B fixedFees(List<FixedFee> fees) {
            this.fixedFeesColumn = FixedFeesHolder.of(fees);
            return self();
        }

        public B fractionalFees(List<FractionalFee> fees) {
            this.fractionalFeesColumn = FractionalFeesHolder.of(fees);
            return self();
        }

        public B royaltyFees(List<RoyaltyFee> fees) {
            this.royaltyFeesColumn = RoyaltyFeesHolder.of(fees);
            return self();
        }
    }
}
