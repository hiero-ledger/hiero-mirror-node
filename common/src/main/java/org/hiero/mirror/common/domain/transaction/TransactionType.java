// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityOperation;

@Getter
@RequiredArgsConstructor
public enum TransactionType {
    UNKNOWN(-1, EntityOperation.NONE),
    CONTRACTCALL(7, EntityOperation.NONE),
    CONTRACTCREATEINSTANCE(8, EntityOperation.CREATE),
    CONTRACTUPDATEINSTANCE(9, EntityOperation.UPDATE),
    CRYPTOADDLIVEHASH(10, EntityOperation.NONE),
    CRYPTOCREATEACCOUNT(11, EntityOperation.CREATE),
    CRYPTODELETE(12, EntityOperation.DELETE),
    CRYPTODELETELIVEHASH(13, EntityOperation.NONE),
    CRYPTOTRANSFER(14, EntityOperation.NONE),
    CRYPTOUPDATEACCOUNT(15, EntityOperation.UPDATE),
    FILEAPPEND(16, EntityOperation.NONE),
    FILECREATE(17, EntityOperation.CREATE),
    FILEDELETE(18, EntityOperation.DELETE),
    FILEUPDATE(19, EntityOperation.UPDATE),
    SYSTEMDELETE(20, EntityOperation.DELETE),
    SYSTEMUNDELETE(21, EntityOperation.UPDATE),
    CONTRACTDELETEINSTANCE(22, EntityOperation.DELETE),
    FREEZE(23, EntityOperation.NONE),
    CONSENSUSCREATETOPIC(24, EntityOperation.CREATE),
    CONSENSUSUPDATETOPIC(25, EntityOperation.UPDATE),
    CONSENSUSDELETETOPIC(26, EntityOperation.DELETE),
    CONSENSUSSUBMITMESSAGE(27, EntityOperation.NONE),
    UNCHECKEDSUBMIT(28, EntityOperation.NONE),
    TOKENCREATION(29, EntityOperation.CREATE),
    TOKENFREEZE(31, EntityOperation.NONE),
    TOKENUNFREEZE(32, EntityOperation.NONE),
    TOKENGRANTKYC(33, EntityOperation.NONE),
    TOKENREVOKEKYC(34, EntityOperation.NONE),
    TOKENDELETION(35, EntityOperation.DELETE),
    TOKENUPDATE(36, EntityOperation.UPDATE),
    TOKENMINT(37, EntityOperation.NONE),
    TOKENBURN(38, EntityOperation.NONE),
    TOKENWIPE(39, EntityOperation.NONE),
    TOKENASSOCIATE(40, EntityOperation.NONE),
    TOKENDISSOCIATE(41, EntityOperation.NONE),
    SCHEDULECREATE(42, EntityOperation.CREATE),
    SCHEDULEDELETE(43, EntityOperation.DELETE),
    SCHEDULESIGN(44, EntityOperation.NONE),
    TOKENFEESCHEDULEUPDATE(45, EntityOperation.NONE),
    TOKENPAUSE(46, EntityOperation.NONE),
    TOKENUNPAUSE(47, EntityOperation.NONE),
    CRYPTOAPPROVEALLOWANCE(48, EntityOperation.NONE),
    CRYPTODELETEALLOWANCE(49, EntityOperation.NONE),
    ETHEREUMTRANSACTION(50, EntityOperation.NONE),
    NODESTAKEUPDATE(51, EntityOperation.NONE),
    UTILPRNG(52, EntityOperation.NONE),
    TOKENUPDATENFTS(53, EntityOperation.NONE),
    NODECREATE(54, EntityOperation.NONE),
    NODEUPDATE(55, EntityOperation.NONE),
    NODEDELETE(56, EntityOperation.NONE),
    TOKENREJECT(57, EntityOperation.NONE),
    TOKENAIRDROP(58, EntityOperation.NONE),
    TOKENCANCELAIRDROP(59, EntityOperation.NONE),
    TOKENCLAIMAIRDROP(60, EntityOperation.NONE),
    ATOMIC_BATCH(74, EntityOperation.NONE),
    HOOKSTORE(75, EntityOperation.NONE),
    LEDGERIDPUBLICATION(77, EntityOperation.NONE),
    REGISTEREDNODECREATE(101, EntityOperation.CREATE),
    REGISTEREDNODEUPDATE(102, EntityOperation.UPDATE),
    REGISTEREDNODEDELETE(103, EntityOperation.DELETE);

    private static final Map<Integer, TransactionType> idMap =
            Arrays.stream(values()).collect(Collectors.toMap(TransactionType::getProtoId, Function.identity()));

    private final int protoId;
    private final EntityOperation entityOperation;

    public static TransactionType of(int protoId) {
        return idMap.getOrDefault(protoId, UNKNOWN);
    }
}
