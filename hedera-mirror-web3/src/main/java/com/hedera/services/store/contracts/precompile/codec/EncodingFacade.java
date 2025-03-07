// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BOOLEAN_TUPLE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_TUPLE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.NOT_SPECIFIED_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FunctionType.HAPI_BURN;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FunctionType.HAPI_MINT;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.burnReturnType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.intAddressTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.intTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.mintReturnType;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

public class EncodingFacade {
    public static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
    private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];

    public static Bytes resultFrom(@NonNull final ResponseCodeEnum status) {
        return UInt256.valueOf(status.getNumber());
    }

    static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(@NonNull final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(address.toUnsignedBigInteger()));
    }

    public Bytes encodeGetApproved(final int status, final Address approved) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_APPROVED)
                .withStatus(status)
                .withApproved(approved)
                .build();
    }

    public Bytes encodeAllowance(final int responseCode, final long allowance) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_ALLOWANCE)
                .withStatus(responseCode)
                .withAllowance(allowance)
                .build();
    }

    public Bytes encodeApprove(final boolean approve) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_APPROVE)
                .withApprove(approve)
                .build();
    }

    public Bytes encodeApprove(final int responseCode, final boolean approve) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_APPROVE)
                .withStatus(responseCode)
                .withApprove(approve)
                .build();
    }

    public Bytes encodeApproveNFT(final int responseCode) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_APPROVE_NFT)
                .withStatus(responseCode)
                .build();
    }

    public Bytes encodeMintSuccess(final long totalSupply, final long[] serialNumbers) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeBurnSuccess(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(HAPI_BURN)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeMintFailure(@NonNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .withSerialNumbers(NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeBurnFailure(@NonNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(HAPI_BURN)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .build();
    }

    public Bytes encodeEcFungibleTransfer(final boolean ercFungibleTransferStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TRANSFER)
                .withErcFungibleTransferStatus(ercFungibleTransferStatus)
                .build();
    }

    public Bytes encodeCreateSuccess(final Address newTokenAddress) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_CREATE)
                .withStatus(SUCCESS.getNumber())
                .withNewTokenAddress(newTokenAddress)
                .build();
    }

    public Bytes encodeCreateFailure(@NonNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_CREATE)
                .withStatus(status.getNumber())
                .withNewTokenAddress(Address.ZERO)
                .build();
    }

    public Bytes encodeIsApprovedForAll(final int status, final boolean isApprovedForAllStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_APPROVED_FOR_ALL)
                .withStatus(status)
                .withIsApprovedForAllStatus(isApprovedForAllStatus)
                .build();
    }

    private FunctionResultBuilder functionResultBuilder() {
        return new FunctionResultBuilder();
    }

    private static class FunctionResultBuilder {
        private FunctionType functionType;
        private TupleType tupleType;
        private int status;
        private Address newTokenAddress;
        private boolean ercFungibleTransferStatus;
        private boolean isApprovedForAllStatus;
        private long totalSupply;
        private long allowance;
        private boolean approve;
        private long[] serialNumbers;
        private Address approved;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType = switch (functionType) {
                case HAPI_CREATE, HAPI_GET_APPROVED -> intAddressTuple;
                case HAPI_MINT -> mintReturnType;
                case HAPI_BURN -> burnReturnType;
                case ERC_TRANSFER, ERC_APPROVE -> BOOLEAN_TUPLE;
                case HAPI_ALLOWANCE -> hapiAllowanceOfType;
                case HAPI_APPROVE, HAPI_IS_APPROVED_FOR_ALL -> INT_BOOL_TUPLE;
                case HAPI_APPROVE_NFT -> intTuple;
                default -> NOT_SPECIFIED_TYPE;};

            this.functionType = functionType;
            return this;
        }

        private FunctionResultBuilder withStatus(final int status) {
            this.status = status;
            return this;
        }

        private FunctionResultBuilder withNewTokenAddress(final Address newTokenAddress) {
            this.newTokenAddress = newTokenAddress;
            return this;
        }

        private FunctionResultBuilder withTotalSupply(final long totalSupply) {
            this.totalSupply = totalSupply;
            return this;
        }

        private FunctionResultBuilder withSerialNumbers(final long[] serialNumbers) {
            this.serialNumbers = serialNumbers;
            return this;
        }

        private FunctionResultBuilder withAllowance(final long allowance) {
            this.allowance = allowance;
            return this;
        }

        private FunctionResultBuilder withApprove(final boolean approve) {
            this.approve = approve;
            return this;
        }

        private FunctionResultBuilder withApproved(final Address approved) {
            this.approved = approved;
            return this;
        }

        private FunctionResultBuilder withErcFungibleTransferStatus(final boolean ercFungibleTransferStatus) {
            this.ercFungibleTransferStatus = ercFungibleTransferStatus;
            return this;
        }

        private FunctionResultBuilder withIsApprovedForAllStatus(final boolean isApprovedForAllStatus) {
            this.isApprovedForAllStatus = isApprovedForAllStatus;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case HAPI_CREATE -> Tuple.of(status, convertBesuAddressToHeadlongAddress(newTokenAddress));
                        case HAPI_MINT -> Tuple.of(status, BigInteger.valueOf(totalSupply), serialNumbers);
                        case HAPI_BURN -> Tuple.of(status, BigInteger.valueOf(totalSupply));
                        case ERC_TRANSFER -> Tuple.of(ercFungibleTransferStatus);
                        case ERC_APPROVE -> Tuple.of(approve);
                        case HAPI_APPROVE -> Tuple.of(status, approve);
                        case HAPI_APPROVE_NFT -> Tuple.of(status);
                        case HAPI_ALLOWANCE -> Tuple.of(status, BigInteger.valueOf(allowance));
                        case HAPI_GET_APPROVED -> Tuple.of(status, convertBesuAddressToHeadlongAddress(approved));
                        case HAPI_IS_APPROVED_FOR_ALL -> Tuple.of(status, isApprovedForAllStatus);
                        default -> Tuple.of(status);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }
    }

    public static class LogBuilder {
        final StringBuilder tupleTypes = new StringBuilder("(");
        private final List<Object> data = new ArrayList<>();
        private final List<LogTopic> topics = new ArrayList<>();
        private Address logger;

        public static LogBuilder logBuilder() {
            return new LogBuilder();
        }

        private static LogTopic generateLogTopic(final Object param) {
            byte[] array = new byte[] {};
            if (param instanceof Address address) {
                array = address.toArray();
            } else if (param instanceof BigInteger numeric) {
                array = numeric.toByteArray();
            } else if (param instanceof Long numeric) {
                array = BigInteger.valueOf(numeric).toByteArray();
            } else if (param instanceof Boolean bool) {
                array = new byte[] {(byte) (Boolean.TRUE.equals(bool) ? 1 : 0)};
            } else if (param instanceof Bytes bytes) {
                array = bytes.toArray();
            }

            return LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array)));
        }

        private static void addTupleType(final Object param, final StringBuilder stringBuilder) {
            if (param instanceof Address) {
                stringBuilder.append("address,");
            } else if (param instanceof BigInteger || param instanceof Long) {
                stringBuilder.append("uint256,");
            } else if (param instanceof Boolean) {
                stringBuilder.append("bool,");
            }
        }

        private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
            final byte[] expandedArray = new byte[32];

            System.arraycopy(
                    bytesToExpand, 0, expandedArray, expandedArray.length - bytesToExpand.length, bytesToExpand.length);
            return expandedArray;
        }

        public LogBuilder forLogger(final Address logger) {
            this.logger = logger;
            return this;
        }

        public LogBuilder forEventSignature(final Bytes eventSignature) {
            topics.add(generateLogTopic(eventSignature));
            return this;
        }

        public LogBuilder forDataItem(final Object dataItem) {
            data.add(convertDataItem(dataItem));
            addTupleType(dataItem, tupleTypes);
            return this;
        }

        public LogBuilder forIndexedArgument(final Object param) {
            topics.add(generateLogTopic(param));
            return this;
        }

        public Log build() {
            if (tupleTypes.length() > 1) {
                tupleTypes.deleteCharAt(tupleTypes.length() - 1);
                tupleTypes.append(")");
                final var tuple = Tuple.of(data.toArray());
                final var tupleType = TupleType.parse(tupleTypes.toString());
                return new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics);
            } else {
                return new Log(logger, Bytes.EMPTY, topics);
            }
        }

        private Object convertDataItem(final Object param) {
            if (param instanceof Address address) {
                return convertBesuAddressToHeadlongAddress(address);
            } else if (param instanceof Long numeric) {
                return BigInteger.valueOf(numeric);
            } else {
                return param;
            }
        }
    }
}
