// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenKycStatusEnum {
    NOT_APPLICABLE(0),
    GRANTED(1),
    REVOKED(2);

    private final int id;

    @JsonValue
    public String getId() {
        return String.valueOf(id);
    }
}
