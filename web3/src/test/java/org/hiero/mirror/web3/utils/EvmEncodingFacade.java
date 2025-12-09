// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
@SuppressWarnings({"rawtypes", "unchecked"})
public class EvmEncodingFacade {

    public static Bytes encodeName(final String name) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_NAME)
                .withName(name)
                .build();
    }

    public static Bytes encodeSymbol(final String symbol) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_SYMBOL)
                .withSymbol(symbol)
                .build();
    }

    private static FunctionResultBuilder functionResultBuilder() {
        return new FunctionResultBuilder();
    }

    private static class FunctionResultBuilder {

        private FunctionType functionType;
        private TupleType tupleType;
        private String name;
        private String symbol;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType = ParsingConstants.stringTuple;
            this.functionType = functionType;
            return this;
        }

        private FunctionResultBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        private FunctionResultBuilder withSymbol(final String symbol) {
            this.symbol = symbol;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case ERC_NAME -> Tuple.from(name);
                        case ERC_SYMBOL -> Tuple.from(symbol);
                        default -> Tuple.from(true);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }
    }
}
