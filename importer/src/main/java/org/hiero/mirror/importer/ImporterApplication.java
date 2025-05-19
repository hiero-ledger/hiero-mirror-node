// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import org.hiero.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CommonConfiguration.class)
@SpringBootApplication
public class ImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImporterApplication.class, args);
    }
}
