// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import java.util.Collection;

public interface TokenAirdropService {

    Collection<TokenAirdrop> getAirdrops(TokenAirdropRequest request);
}
