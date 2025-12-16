// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ContractCallRequest;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.hiero.mirror.rest.model.TransactionDetail;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.EncoderDecoderFacade;
import org.hiero.mirror.test.e2e.acceptance.client.FileClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.client.NetworkAdapter;
import org.hiero.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.hiero.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

@CustomLog
public abstract class AbstractFeature extends EncoderDecoderFacade {
    private static final Map<ContractResource, DeployedContract> contractIdMap = new ConcurrentHashMap<>();
    protected NetworkTransactionResponse networkTransactionResponse;
    protected ContractId contractId;

    @Autowired
    protected ContractClient contractClient;

    @Autowired
    protected FileClient fileClient;

    @Autowired
    protected MirrorNodeClient mirrorClient;

    @Autowired
    protected NetworkAdapter networkAdapter;

    protected NetworkExchangeRateSetResponse exchangeRates;

    @Autowired
    protected ResourceLoader resourceLoader;

    protected long calculateCreateTokenFee(double usdFee, boolean useCurrentFee) {
        if (exchangeRates == null) {
            throw new RuntimeException("Exchange rates are not initialized.");
        }
        final var fee = useCurrentFee ? exchangeRates.getCurrentRate() : exchangeRates.getNextRate();
        final double hbarPriceInCents = (double) fee.getCentEquivalent() / fee.getHbarEquivalent();
        final int usdInCents = 100;
        // create token requires 1 usd in fees
        // create token with custom fees requires 2 usd in fees
        // usdInCents / hbarPriceInCents = amount of hbars equal to 1 usd. Increment that number with 1 for safety and
        // multiply that number with 10 ^ 8 to convert hbar to tinybar
        return (long) ((usdInCents * usdFee / hbarPriceInCents + 1) * 100000000);
    }

    protected TransactionDetail verifyMirrorTransactionsResponse(MirrorNodeClient mirrorClient, int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    public DeployedContract getContract(ContractResource contractResource) {
        return contractIdMap.computeIfAbsent(contractResource, x -> {
            var resource = resourceLoader.getResource(contractResource.path);
            try (var in = resource.getInputStream()) {
                CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
                var fileId = persistContractBytes(
                        compiledSolidityArtifact.getBytecode().replaceFirst(HEX_PREFIX, ""));
                networkTransactionResponse = contractClient.createContract(
                        contractResource.name,
                        fileId,
                        contractClient
                                .getSdkClient()
                                .getAcceptanceTestProperties()
                                .getFeatureProperties()
                                .getMaxContractFunctionGas(),
                        contractResource.initialBalance == 0
                                ? null
                                : Hbar.fromTinybars(contractResource.initialBalance),
                        null);
                ContractId networkTransactionResponseContractId = verifyCreateContractNetworkResponse();
                return new DeployedContract(fileId, networkTransactionResponseContractId, compiledSolidityArtifact);
            } catch (IOException e) {
                log.warn("Issue creating contract: {}, ex: {}", contractResource, e);
                throw new RuntimeException(e);
            }
        });
    }

    protected FileId persistContractBytes(String contractContents) {
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    protected ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var createdContractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(createdContractId);
        return createdContractId;
    }

    protected ContractCallResponseWrapper callContract(String data, String contractAddress) {
        return callContract("LATEST", data, contractAddress);
    }

    protected ContractCallResponseWrapper callContract(String data, String contractAddress, int actualGas) {
        return callContract("LATEST", data, contractAddress, actualGas);
    }

    protected ContractCallResponseWrapper callContract(String blockNumber, String data, String contractAddress) {
        var contractCallRequest = ModelBuilder.contractCallRequest()
                .block(blockNumber)
                .data(data)
                .from(contractClient.getClientAddress())
                .to(contractAddress);

        return callContract(contractCallRequest);
    }

    protected ContractCallResponseWrapper callContract(
            String blockNumber, String data, String contractAddress, int actualGas) {
        var contractCallRequest = ModelBuilder.contractCallRequest(actualGas)
                .block(blockNumber)
                .data(data)
                .from(contractClient.getClientAddress())
                .to(contractAddress);

        return callContract(contractCallRequest);
    }

    protected ContractCallResponseWrapper callContract(ContractCallRequest contractCallRequest) {
        return ContractCallResponseWrapper.of(mirrorClient.contractsCall(contractCallRequest));
    }

    @SuppressWarnings("rawtypes")
    protected ContractCallResponseWrapper callContract(
            final NodeNameEnum node,
            final String from,
            final ContractResource contractResource,
            final SelectorInterface method,
            final String data,
            final TupleType returnTupleType) {
        return ContractCallResponseWrapper.of(networkAdapter.contractsCall(
                node, false, from, getContract(contractResource), method, data, returnTupleType));
    }

    protected String encodeData(ContractResource resource, SelectorInterface method, Object... args) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        return Strings.encode(function.encodeCallWithArgs(args));
    }

    protected String encodeData(SelectorInterface method, Object... args) {
        return Strings.encode(new Function(method.getSelector()).encodeCallWithArgs(args));
    }

    protected void removeFromContractIdMap(ContractResource key) {
        contractIdMap.remove(key);
    }

    @RequiredArgsConstructor
    @Getter
    public enum ContractResource {
        ESTIMATE_PRECOMPILE(
                "ESTIMATE_PRECOMPILE",
                "classpath:solidity/artifacts/contracts/EstimatePrecompileContract.sol/EstimatePrecompileContract.json",
                0),
        ERC("ERC", "classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json", 0),
        EQUIVALENCE_CALL(
                "EQUIVALENCE_CALL",
                "classpath:solidity/artifacts/contracts/EquivalenceContract.sol/EquivalenceContract.json",
                0),
        EQUIVALENCE_DESTRUCT(
                "EQUIVALENCE_DESTRUCT",
                "classpath:solidity/artifacts/contracts/EquivalenceDestruct.sol/EquivalenceDestruct.json",
                10000),
        PRECOMPILE(
                "PRECOMPILE",
                "classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json",
                0),
        ESTIMATE_GAS(
                "ESTIMATE_GAS",
                "classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json",
                1000000),
        SIMPLE_HOOK(
                "SIMPLE_HOOK",
                "classpath:solidity/artifacts/contracts/SimpleHookContract.sol/SimpleHookContract.json",
                1000000),
        PARENT_CONTRACT("PARENT", "classpath:solidity/artifacts/contracts/Parent.sol/Parent.json", 10000000);

        private final String name;
        private final String path;
        private final int initialBalance;

        @Override
        public String toString() {
            return "ContractResource{" + "path='" + path + '\'' + '}';
        }
    }

    public interface SelectorInterface {
        String getSelector();

        FunctionType getFunctionType();

        enum FunctionType {
            VIEW,
            PAYABLE,
            PURE,
            MUTABLE
        }
    }

    public interface ContractMethodInterface extends SelectorInterface {
        int getActualGas();
    }

    public record DeployedContract(
            FileId fileId, ContractId contractId, CompiledSolidityArtifact compiledSolidityArtifact) {}
}
