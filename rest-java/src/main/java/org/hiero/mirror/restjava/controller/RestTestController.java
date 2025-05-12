// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/teststuff2")
@RestController
public class RestTestController {

    @GetMapping
    public Map<String, Object> getTestStuff() {
        return Map.of("test", "test");
    }
}
