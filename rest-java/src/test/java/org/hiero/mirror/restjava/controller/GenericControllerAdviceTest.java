// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies that {@link GenericControllerAdvice} never exposes exception details, such as messages or stack traces, in
 * 5xx responses. Real endpoints are expected to map bad input to 4xx, so a dummy controller that always throws is used
 * to reach the 5xx handling path.
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

    @RestController
    private static final class ThrowingController {

        @GetMapping("/throw")
        String throwException() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }
    }
}
