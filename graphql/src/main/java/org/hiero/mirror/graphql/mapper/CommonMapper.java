// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.mapper;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.graphql.viewmodel.EntityId;
import org.hiero.mirror.graphql.viewmodel.TimestampRange;
import org.mapstruct.Mapper;
import org.mapstruct.MappingInheritanceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

@Mapper(mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface CommonMapper {

    Logger logger = LoggerFactory.getLogger(CommonMapper.class);
    String CONTRACT_ID = "CONTRACT_ID";
    String KEYS = "keys";
    String THRESHOLD = "threshold";

    default EntityId mapContractId(ContractID contractID) {
        if (contractID == null || ContractID.getDefaultInstance().equals(contractID)) {
            return null;
        }

        var entityId = new EntityId();
        entityId.setShard(contractID.getShardNum());
        entityId.setRealm(contractID.getRealmNum());
        entityId.setNum(contractID.getContractNum());
        return entityId;
    }

    default Duration mapDuration(Long source) {
        return source != null ? Duration.ofSeconds(source) : null;
    }

    default EntityId mapEntityId(Long source) {
        if (source == null) {
            return null;
        }

        var eid = org.hiero.mirror.common.domain.entity.EntityId.of(source);
        return mapEntityId(eid);
    }

    default EntityId mapEntityId(org.hiero.mirror.common.domain.entity.EntityId source) {
        if (source == null) {
            return null;
        }

        var viewModel = new EntityId();
        viewModel.setShard(source.getShard());
        viewModel.setRealm(source.getRealm());
        viewModel.setNum(source.getNum());
        return viewModel;
    }

    default Instant mapInstant(Long source) {
        return source != null ? Instant.ofEpochSecond(0L, source) : null;
    }

    default Object mapKey(byte[] source) {
        if (source == null) {
            return null;
        }

        if (ArrayUtils.isEmpty(source)) {
            return Collections.emptyMap();
        }

        try {
            var key = Key.parseFrom(source);
            return mapKey(key);
        } catch (Exception e) {
            logger.error("Unable to map protobuf Key to map", e);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    default Object mapKey(Key key) {
        var keyCase = key.getKeyCase();
        return switch (keyCase) {
            case CONTRACTID -> Map.of(CONTRACT_ID, mapContractId(key.getContractID()));
            case DELEGATABLE_CONTRACT_ID -> Map.of(keyCase.toString(), mapContractId(key.getDelegatableContractId()));
            case ECDSA_384 -> Map.of(keyCase.toString(), encodeBase64String(toBytes(key.getECDSA384())));
            case ECDSA_SECP256K1 -> Map.of(keyCase.toString(), encodeBase64String(toBytes(key.getECDSASecp256K1())));
            case ED25519 -> Map.of(keyCase.toString(), encodeBase64String(toBytes(key.getEd25519())));
            case KEYLIST -> Map.of(KEYS, mapKeyList(key.getKeyList()));
            case RSA_3072 -> Map.of(keyCase.toString(), encodeBase64String(toBytes(key.getRSA3072())));
            case THRESHOLDKEY ->
                Map.of(
                        THRESHOLD, key.getThresholdKey().getThreshold(),
                        KEYS, mapKeyList(key.getThresholdKey().getKeys()));
            default -> null;
        };
    }

    default List<Object> mapKeyList(KeyList keyList) {
        var keys = keyList.getKeysList();
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }

        var target = new ArrayList<>(keys.size());

        for (var key : keys) {
            target.add(mapKey(key));
        }

        return target;
    }

    default TimestampRange mapRange(Range<Long> source) {
        if (source == null) {
            return null;
        }

        var target = new TimestampRange();
        if (source.hasLowerBound()) {
            target.setFrom(mapInstant(source.lowerEndpoint()));
        }

        if (source.hasUpperBound()) {
            target.setTo(mapInstant(source.upperEndpoint()));
        }

        return target;
    }
}
