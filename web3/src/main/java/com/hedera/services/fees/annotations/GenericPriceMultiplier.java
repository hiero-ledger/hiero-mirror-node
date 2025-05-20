// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.PARAMETER})
@Qualifier
@Retention(RUNTIME)
public @interface GenericPriceMultiplier {}
