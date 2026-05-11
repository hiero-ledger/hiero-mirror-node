// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
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
import org.hiero.mirror.restjava.mapper.NetworkNodeMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.mapper.NetworkSupplyMapper;
import org.hiero.mirror.restjava.mapper.RegisteredNodeMapper;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final FileService fileService;
    private final LinkFactory linkFactory;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkSupplyMapper networkSupplyMapper;
    private final NetworkNodeMapper networkNodeMapper;
    private final RegisteredNodeMapper registeredNodeMapper;

    @GetMapping("/exchangerate")
    NetworkExchangeRateSetResponse getExchangeRate(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var exchangeRateSet = fileService.getExchangeRate(bound);
        return exchangeRateMapper.map(exchangeRateSet);
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
    RegisteredNodesResponse getRegisteredNodes(@RequestParameter RegisteredNodesRequest request) {
        final var registeredNodes = networkService.getRegisteredNodes(request);
        final var registeredNodeDtos = registeredNodeMapper.map(registeredNodes);

        final var sort = Sort.by(request.getOrder(), Constants.REGISTERED_NODE_ID);
        final var pageable = PageRequest.of(0, request.getLimit(), sort);
        final var links = linkFactory.create(registeredNodeDtos, pageable, REGISTERED_NODE_EXTRACTOR);

        final var response = new RegisteredNodesResponse();
        response.setRegisteredNodes(registeredNodeDtos);
        response.setLinks(links);

        return response;
    }
}
