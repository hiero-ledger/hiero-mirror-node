// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies that {@link GenericControllerAdvice} preserves public error details in 404 responses and never exposes
 * internal exception details in 5xx responses.
 */
final class GenericControllerAdviceTest {

    private static final String INTERNAL_DETAIL = "Internal detail at org.hiero.mirror.restjava.Secret";

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GenericControllerAdvice(new RestJavaProperties()))
                .build();
    }

    @Test
    void unexpectedExceptionHidesDetails() throws Exception {
        mockMvc.perform(get("/throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$._status.messages.length()").value(1))
                .andExpect(jsonPath("$._status.messages[0].message").value("Internal Server Error"))
                .andExpect(jsonPath("$._status.messages[0].detail").value(""))
                .andExpect(content().string(Matchers.not(Matchers.containsString(INTERNAL_DETAIL))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Exception"))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void entityNotFoundPreservesErrorContract(boolean withCause) throws Exception {
        mockMvc.perform(get("/not-found").param("withCause", Boolean.toString(withCause)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                                {"_status":{"messages":[{"message":"Not Found","detail":"Topic not found"}]}}
                                """, JsonCompareMode.STRICT));
    }

    @RestController
    private static final class ThrowingController {

        @GetMapping("/throw")
        String throwException() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }

        @GetMapping("/not-found")
        String entityNotFound(@RequestParam boolean withCause) {
            if (withCause) {
                throw new EntityNotFoundException("Topic not found", new IllegalStateException(INTERNAL_DETAIL));
            }
            throw new EntityNotFoundException("Topic not found");
        }
    }
}
