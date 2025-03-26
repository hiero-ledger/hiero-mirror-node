// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.client.NetworkAdapter.BIG_INTEGER_TUPLE;
import static com.hedera.mirror.test.e2e.acceptance.client.NetworkAdapter.BYTES_TUPLE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.COPY_CODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.DESTROY_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_HASH;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_SIZE;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@CustomLog
@RequiredArgsConstructor
public class EquivalenceFeature extends AbstractFeature {

    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String BAD_REQUEST = "400 ";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";
    private static final String ACCOUNT_ID_FORMAT = "%s.%s.%s";

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final CommonProperties commonProperties;
    private DeployedContract equivalenceDestructContract;
    private DeployedContract equivalenceCallContract;

    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;

    public static long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
    }

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() {
        equivalenceDestructContract = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                equivalenceDestructContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() {
        equivalenceCallContract = getContract(EQUIVALENCE_CALL);
        equivalenceCallContractSolidityAddress =
                equivalenceCallContract.contractId().toSolidityAddress();
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

    @Then("I execute selfdestruct and set beneficiary to {string} num")
    public void selfDestructAndSetBeneficiary(String num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var beneficiary = String.format(ACCOUNT_ID_FORMAT, commonProperties.getShard(), commonProperties.getRealm(), num);
        var accountId = AccountId.fromString(beneficiary);

        var data = encodeData(EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, asAddress(accountId));
        var functionResult = callContract(
                nodeType, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);

        // The contract is removed from the map when executed against the consensus node to prevent potential issues
        // in subsequent runs. This is because if the contract were not removed, the system would seek the cached
        // address on the next execution.
        if (acceptanceTestProperties.getNodeType().equals(NodeNameEnum.CONSENSUS)) {
            removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        }

        final var message = functionResult.getResultAsText();

        if (extractAccountNumber(beneficiary) < 751) {
            var condition = message.startsWith(BAD_REQUEST) || message.equals(INVALID_SOLIDITY_ADDRESS_EXCEPTION);
            assertThat(condition).as("Unexpected error '%s'", message).isTrue();
        } else {
            var condition = functionResult.getResult().equals("0x") || message.equals(TRANSACTION_SUCCESSFUL_MESSAGE);
            assertThat(condition).as("Unexpected error '%s'", message).isTrue();
        }
    }

    @Then("I execute balance opcode to system account {string} num would return 0")
    public void balanceOfAddress(String num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var address = String.format(ACCOUNT_ID_FORMAT, commonProperties.getShard(), commonProperties.getRealm(), num);
        final var accountId = AccountId.fromString(address);
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, asAddress(accountId));
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

    @Then("I verify extcodesize opcode against a system account {string} num returns 0")
    public void extCodeSizeAgainstSystemAccount(String num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var address = String.format(ACCOUNT_ID_FORMAT, commonProperties.getShard(), commonProperties.getRealm(), num);
        final var accountId = AccountId.fromString(address);
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_SIZE, asAddress(accountId));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_SIZE, data, BIG_INTEGER_TUPLE);
        assertThat(functionResult.getResultAsNumber()).isEqualTo(BigInteger.ZERO);
    }

    @Then("I verify extcodecopy opcode against a system account {string} num returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var address = String.format(ACCOUNT_ID_FORMAT, commonProperties.getShard(), commonProperties.getRealm(), num);
        final var accountId = AccountId.fromString(address);
        var data = encodeData(EQUIVALENCE_CALL, COPY_CODE, asAddress(accountId));
        var functionResult = callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, COPY_CODE, data, BYTES_TUPLE);
        assertThat(functionResult.getResultAsText()).isEmpty();
    }

    @Then("I verify extcodehash opcode against a system account {string} num returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String num) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var address = String.format(ACCOUNT_ID_FORMAT, commonProperties.getShard(), commonProperties.getRealm(), num);
        final var accountId = AccountId.fromString(address);
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_HASH, asAddress(accountId));
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
