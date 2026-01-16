// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
final class RequestFilter implements Predicate<ContractCallRequest> {

    @NotBlank
    private String expression;

    @NotNull
    private FilterField field = FilterField.DATA;

    @NotNull
    private FilterType type = FilterType.CONTAINS;

    @Override
    public boolean test(ContractCallRequest request) {
        var value = field.getExtractor().apply(request);
        var stringValue = value instanceof String s ? s : String.valueOf(value);
        return type.getPredicate().test(stringValue, expression);
    }

    @Getter
    @RequiredArgsConstructor
    enum FilterField {
        BLOCK(ContractCallRequest::getBlock),
        DATA(ContractCallRequest::getData),
        ESTIMATE(ContractCallRequest::isEstimate),
        FROM(ContractCallRequest::getFrom),
        GAS(ContractCallRequest::getGas),
        TO(ContractCallRequest::getTo),
        VALUE(ContractCallRequest::getValue);

        private final Function<ContractCallRequest, Object> extractor;
    }

    @Getter
    @RequiredArgsConstructor
    enum FilterType {
        CONTAINS(Strings.CI::contains),
        EQUALS(String::equalsIgnoreCase);

        private final BiPredicate<String, String> predicate;
    }
}
