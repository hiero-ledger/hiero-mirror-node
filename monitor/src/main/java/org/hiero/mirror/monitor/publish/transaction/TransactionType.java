// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.transaction;

import java.util.function.Supplier;
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
    ACCOUNT_CREATE(AccountCreateTransactionSupplier::new),
    ACCOUNT_DELETE(AccountDeleteTransactionSupplier::new),
    ACCOUNT_UPDATE(AccountUpdateTransactionSupplier::new),
    CONSENSUS_CREATE_TOPIC(ConsensusCreateTopicTransactionSupplier::new),
    CONSENSUS_DELETE_TOPIC(ConsensusDeleteTopicTransactionSupplier::new),
    CONSENSUS_SUBMIT_MESSAGE(ConsensusSubmitMessageTransactionSupplier::new),
    CONSENSUS_UPDATE_TOPIC(ConsensusUpdateTopicTransactionSupplier::new),
    CRYPTO_TRANSFER(CryptoTransferTransactionSupplier::new),
    FREEZE(FreezeTransactionSupplier::new),
    SCHEDULE_CREATE(ScheduleCreateTransactionSupplier::new),
    SCHEDULE_DELETE(ScheduleDeleteTransactionSupplier::new),
    SCHEDULE_SIGN(ScheduleSignTransactionSupplier::new),
    TOKEN_ASSOCIATE(TokenAssociateTransactionSupplier::new),
    TOKEN_BURN(TokenBurnTransactionSupplier::new),
    TOKEN_CREATE(TokenCreateTransactionSupplier::new),
    TOKEN_DELETE(TokenDeleteTransactionSupplier::new),
    TOKEN_DISSOCIATE(TokenDissociateTransactionSupplier::new),
    TOKEN_FREEZE(TokenFreezeTransactionSupplier::new),
    TOKEN_GRANT_KYC(TokenGrantKycTransactionSupplier::new),
    TOKEN_MINT(TokenMintTransactionSupplier::new),
    TOKEN_PAUSE(TokenPauseTransactionSupplier::new),
    TOKEN_REVOKE_KYC(TokenRevokeKycTransactionSupplier::new),
    TOKEN_UNFREEZE(TokenUnfreezeTransactionSupplier::new),
    TOKEN_UNPAUSE(TokenUnpauseTransactionSupplier::new),
    TOKEN_UPDATE(TokenUpdateTransactionSupplier::new),
    TOKEN_WIPE(TokenWipeTransactionSupplier::new);

    private final Supplier<? extends TransactionSupplier<?>> supplier;
}
