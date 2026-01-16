// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

@Data
public class MigrationProperties {

    private int checksum = 1;

    private boolean enabled = true;

    @NotNull
    private Map<String, String> params = new CaseInsensitiveMap<>();
}
