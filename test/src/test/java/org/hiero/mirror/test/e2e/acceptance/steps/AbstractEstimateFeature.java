// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.VIEW;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;

import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.AssertionsForClassTypes;
import org.hiero.mirror.rest.model.ContractAction;
import org.hiero.mirror.rest.model.ContractActionsResponse;
import org.hiero.mirror.rest.model.ContractCallResponse;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.config.FeatureProperties;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

abstract class AbstractEstimateFeature extends BaseContractFeature {

    private static final int BASE_GAS_FEE = 21_000;
    private static final int ADDITIONAL_FEE_FOR_CREATE = 32_000;

    protected int lowerDeviation;
    protected int upperDeviation;
    protected Object gasConsumedSelector;

    @Autowired
    protected MirrorNodeClient mirrorClient;

    @Autowired
    protected FeatureProperties featureProperties;

    /**
     * Checks if the estimatedGas is within the specified range of the actualGas.
     * <p>
     * The method calculates the lower and upper bounds as percentages of the actualUsedGas, then checks if the
     * estimatedGas is within the range (inclusive).
     *
     * @param actualUsedGas     the integer value that represents the actualGas used value
     * @param estimatedGas      the integer value to be checked
     * @param lowerBoundPercent the integer percentage value for the lower bound of the acceptable range
     * @param upperBoundPercent the integer percentage value for the upper bound of the acceptable range
     */
    protected static void assertWithinDeviation(
            int actualUsedGas, int estimatedGas, int lowerBoundPercent, int upperBoundPercent) {
        int lowerDeviation = actualUsedGas * lowerBoundPercent / 100;
        int upperDeviation = actualUsedGas * upperBoundPercent / 100;

        int lowerBound = actualUsedGas + lowerDeviation;
        int upperBound = actualUsedGas + upperDeviation;

        assertThat(estimatedGas).as("Estimated gas is above lower bound").isGreaterThanOrEqualTo(lowerBound);
        assertThat(estimatedGas).as("Estimated gas is below upper bound").isLessThanOrEqualTo(upperBound);
    }

    /**
     * Validates the gas estimation for a specific contract call.
     * <p>
     * This method estimates the gas cost for a given contract call, and then checks whether the actual gas used falls
     * within an acceptable deviation range. It utilizes the provided call endpoint to perform the contract call and
     * then compares the estimated gas with the actual gas used.
     *
     * @param data            The function signature and method data of the contract call.
     * @param method          The method we are going to call.
     * @param contractEvmAddress        The address of the contract.
     * @param sender          The sender's address (optional).
     * *
     * @return the estimated gas
     * @throws AssertionError If the actual gas used is not within the acceptable deviation range.
     */
    protected long validateGasEstimationWithSender(
            String data, ContractMethodInterface method, String contractEvmAddress, Optional<String> sender) {
        var contractCallRequest = ModelBuilder.contractCallRequest(method.getActualGas())
                .data(data)
                .estimate(true)
                .to(contractEvmAddress);
        sender.ifPresent(contractCallRequest::from);

        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        int estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(method.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);

        return estimatedGas;
    }

    /**
     * Validates the gas estimation using a ContractCallLocal query against the network node
     * <p>
     * This method receives an estimated gas value made by eth_estimateGas and validates that it is sufficient to execute
     * a real network call using ContractCallLocal. This validation is only performed for view functions
     *
     * @param data            The function signature and method data of the contract call.
     * @param method          The method we are going to call.
     * @param contract        The contract object
     * @param estimatedGas          The estimated gas to use for the ContractCallLocal execution
     * *
     * @throws RuntimeException If the ContractCallLocal execution has thrown an exception.
     */
    private void validateGasViaContractCallLocal(
            String data, ContractMethodInterface method, DeployedContract contract, Long estimatedGas) {
        if (featureProperties.isContractCallLocalEstimate() && VIEW.equals(method.getFunctionType())) {
            System.out.println("I invoke ContractCallLocal");
            try {
                contractClient.executeContractQuery(
                        contract.contractId(), method.getSelector(), estimatedGas, Strings.decode(data));
            } catch (PrecheckStatusException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void validateGasEstimation(String data, ContractMethodInterface method, DeployedContract contract) {
        final var estimatedGas = validateGasEstimationWithSender(
                data, method, contract.contractId().toEvmAddress(), Optional.empty());
        validateGasViaContractCallLocal(data, method, contract, estimatedGas);
    }

    protected void validateGasEstimation(String data, ContractMethodInterface method, String contractEvmAddress) {
        validateGasEstimationWithSender(data, method, contractEvmAddress, Optional.empty());
    }

    protected void validateGasEstimationNestedCalls(
            String data, ContractMethodInterface method, String solidityAddress) {
        var contractCallRequest = ModelBuilder.contractCallRequestNestedCalls(method.getActualGas())
                .data(data)
                .estimate(true)
                .to(solidityAddress);

        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        int estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(method.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    /**
     * Asserts that a specific contract call results in a "400 Bad Request" response.
     * <p>
     * This method constructs a contract call request using the given encoded function call and contract address, and
     * then sends the request. It expects the call to result in a "400 Bad Request" response, and will throw an
     * assertion error if the response is anything other than that.
     *
     * @param data            The encoded function data to be sent.
     * @param contractAddress The address of the contract.
     * @throws AssertionError If the response from the contract call does not contain "400 Bad Request from POST".
     */
    protected void assertContractCallReturnsBadRequest(String data, String contractAddress) {
        var contractCallRequest =
                ModelBuilder.contractCallRequest().data(data).estimate(true).to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    protected void assertContractCallReturnsBadRequest(String data, int actualGas, String contractAddress) {
        var contractCallRequest = ModelBuilder.contractCallRequest(actualGas)
                .data(data)
                .estimate(true)
                .to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    protected void assertEthCallReturnsBadRequest(String block, String data, String contractAddress, int actualGas) {
        var contractCallRequest = ModelBuilder.contractCallRequest(actualGas)
                .block(block)
                .data(data)
                .to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    protected void verifyGasConsumed(String txId) {
        int totalGasFee;
        try {
            totalGasFee = calculateIntrinsicValue(gasConsumedSelector);
        } catch (DecoderException e) {
            throw new RuntimeException("Failed to decode hexadecimal string.", e);
        }
        var gasConsumed = getGasConsumedByTransactionId(txId);
        var gasUsed = getGasFromActions(txId);
        AssertionsForClassTypes.assertThat(gasConsumed).isEqualTo(gasUsed + totalGasFee);
    }

    /**
     * Calculates the total intrinsic gas required for a given operation, taking into account the
     * operation type and the data involved. This method adjusts the gas calculation based
     * on the type of operation: contract creation (CREATE) operations, indicated by a hexadecimal
     * string input, include an additional fee on top of the base gas fee. The intrinsic gas for
     * the data payload is calculated by adding a specific gas amount for each byte in the payload,
     * with different amounts for zero and non-zero bytes.
     *
     * @param data The operation data, which can be a hexadecimal string for CREATE operations or
     *             a byte array for contract call operations.
     * @return The total intrinsic gas calculated for the operation
     * @throws DecoderException If the data parameter is a String and cannot be decoded from hexadecimal
     *                          format, indicating an issue with the input format.
     * @throws IllegalArgumentException If the data parameter is not an instance of String or byte[],
     *                                  indicating that the provided data type is unsupported for gas
     *                                  calculation in the context of this method and tests.
     */
    private int calculateIntrinsicValue(Object data) throws DecoderException {
        int total = BASE_GAS_FEE;
        byte[] values;
        if (data instanceof String) {
            values = Hex.decodeHex(((String) data).replaceFirst(HEX_PREFIX, ""));
            total += ADDITIONAL_FEE_FOR_CREATE;
        } else if (data instanceof byte[]) {
            values = (byte[]) data;
        } else {
            throw new IllegalArgumentException("Unsupported data type for gas calculation.");
        }

        // Calculates the intrinsic value by adding 4 for each 0 bytes and 16 for non-zero bytes
        for (byte value : values) {
            total += (value == 0) ? 4 : 16;
        }
        return total;
    }

    private long getGasFromActions(String transactionId) {
        return Optional.ofNullable(mirrorClient.getContractResultActionsByTransactionId(transactionId))
                .map(ContractActionsResponse::getActions)
                .filter(actions -> !actions.isEmpty())
                .map(List::getFirst)
                .map(ContractAction::getGasUsed)
                .orElse(0L); // Provide a default value in case any step results in null
    }

    private Long getGasConsumedByTransactionId(String transactionId) {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        return contractResult.getGasConsumed();
    }
}
