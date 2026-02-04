// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.config;

import com.google.protobuf.GeneratedMessageLite;
import com.hedera.hashgraph.sdk.Transaction;
import lombok.CustomLog;
import org.hiero.mirror.monitor.config.RuntimeHintsConfiguration.CustomRuntimeHints;
import org.hiero.mirror.monitor.publish.transaction.TransactionSupplier;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AssignableTypeFilter;

@Configuration
@CustomLog
@ImportRuntimeHints(CustomRuntimeHints.class)
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            hints.resources().registerPattern("addressbook/*.pb"); // Hiero SDK internal address book
            registerOpenApi(hints);
            registerProtobufs(hints);
            registerTransactionSuppliers(hints);
        }

        /**
         * Jackson uses reflection to instantiate the OpenAPI generated response models returned via the REST API.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerOpenApi(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            scanner.findCandidateComponents(NetworkNodesResponse.class.getPackageName())
                    .forEach(b -> hints.reflection()
                            .registerType(
                                    TypeReference.of(b.getBeanClassName()),
                                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                    MemberCategory.ACCESS_DECLARED_FIELDS,
                                    MemberCategory.INVOKE_PUBLIC_METHODS));
        }

        /**
         * Protobuf lite uses reflection to enumerate the protobuf fields to generate the schema.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerProtobufs(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(GeneratedMessageLite.class));
            scanner.findCandidateComponents(Transaction.class.getPackageName()).forEach(b -> hints.reflection()
                    .registerType(TypeReference.of(b.getBeanClassName()), MemberCategory.ACCESS_DECLARED_FIELDS));
        }

        /**
         * The TransactionGenerator converts the generic properties in the config to TransactionSupplier instances.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerTransactionSuppliers(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(TransactionSupplier.class));
            scanner.findCandidateComponents(TransactionSupplier.class.getPackageName())
                    .forEach(b -> hints.reflection()
                            .registerType(
                                    TypeReference.of(b.getBeanClassName()),
                                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                    MemberCategory.ACCESS_DECLARED_FIELDS,
                                    MemberCategory.INVOKE_PUBLIC_METHODS));
        }
    }
}
