// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.PURE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface.FunctionType.VIEW;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;

import com.google.common.base.Suppliers;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.rest.model.ContractAction;
import org.hiero.mirror.rest.model.ContractActionsResponse;
import org.hiero.mirror.rest.model.ContractResult;
import org.hiero.mirror.test.e2e.acceptance.config.FeatureProperties;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

abstract class AbstractEstimateFeature extends BaseContractFeature {

    private static final int BASE_GAS_FEE = 21_000;
    private static final int ADDITIONAL_FEE_FOR_CREATE = 32_000;
    private static final long CODE_DEPOSIT_BYTE_COST = 200L;

    protected int lowerDeviation;
    protected int upperDeviation;
    protected Object gasConsumedSelector;

    @Autowired
    protected FeatureProperties featureProperties;

    // Temporary until consensus node with code deposit change is deployed to all environments.
    private final Supplier<Boolean> shouldUseCodeDepositCost = Suppliers.memoize(() -> {
        try {
            var blocksResponse = mirrorClient.getBlocks(Order.DESC, 1);
            verifyMirrorTransactionsResponse(mirrorClient, 200);

            if (blocksResponse != null && !blocksResponse.getBlocks().isEmpty()) {
                var latestBlock = blocksResponse.getBlocks().getFirst();
                String hapiVersion = latestBlock.getHapiVersion();
                if (hapiVersion != null) {
                    String[] versionParts = hapiVersion.split("\\.");
                    if (versionParts.length == 3) {
                        int minor = Integer.parseInt(versionParts[1]);
                        return minor >= featureProperties.getHapiMinorVersionWithoutGasRefund();
                    } else {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    });

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
     * Validates the gas estimation for a specific contract call without a value.
     * <p>
     * This method estimates the gas cost for a given contract call, and then checks whether the actual gas used falls
     * within an acceptable deviation range. It utilizes the provided call endpoint to perform the contract call and
     * then compares the estimated gas with the actual gas used.
     * @param contractId The ID of the contract to call.
     * @param method The method to call.
     * @param params The parameters to pass to the function.
     * @param sender The account ID of the sender.
     */
    protected void validateGasEstimation(
            final ContractId contractId,
            final ContractMethodInterface method,
            final ContractFunctionParameters params,
            final AccountId sender)
            throws ExecutionException, InterruptedException {
        var estimateGasResult =
                mirrorClient.estimateGasQueryTopLevelCall(contractId, method, params, sender, Optional.empty());

        assertWithinDeviation(method.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);

        if (contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .isContractCallLocalEstimate()
                && (VIEW.equals(method.getFunctionType()) || PURE.equals(method.getFunctionType()))) {
            try {
                var data = params.toBytes(method.getSelector()).toByteArray();
                contractClient.executeContractQuery(contractId, method.getSelector(), estimateGasResult, data);
            } catch (PrecheckStatusException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Validates the gas estimation for a specific contract call with value
     * @param contractId The ID of the contract to call.
     * @param method The method to call.
     * @param params The parameters to pass to the function.
     * @param sender The account ID of the sender.
     * @param value The value to send with the call.
     */
    protected void validateGasEstimation(
            final ContractId contractId,
            final ContractMethodInterface method,
            final ContractFunctionParameters params,
            final AccountId sender,
            final Optional<java.lang.Long> value)
            throws ExecutionException, InterruptedException {
        var estimateGasResult = mirrorClient.estimateGasQueryTopLevelCall(contractId, method, params, sender, value);

        assertWithinDeviation(method.getActualGas(), (int) estimateGasResult, lowerDeviation, upperDeviation);
    }

    /**
     * Validates the gas estimation for a specific contract call without function parameters
     * @param contractId The ID of the contract to call.
     * @param functionName The name of the function to call.
     * @param sender The account ID of the sender.
     * @param actualGas The actual gas used for the call.
     */
    protected void validateGasEstimation(
            final ContractId contractId, final String functionName, final AccountId sender, final int actualGas)
            throws ExecutionException, InterruptedException {
        var estimateGasResult = mirrorClient.estimateGasQueryWithoutParams(contractId, functionName, sender, actualGas);

        assertWithinDeviation(actualGas, (int) estimateGasResult, lowerDeviation, upperDeviation);
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

    protected void assertEthCallReturnsBadRequest(String block, String data, String contractAddress, int actualGas) {
        var contractCallRequest = ModelBuilder.contractCallRequest(actualGas)
                .block(block)
                .data(data)
                .to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    /**
     * Validates that a transaction has charged a proper amount of gas. This method uses manual calculation, combining
     * data from sidecar actions and intrinsic gas.
     *
     * @param txId the transaction that is going to be validated
     */
    protected void verifyGasConsumed(String txId) {
        int totalGasFee;
        try {
            totalGasFee = calculateIntrinsicValue(gasConsumedSelector);
        } catch (DecoderException e) {
            throw new RuntimeException("Failed to decode hexadecimal string.", e);
        }
        var gasConsumed = getGasConsumedByTransactionId(txId);
        var gasUsed = getGasFromActions(txId);
        assertThat(gasConsumed).isEqualTo(gasUsed + totalGasFee);
    }

    /**
     * Validates that a transaction including contract create has charged a proper amount of gas. This method uses manual calculation, combining
     * data from sidecar actions, runtime bytecode length for code deposit and intrinsic gas.
     *
     * @param txId the transaction that is going to be validated
     * @param contractId the contract for which to calculate the code deposit cost
     * @param hasNestedDeploy whether the smart contract transaction includes a nested contract create
     */
    protected void verifyGasConsumed(String txId, String contractId, boolean hasNestedDeploy) {
        int totalGasFee;
        try {
            totalGasFee = calculateIntrinsicValue(gasConsumedSelector);
        } catch (DecoderException e) {
            throw new RuntimeException("Failed to decode hexadecimal string.", e);
        }
        var gasConsumed = getGasConsumedByTransactionId(txId);
        var gasUsed = getGasFromActions(txId);
        // If there is a nested deploy the gas consumption is already captured in sidecars, so we shouldn't add
        // additional code deposit
        var codeDepositCost = !shouldUseCodeDepositCost.get() || hasNestedDeploy ? 0L : getCodeDepositGas(contractId);
        assertThat(gasConsumed).isEqualTo(gasUsed + codeDepositCost + totalGasFee);
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
            int initCodeCost = (values.length + Bytes32.SIZE - 1) / Bytes32.SIZE * 2;
            total += ADDITIONAL_FEE_FOR_CREATE + (shouldUseCodeDepositCost.get() ? initCodeCost : 0);
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

    /**
     * The EVM is charging additional gas cost during contract deploy for storing the runtime bytecode. We should add
     * this value on top of the current gas cost for contract creates.
     *
     * @param contractId the contract for which to calculate the code deposit cost
     */
    private long getCodeDepositGas(String contractId) {
        return Optional.ofNullable(mirrorClient.getContractInfo(contractId))
                .map(contractInfo -> {
                    String bytecode = contractInfo.getRuntimeBytecode();
                    if (bytecode != null && bytecode.startsWith("0x")) {
                        // Remove "0x" prefix and convert hex string to byte length
                        int byteLength = (bytecode.length() - 2) / 2;
                        return byteLength * CODE_DEPOSIT_BYTE_COST;
                    }
                    return 0L;
                })
                .orElse(0L);
    }

    private Long getGasConsumedByTransactionId(String transactionId) {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        return contractResult.getGasConsumed();
    }
}
