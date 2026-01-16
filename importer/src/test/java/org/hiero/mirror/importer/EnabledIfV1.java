// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.junit.jupiter.EnabledIf;

@EnabledIf("#{environment.acceptsProfiles('!v2')}")
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnabledIfV1 {}
