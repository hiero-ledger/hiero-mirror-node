// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * This service is used to centralize the conversion logic from protobuf-based HAPI entities to its internal EntityId
 * representation. Lookup methods encapsulate caching and alias resolution.
 */
public interface EntityIdService {

    /**
     * Converts a protobuf AccountID to an EntityID, resolving any aliases that may be present.
     *
     * @param accountId The protobuf account ID
     * @return An optional of the converted EntityId if it can be resolved, or EntityId.EMPTY if none can be resolved.
     */
    Optional<EntityId> lookup(AccountID accountId);

    /**
     * Specialized form of lookup(AccountID) that returns the first account ID parameter that resolves to a non-empty
     * EntityId.
     *
     * @param accountIds The protobuf account IDs
     * @return An optional of the converted EntityId if it can be resolved, or EntityId.EMPTY if none can be resolved.
     */
    Optional<EntityId> lookup(AccountID... accountIds);

    /**
     * Converts a protobuf ContractID to an EntityID, resolving any EVM addresses that may be present.
     *
     * @param contractId The protobuf contract ID
     * @return An optional of the converted EntityId if it can be resolved, or EntityId.EMPTY if none can be resolved.
     */
    Optional<EntityId> lookup(ContractID contractId);

    /**
     * Converts a protobuf ContractID to an EntityID, resolving any EVM addresses that may be present.
     *
     * @param contractId            The protobuf contract ID
     * @param throwRecoverableError If true, will throw a recoverable error if an EVM address cannot be found.
     * @return An optional of the converted EntityId if it can be resolved, or EntityId.EMPTY if none can be resolved.
     */
    Optional<EntityId> lookup(ContractID contractId, boolean throwRecoverableError);

    /**
     * Specialized form of lookup(ContractID) that returns the first contract ID parameter that resolves to a non-empty
     * EntityId.
     *
     * @param contractIds The protobuf contract IDs
     * @return An optional of the converted EntityId if it can be resolved, or EntityId.EMPTY if none can be resolved.
     */
    Optional<EntityId> lookup(ContractID... contractIds);

    /**
     * Used to notify the system of new aliases / evm addresses for potential use in future lookups.
     *
     * @param entity The entity which may have alias or evm address
     */
    void notify(Entity entity);
}
