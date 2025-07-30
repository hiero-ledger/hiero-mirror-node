// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.client.NetworkAdapter.BIG_INTEGER_TUPLE;
import static org.hiero.mirror.test.e2e.acceptance.client.NetworkAdapter.BYTES_TUPLE;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.COPY_CODE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.DESTROY_CONTRACT;
import static org.hiero.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_BALANCE;
import static org.hiero.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_HASH;
import static org.hiero.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_SIZE;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asAddress;

import com.esaulpaugh.headlong.abi.TupleType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;

@CustomLog
@RequiredArgsConstructor
public class EquivalenceFeature extends AbstractFeature {

    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String BAD_REQUEST = "400 ";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";

    private final AcceptanceTestProperties acceptanceTestProperties;
    private DeployedContract equivalenceDestructContract;
    private DeployedContract equivalenceCallContract;

    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() {
        equivalenceDestructContract = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                equivalenceDestructContract.contractId().toEvmAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() {
        equivalenceCallContract = getContract(EQUIVALENCE_CALL);
        equivalenceCallContractSolidityAddress =
                equivalenceCallContract.contractId().toEvmAddress();
    }

    @RetryAsserts
    @Given("I verify the equivalence contract bytecode is deployed")
    public void verifyEquivalenceContractDeployed() {
        verifyContractDeployed(equivalenceCallContractSolidityAddress);
    }

    @RetryAsserts
    @Given("I verify the selfdestruct contract bytecode is deployed")
    public void verifyDestructContractDeployed() {
        verifyContractDeployed(equivalenceDestructContractSolidityAddress);
    }

    private void verifyContractDeployed(String contractAddress) {
        var response = mirrorClient.getContractInfo(contractAddress);
        assertThat(response.getBytecode()).isNotBlank();
        assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    @Then("I execute selfdestruct and set beneficiary to {long} num")
    public void selfDestructAndSetBeneficiary(final long num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, asAddress(num));
        var functionResult = callContract(
                nodeType, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);

        // The contract is removed from the map when executed against the consensus node to prevent potential issues
        // in subsequent runs. This is because if the contract were not removed, the system would seek the cached
        // address on the next execution.
        if (acceptanceTestProperties.getNodeType().equals(NodeNameEnum.CONSENSUS)) {
            removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        }

        final var message = functionResult.getResultAsText();

        boolean condition;
        if (num < 751) {
            condition = message.startsWith(BAD_REQUEST) || message.equals(INVALID_SOLIDITY_ADDRESS_EXCEPTION);
        } else {
            condition = functionResult.getResult().equals(HEX_PREFIX) || message.equals(TRANSACTION_SUCCESSFUL_MESSAGE);
        }
        assertThat(condition).as("Unexpected error '%s'", message).isTrue();
    }

    @Then("I execute balance opcode to system account {long} num would return 0")
    public void balanceOfAddress(final long num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, asAddress(num));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        assertThat(functionResult.getResultAsNumber()).isEqualTo(BigInteger.ZERO);
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, asAddress(equivalenceDestructContract.contractId()));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        assertThat(functionResult.getResultAsNumber()).isEqualTo(new BigInteger("10000"));
    }

    @Then("I verify extcodesize opcode against a system account {long} num returns 0")
    public void extCodeSizeAgainstSystemAccount(final long num) {
        var nodeType = acceptanceTestProperties.getNodeType();

        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_SIZE, asAddress(num));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_SIZE, data, BIG_INTEGER_TUPLE);
        assertThat(functionResult.getResultAsNumber()).isEqualTo(BigInteger.ZERO);
    }

    @Then("I verify extcodecopy opcode against a system account {long} num returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(final long num) {
        var nodeType = acceptanceTestProperties.getNodeType();

        var data = encodeData(EQUIVALENCE_CALL, COPY_CODE, asAddress(num));
        var functionResult = callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, COPY_CODE, data, BYTES_TUPLE);
        assertThat(functionResult.getResultAsText()).isEmpty();
    }

    @Then("I verify extcodehash opcode against a system account {long} num returns empty bytes")
    public void extCodeHashAgainstSystemAccount(final long num) {
        var nodeType = acceptanceTestProperties.getNodeType();

        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_HASH, asAddress(num));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_HASH, data, BYTES_TUPLE);
        assertThat(functionResult.getResultAsBytes().toArray()).isEmpty();
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(
                EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, asAddress(equivalenceDestructContractSolidityAddress));
        var functionResult = callContract(
                nodeType, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);
        if (nodeType.equals(NodeNameEnum.CONSENSUS)) {
            removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        }
        var message = functionResult.getResultAsText();
        var condition = message.startsWith(BAD_REQUEST) || message.equals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION);
        assertThat(condition).as("Unexpected error '%s'", message).isTrue();
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        GET_BALANCE("getBalance"),
        DESTROY_CONTRACT("destroyContract"),
        COPY_CODE("copyCode"),
        GET_CODE_SIZE("getCodeSize"),
        GET_CODE_HASH("getCodeHash");

        private final String selector;
    }
}
