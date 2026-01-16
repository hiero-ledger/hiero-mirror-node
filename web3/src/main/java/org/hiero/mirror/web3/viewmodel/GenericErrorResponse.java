// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("_status")
@Value
@NoArgsConstructor
public class GenericErrorResponse {
    private final List<ErrorMessage> messages = new ArrayList<>();

    public GenericErrorResponse(String message) {
        this(message, StringUtils.EMPTY, StringUtils.EMPTY);
    }

    public GenericErrorResponse(String message, String detail) {
        this(message, detail, StringUtils.EMPTY);
    }

    public GenericErrorResponse(String message, String detailedMessage, String data) {
        final var errorMessage = new ErrorMessage(message, detailedMessage, data);
        messages.add(errorMessage);
    }

    public GenericErrorResponse(
            String message, String detailedMessage, String data, List<ErrorMessage> childTransactionErrors) {
        this(message, detailedMessage, data);
        messages.addAll(childTransactionErrors);
    }

    public GenericErrorResponse(List<ErrorMessage> errorMessages) {
        this.messages.addAll(errorMessages);
    }

    @Value
    public static class ErrorMessage {
        private String message;
        private String detail;
        private String data;
    }
}
