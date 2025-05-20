// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import com.hedera.services.store.models.Account;

public record CreateParams(int functionId, Account account) implements BodyParams {}
