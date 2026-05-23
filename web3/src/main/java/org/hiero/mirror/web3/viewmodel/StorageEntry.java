// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;

/**
 * A single storage slot key-value pair used within a {@link StateOverride}.
 * Both {@code key} and {@code value} are 32-byte hex-encoded storage slot identifiers/values.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageEntry {

    @Hex
    private String key;

    @Hex
    private String value;
}
