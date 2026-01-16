// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.controller;

import static org.hiero.mirror.graphql.util.GraphQlUtils.convertCurrency;
import static org.hiero.mirror.graphql.util.GraphQlUtils.toEntityId;
import static org.hiero.mirror.graphql.util.GraphQlUtils.validateOneOf;

import jakarta.validation.Valid;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.graphql.mapper.AccountMapper;
import org.hiero.mirror.graphql.service.EntityService;
import org.hiero.mirror.graphql.viewmodel.Account;
import org.hiero.mirror.graphql.viewmodel.AccountInput;
import org.hiero.mirror.graphql.viewmodel.HbarUnit;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@CustomLog
@RequiredArgsConstructor
class AccountController {

    private final AccountMapper accountMapper;
    private final EntityService entityService;

    @QueryMapping
    Optional<Account> account(@Argument @Valid AccountInput input) {
        final var alias = input.getAlias();
        final var evmAddress = input.getEvmAddress();
        final var entityId = input.getEntityId();
        final var id = input.getId();

        validateOneOf(alias, entityId, evmAddress, id);

        if (entityId != null) {
            return entityService
                    .getByIdAndType(toEntityId(entityId), EntityType.ACCOUNT)
                    .map(accountMapper::map);
        }

        if (alias != null) {
            return entityService.getByAliasAndType(alias, EntityType.ACCOUNT).map(accountMapper::map);
        }

        if (evmAddress != null) {
            return entityService
                    .getByEvmAddressAndType(evmAddress, EntityType.ACCOUNT)
                    .map(accountMapper::map);
        }

        throw new IllegalStateException("Not implemented");
    }

    @SchemaMapping
    Long balance(@Argument @Valid HbarUnit unit, Account account) {
        return convertCurrency(unit, account.getBalance());
    }
}
