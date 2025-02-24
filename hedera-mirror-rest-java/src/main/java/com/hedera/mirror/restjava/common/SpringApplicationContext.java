// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@Named
public class SpringApplicationContext implements ApplicationContextAware {

    private static final AtomicReference<ApplicationContext> CONTEXT = new AtomicReference<>();

    public static <T extends Object> T getBean(Class<T> beanClass) {
        return CONTEXT.get().getBean(beanClass);
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        CONTEXT.set(context);
    }
}
