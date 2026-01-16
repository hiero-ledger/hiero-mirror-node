// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import {proto} from '@hashgraph/proto';

import EntityId from '../entityId';
import {TransactionId} from '../model/index';
import TransactionIdViewModel from './transactionIdViewModel';
import {encodeBase64, encodeBinary, nsToSecNs} from '../utils';

/**
 * Topic message view model
 */
class TopicMessageViewModel {
  // Blockstreams no longer contain runningHashVersion, default to the latest version
  static DEFAULT_RUNNING_HASH_VERSION = 3;

  /**
   * Constructs topicMessage view model
   *
   * @param {TopicMessage} topicMessage
   * @param {String} messageEncoding the encoding to display the message in
   */
  constructor(topicMessage, messageEncoding) {
    this.chunk_info = _.isNil(topicMessage.chunkNum) ? null : new ChunkInfoViewModel(topicMessage);
    this.consensus_timestamp = nsToSecNs(topicMessage.consensusTimestamp);
    this.message = encodeBinary(topicMessage.message, messageEncoding);
    this.payer_account_id = EntityId.parse(topicMessage.payerAccountId).toString();
    this.running_hash = encodeBase64(topicMessage.runningHash);
    this.running_hash_version = topicMessage.runningHashVersion ?? TopicMessageViewModel.DEFAULT_RUNNING_HASH_VERSION;
    this.sequence_number = topicMessage.sequenceNumber;
    this.topic_id = EntityId.parse(topicMessage.topicId).toString();
  }
}

class ChunkInfoViewModel {
  constructor(topicMessage) {
    let initialTransactionId;
    if (!_.isNil(topicMessage.initialTransactionId)) {
      initialTransactionId = proto.TransactionID.decode(topicMessage.initialTransactionId);
    } else {
      initialTransactionId = new TransactionId(
        topicMessage.payerAccountId,
        topicMessage.validStartTimestamp,
        null,
        null
      );
    }
    this.initial_transaction_id = new TransactionIdViewModel(initialTransactionId);
    this.number = topicMessage.chunkNum;
    this.total = topicMessage.chunkTotal;
  }
}

export default TopicMessageViewModel;
