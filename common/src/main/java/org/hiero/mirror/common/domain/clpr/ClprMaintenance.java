// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A system-initiated {@code ClprMaintenance} transaction. The singleton values it touches (ledger configuration,
 * endpoint manifest) are persisted generically from their own state changes; this row only records the reason the
 * network gave for the maintenance.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class ClprMaintenance implements Persistable<Long> {

    @Id
    private long consensusTimestamp;

    // Raw com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason wire number -- preserved as-is even
    // if this mirror node doesn't recognize it yet, rather than losing a value a newer consensus node sent.
    private int reason;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }
}
