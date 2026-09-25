// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Authorization {

    private String address;
    private String chainId;
    private Long nonce;
    private String r;
    private String s;

    @Getter(onMethod_ = {@JsonAlias({"yParity", "yparity"}), @JsonProperty("y_parity")})
    @Setter(onMethod_ = {@JsonProperty("y_parity")})
    private String yParity;
}
