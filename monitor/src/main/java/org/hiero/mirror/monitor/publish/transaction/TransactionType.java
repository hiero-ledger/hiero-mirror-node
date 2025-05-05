// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.monitor.publish.transaction.account.AccountCreateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.account.AccountDeleteTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.account.AccountUpdateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.consensus.ConsensusCreateTopicTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.consensus.ConsensusDeleteTopicTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.consensus.ConsensusSubmitMessageTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.consensus.ConsensusUpdateTopicTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.network.FreezeTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.schedule.ScheduleCreateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.schedule.ScheduleDeleteTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.schedule.ScheduleSignTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenAssociateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenBurnTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenCreateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenDeleteTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenDissociateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenFreezeTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenGrantKycTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenMintTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenPauseTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenRevokeKycTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenUnfreezeTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenUnpauseTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenUpdateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenWipeTransactionSupplier;

@Getter
@RequiredArgsConstructor
public enum TransactionType {
    ACCOUNT_CREATE(AccountCreateTransactionSupplier.class),
    ACCOUNT_DELETE(AccountDeleteTransactionSupplier.class),
    ACCOUNT_UPDATE(AccountUpdateTransactionSupplier.class),
    CONSENSUS_CREATE_TOPIC(ConsensusCreateTopicTransactionSupplier.class),
    CONSENSUS_DELETE_TOPIC(ConsensusDeleteTopicTransactionSupplier.class),
    CONSENSUS_SUBMIT_MESSAGE(ConsensusSubmitMessageTransactionSupplier.class),
    CONSENSUS_UPDATE_TOPIC(ConsensusUpdateTopicTransactionSupplier.class),
    CRYPTO_TRANSFER(CryptoTransferTransactionSupplier.class),
    FREEZE(FreezeTransactionSupplier.class),
    SCHEDULE_CREATE(ScheduleCreateTransactionSupplier.class),
    SCHEDULE_DELETE(ScheduleDeleteTransactionSupplier.class),
    SCHEDULE_SIGN(ScheduleSignTransactionSupplier.class),
    TOKEN_ASSOCIATE(TokenAssociateTransactionSupplier.class),
    TOKEN_BURN(TokenBurnTransactionSupplier.class),
    TOKEN_CREATE(TokenCreateTransactionSupplier.class),
    TOKEN_DELETE(TokenDeleteTransactionSupplier.class),
    TOKEN_DISSOCIATE(TokenDissociateTransactionSupplier.class),
    TOKEN_FREEZE(TokenFreezeTransactionSupplier.class),
    TOKEN_GRANT_KYC(TokenGrantKycTransactionSupplier.class),
    TOKEN_MINT(TokenMintTransactionSupplier.class),
    TOKEN_PAUSE(TokenPauseTransactionSupplier.class),
    TOKEN_REVOKE_KYC(TokenRevokeKycTransactionSupplier.class),
    TOKEN_UNFREEZE(TokenUnfreezeTransactionSupplier.class),
    TOKEN_UNPAUSE(TokenUnpauseTransactionSupplier.class),
    TOKEN_UPDATE(TokenUpdateTransactionSupplier.class),
    TOKEN_WIPE(TokenWipeTransactionSupplier.class);

    private final Class<? extends TransactionSupplier<?>> supplier;
}
