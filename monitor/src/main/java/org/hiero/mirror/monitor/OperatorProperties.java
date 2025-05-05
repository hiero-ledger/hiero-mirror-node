// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class OperatorProperties {

    public static final String DEFAULT_OPERATOR_ACCOUNT_ID = "0.0.2";

    @NotBlank
    private String accountId = DEFAULT_OPERATOR_ACCOUNT_ID;

    @NotBlank
    private String privateKey =
            "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";
}
