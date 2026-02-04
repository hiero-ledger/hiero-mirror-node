// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.FeeEstimate;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.rest.model.FeeEstimateNetwork;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.rest.model.FeeExtra;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RequestParameter;
import org.hiero.mirror.restjava.common.SupplyType;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.CommonMapper;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkNodeMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.mapper.NetworkSupplyMapper;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/api/v1/network", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
final class NetworkController {

    static final FeeEstimateResponse FEE_ESTIMATE_RESPONSE;
    private static final String NODE_ID = "node.id";
    private static final Function<NetworkNode, Map<String, String>> NETWORK_NODE_EXTRACTOR =
            node -> ImmutableSortedMap.of(NODE_ID, node.getNodeId().toString());

    static {
        final var feeExtra = new FeeExtra();
        feeExtra.setCharged(1);
        feeExtra.setCount(2);
        feeExtra.setFeePerUnit(10_000L);
        feeExtra.setIncluded(1);
        feeExtra.setName("Test data");
        feeExtra.setSubtotal(10_000L);

        final var feeEstimate = new FeeEstimate();
        feeEstimate.setBase(100_000L);
        feeEstimate.setExtras(List.of(feeExtra));

        final var network = new FeeEstimateNetwork();
        network.setMultiplier(2);
        network.setSubtotal(220_000L);

        final var feeEstimateResponse = new FeeEstimateResponse();
        feeEstimateResponse.setNotes(List.of("This API is not yet implemented and only returns stubbed test data"));
        feeEstimateResponse.setNetwork(network);
        feeEstimateResponse.setNode(feeEstimate);
        feeEstimateResponse.setService(feeEstimate);
        feeEstimateResponse.setTotal(440_000L);
        FEE_ESTIMATE_RESPONSE = feeEstimateResponse;
    }

    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeScheduleMapper feeScheduleMapper;
    private final FileService fileService;
    private final LinkFactory linkFactory;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkSupplyMapper networkSupplyMapper;
    private final NetworkNodeMapper networkNodeMapper;
    private final CommonMapper commonMapper;

    @GetMapping("/exchangerate")
    NetworkExchangeRateSetResponse getExchangeRate(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var exchangeRateSet = fileService.getExchangeRate(bound);
        return exchangeRateMapper.map(exchangeRateSet);
    }

    @GetMapping("/fees")
    NetworkFeesResponse getFees(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction order) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var feeSchedule = fileService.getFeeSchedule(bound);
        final var exchangeRate = fileService.getExchangeRate(bound);
        return feeScheduleMapper.map(feeSchedule, exchangeRate, order);
    }

    @PostMapping(
            consumes = {"application/protobuf", "application/x-protobuf"},
            value = "/fees")
    FeeEstimateResponse estimateFees(
            @RequestBody Transaction transaction,
            @RequestParam(defaultValue = "INTRINSIC", required = false) FeeEstimateMode mode) {
        try {
            SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse SignedTransaction");
        }

        return FEE_ESTIMATE_RESPONSE;
    }

    @GetMapping("/stake")
    NetworkStakeResponse getNetworkStake() {
        final var networkStake = networkService.getLatestNetworkStake();
        return networkStakeMapper.map(networkStake);
    }

    @GetMapping("/supply")
    ResponseEntity<?> getSupply(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(name = "q", required = false) SupplyType supplyType) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var networkSupply = networkService.getSupply(bound);

        if (supplyType != null) {
            final var valueInTinyCoins =
                    supplyType == SupplyType.TOTALCOINS ? NetworkSupply.TOTAL_SUPPLY : networkSupply.releasedSupply();
            final var formattedValue = networkSupplyMapper.convertToCurrencyFormat(valueInTinyCoins);

            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, UTF_8))
                    .body(formattedValue);
        }

        return ResponseEntity.ok(networkSupplyMapper.map(networkSupply));
    }

    @GetMapping("/nodes")
    ResponseEntity<NetworkNodesResponse> getNodes(@RequestParameter NetworkNodeRequest request) {
        final var networkNodeRows = networkService.getNetworkNodes(request);
        // Use effective limit (capped at MAX_LIMIT) to match rest module behavior
        final var limit = request.getEffectiveLimit();

        // Map database rows to response model
        final var networkNodes =
                networkNodeRows.stream().map(networkNodeMapper::map).toList();

        // Create pagination links using LinkFactory
        // Matches Node.js behavior: generate next link when results.size() == limit (optimistic pagination)
        // No link when results.size() < limit (definitively at the end)
        final var sort = Sort.by(request.getOrder(), NODE_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(networkNodes, pageable, NETWORK_NODE_EXTRACTOR);

        return ResponseEntity.ok(networkNodeMapper.mapToResponse(networkNodes, links));
    }
}
