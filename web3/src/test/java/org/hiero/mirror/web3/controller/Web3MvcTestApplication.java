// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal application for {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest}
 * slice tests. Placed in this package so test context discovery prefers it over
 * {@link org.hiero.mirror.web3.Web3Application}, which imports JDBC configuration that slice
 * tests do not need.
 */
@SpringBootApplication
class Web3MvcTestApplication {}
