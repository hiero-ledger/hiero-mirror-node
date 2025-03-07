// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.controller;

import static com.hedera.mirror.restjava.common.Constants.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.MAX_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;
import static com.hedera.mirror.restjava.jooq.domain.Tables.NFT_ALLOWANCE;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.Bound;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.function.Function;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/accounts/{id}/allowances")
@RequiredArgsConstructor
@RestController
public class AllowancesController {

    private static final Map<Boolean, Function<NftAllowance, Map<String, String>>> EXTRACTORS = Map.of(
            true,
            nftAllowance -> ImmutableSortedMap.of(
                    ACCOUNT_ID, nftAllowance.getSpender(),
                    TOKEN_ID, nftAllowance.getTokenId()),
            false,
            nftAllowance -> ImmutableSortedMap.of(
                    ACCOUNT_ID, nftAllowance.getOwner(),
                    TOKEN_ID, nftAllowance.getTokenId()));

    private final LinkFactory linkFactory;
    private final NftAllowanceService service;
    private final NftAllowanceMapper nftAllowanceMapper;

    @GetMapping(value = "/nfts")
    NftAllowancesResponse getNftAllowances(
            @PathVariable EntityIdParameter id,
            @RequestParam(name = ACCOUNT_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] accountIds,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(defaultValue = "true") boolean owner,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) EntityIdRangeParameter[] tokenIds) {
        var field = owner ? NFT_ALLOWANCE.SPENDER : NFT_ALLOWANCE.OWNER;
        var request = NftAllowanceRequest.builder()
                .accountId(id)
                .isOwner(owner)
                .limit(limit)
                .order(order)
                .ownerOrSpenderIds(new Bound(accountIds, true, ACCOUNT_ID, field))
                .tokenIds(new Bound(tokenIds, false, TOKEN_ID, NFT_ALLOWANCE.TOKEN_ID))
                .build();

        var serviceResponse = service.getNftAllowances(request);
        var allowances = nftAllowanceMapper.map(serviceResponse);

        var sort = Sort.by(order, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, limit, sort);
        var links = linkFactory.create(allowances, pageable, EXTRACTORS.get(owner));

        return new NftAllowancesResponse().allowances(allowances).links(links);
    }
}
