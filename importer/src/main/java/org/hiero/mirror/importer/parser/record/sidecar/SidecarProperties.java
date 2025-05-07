// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.sidecar;

import com.hedera.services.stream.proto.SidecarType;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("hiero.mirror.importer.parser.record.sidecar")
@Validated
public class SidecarProperties {

    private boolean enabled = false;

    private boolean persistBytes = false;

    @NotNull
    private Set<SidecarType> types = Collections.emptySet();
}
