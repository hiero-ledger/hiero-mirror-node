// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.hiero.mirror.rest.model.Error;
import org.hiero.mirror.rest.model.ErrorStatus;
import org.hiero.mirror.rest.model.ErrorStatusMessagesInner;

/**
 * Builds the shared REST error response model so that all producers stay in the same format. This includes
 * {@code GenericControllerAdvice} as well as producers that cannot route through it, such as filters that run outside
 * the DispatcherServlet.
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {}

    public static Error create(String message, String detail) {
        final var errorMessage = new ErrorMessage();
        errorMessage.setMessage(message);
        errorMessage.setDetail(detail);
        return new Error().status(new ErrorStatus().addMessagesItem(errorMessage));
    }

    // Subclass that overrides nullable getters with @JsonInclude(NON_NULL) so that unset
    // fields are omitted from the serialized error response, matching the JS module behavior.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorMessage extends ErrorStatusMessagesInner {

        @Override
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getData() {
            return super.getData();
        }

        @Override
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getDetail() {
            return super.getDetail();
        }
    }
}
