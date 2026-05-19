// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class AccessListEntry {
    private String address;
    private List<String> storageKeys;
}
