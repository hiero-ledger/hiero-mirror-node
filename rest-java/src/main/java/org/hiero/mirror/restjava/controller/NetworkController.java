// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.lang.Long.MAX_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.REGISTERED_NODE_ID;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
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
import org.hiero.mirror.rest.model.RegisteredNode;
import org.hiero.mirror.rest.model.RegisteredNodesResponse;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.common.SupplyType;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkNodeMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.mapper.NetworkSupplyMapper;
import org.hiero.mirror.restjava.mapper.RegisteredNodeMapper;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.hiero.mirror.restjava.service.RegisteredNodeService;
import org.hiero.mirror.restjava.service.fee.FeeEstimationService;
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

    private static final Function<NetworkNode, Map<String, String>> NETWORK_NODE_EXTRACTOR =
            node -> ImmutableSortedMap.of(Constants.NODE_ID, node.getNodeId().toString());

    private static final Function<RegisteredNode, Map<String, String>> REGISTERED_NODE_EXTRACTOR =
            node -> ImmutableSortedMap.of(
                    Constants.REGISTERED_NODE_ID, node.getRegisteredNodeId().toString());

    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeEstimationService feeEstimationService;
    private final FeeScheduleMapper feeScheduleMapper;
    private final FileService fileService;
    private final LinkFactory linkFactory;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkSupplyMapper networkSupplyMapper;
    private final NetworkNodeMapper networkNodeMapper;
    private final RegisteredNodeService registeredNodeService;
    private final RegisteredNodeMapper registeredNodeMapper;

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
        return feeScheduleMapper.map(feeSchedule, exchangeRate, bound, order);
    }

    @PostMapping(
            consumes = {"application/protobuf", "application/x-protobuf"},
            value = "/fees")
    FeeEstimateResponse estimateFees(
            @RequestBody @NotNull byte[] body,
            @RequestParam(defaultValue = "INTRINSIC", required = false) FeeEstimateMode mode) {
        try {
            final var transaction = Transaction.PROTOBUF.parse(Bytes.wrap(body));
            return toResponse(feeEstimationService.estimateFees(transaction, mode));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        }
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
        final var fileId = request.getFileId();
        if (fileId != null && fileId.operator() != RangeOperator.EQ) {
            throw new IllegalArgumentException("Only equality operator is supported for file.id");
        }
        final var networkNodeRows = networkService.getNetworkNodes(request);
        final var limit = request.getEffectiveLimit();

        final var networkNodes = networkNodeMapper.map(networkNodeRows);

        final var sort = Sort.by(request.getOrder(), Constants.NODE_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(networkNodes, pageable, NETWORK_NODE_EXTRACTOR);

        var response = new NetworkNodesResponse();
        response.setNodes(networkNodes);
        response.setLinks(links);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/registered-nodes")
    RegisteredNodesResponse getRegisteredNodes(
            @RequestParam(required = false, defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(required = false, defaultValue = "asc") Sort.Direction order,
            @RequestParam(required = false, defaultValue = "", name = REGISTERED_NODE_ID) @Size(max = 2)
                    NumberRangeParameter[] registeredNodeId,
            @RequestParam(required = false) RegisteredNodeType type) {
        final var request = registeredNodesRequest(registeredNodeId, limit, order, type);
        final var nodesQueryResult = registeredNodeService.getRegisteredNodes(request);
        final var nodes = registeredNodeMapper.map(nodesQueryResult);

        final var sort = Sort.by(order, REGISTERED_NODE_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(nodes, pageable, REGISTERED_NODE_EXTRACTOR);

        final var response = new RegisteredNodesResponse();
        response.setRegisteredNodes(nodes);
        response.setLinks(links);

        return response;
    }

    private RegisteredNodesRequest registeredNodesRequest(
            NumberRangeParameter[] registeredNodeIdRanges, int limit, Sort.Direction order, RegisteredNodeType type) {
        long lowerBound = 0L;
        long upperBound = MAX_VALUE;

        for (final var range : registeredNodeIdRanges) {
            if (range.operator() == RangeOperator.EQ) {
                lowerBound = upperBound = range.value(); // The eq operator must not be mixed with other operators
            } else if (range.hasLowerBound()) {
                lowerBound = Math.max(lowerBound, range.getInclusiveValue());
            } else if (range.hasUpperBound()) {
                upperBound = Math.min(upperBound, range.getInclusiveValue());
            }
        }

        return RegisteredNodesRequest.builder()
                .lowerBound(lowerBound)
                .limit(limit)
                .order(order)
                .type(type)
                .upperBound(upperBound)
                .build();
    }

    private static FeeEstimateResponse toResponse(FeeResult feeResult) {
        return new FeeEstimateResponse()
                .node(new FeeEstimate()
                        .base(feeResult.getNodeBaseFeeTinycents())
                        .extras(toExtras(feeResult.getNodeExtraDetails())))
                .network(new FeeEstimateNetwork()
                        .multiplier(feeResult.getNetworkMultiplier())
                        .subtotal(feeResult.getNetworkTotalTinycents()))
                .service(new FeeEstimate()
                        .base(feeResult.getServiceBaseFeeTinycents())
                        .extras(toExtras(feeResult.getServiceExtraDetails())))
                .total(feeResult.totalTinycents());
    }

    private static List<FeeExtra> toExtras(List<FeeResult.FeeDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        final var extras = new ArrayList<FeeExtra>(details.size());
        for (var detail : details) {
            extras.add(toExtra(detail));
        }
        return extras;
    }

    private static FeeExtra toExtra(FeeResult.FeeDetail detail) {
        return new FeeExtra()
                .charged(detail.charged())
                .count(detail.used())
                .feePerUnit(detail.perUnit())
                .included(detail.included())
                .name(detail.name())
                .subtotal(detail.perUnit() * detail.charged());
    }
}
