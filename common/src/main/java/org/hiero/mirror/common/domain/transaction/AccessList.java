// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class AccessList {
    private String address;
    private List<String> storageKeys;
}
