// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.repository.TokenAirdropRepository;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TokenAirdropServiceImpl implements TokenAirdropService {

    private final EntityService entityService;
    private final TokenAirdropRepository repository;

    public Collection<TokenAirdrop> getAirdrops(TokenAirdropRequest request) {
        var id = entityService.lookup(request.getAccountId());
        return repository.findAll(request, id);
    }
}
