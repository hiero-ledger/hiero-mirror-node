// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;
import org.hiero.mirror.restjava.repository.TokenAirdropRepository;

@Named
@RequiredArgsConstructor
final class TokenAirdropServiceImpl implements TokenAirdropService {

    private final EntityService entityService;
    private final TokenAirdropRepository repository;

    public Collection<TokenAirdrop> getAirdrops(TokenAirdropRequest request) {
        var id = entityService.lookup(request.getAccountId());
        return repository.findAll(request, id);
    }
}
