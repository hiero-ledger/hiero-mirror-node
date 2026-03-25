// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerAnnotatedPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerResourcePatterns;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.swirlds.config.api.ConfigData;
import lombok.CustomLog;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
@NullMarked
final class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            ClassLoader loader = classLoader != null ? classLoader : getClass().getClassLoader();

            registerAnnotatedPackage(hints, loader, "com.hedera.node.config.data", ConfigData.class);

            registerPackage(hints, loader, ThrottleGroup.class.getPackageName());

            registerResourcePatterns(
                    hints,
                    "com/hedera/nativelib/hints/**",
                    "com/hedera/nativelib/wraps/**",
                    "genesis/**",
                    "semantic-version.properties");
        }
    }
}
