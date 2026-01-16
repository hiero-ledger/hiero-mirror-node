// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;

public interface TokenAirdropService {

    Collection<TokenAirdrop> getAirdrops(TokenAirdropRequest request);
}
