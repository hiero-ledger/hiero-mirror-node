// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import pgformat from 'pg-format';
import {Range} from 'pg-range';
import {proto} from '@hiero-ledger/proto';

import base32 from '../base32';
import {getMirrorConfig} from '../config';
import * as constants from '../constants';
import EntityId from '../entityId';
import {valueToBuffer} from './testutils';
import {JSONStringify} from '../utils';
import {encodedIdFromSpecValue} from './integrationUtils';

const config = getMirrorConfig();
const NETWORK_FEE = 1n;
const NODE_FEE = 2n;
const SERVICE_FEE = 4n;
const DEFAULT_FEE_COLLECTOR_NUM = EntityId.systemEntity.networkAdminFeeAccount.num;
const DEFAULT_NODE_ID = 3;
const DEFAULT_PAYER_ACCOUNT_ID = 102;
const DEFAULT_SENDER_ID = 101;

const defaultFileData = '\\x97c1fc0a6ed5551bc831571325e9bdb365d06803100dc20648640ba24ce69750';

const setup = async (testDataJson) => {
  await loadAccounts(testDataJson.accounts);
  await loadAddressBooks(testDataJson.addressbooks);
  await loadAddressBookEntries(testDataJson.addressbookentries);
  await loadAddressBookServiceEndpoints(testDataJson.addressbookserviceendpoints);
  await loadAssessedCustomFees(testDataJson.assessedcustomfees);
  await loadBalances(testDataJson.balances);
  await loadCryptoTransfers(testDataJson.cryptotransfers);
  await loadContracts(testDataJson.contracts);
  await loadContractActions(testDataJson.contractactions);
  await loadContractLogs(testDataJson.contractlogs);
  await loadContractResults(testDataJson.contractresults);
  await loadContractStateChanges(testDataJson.contractStateChanges);
  await loadContractTransactions(
    testDataJson.contractlogs,
    testDataJson.contractresults,
    testDataJson.contractStateChanges
  );
  await loadCryptoAllowances(testDataJson.cryptoAllowances);
  await loadCustomFees(testDataJson.customfees);
  await loadEntities(testDataJson.entities);
  await loadEntityStakes(testDataJson.entityStakes);
  await loadEthereumTransactions(testDataJson.ethereumtransactions);
  await loadFileData(testDataJson.filedata);
  await loadNfts(testDataJson.nfts);
  await loadNodes(testDataJson.nodes);
  await loadNodeStakes(testDataJson.nodestakes);
  await loadRecordFiles(testDataJson.recordFiles);
  await loadSchedules(testDataJson.schedules);
  await loadStakingRewardTransfers(testDataJson.stakingRewardTransfers);
  await loadTopicMessages(testDataJson.topicmessages);
  await loadTopicMessageLookups(testDataJson.topicmessagelookups);
  await loadTokens(testDataJson.tokens);
  await loadTokenAccounts(testDataJson.tokenaccounts);
  await loadTokenAllowances(testDataJson.tokenAllowances);
  await loadTokenBalances(testDataJson.tokenBalance);
  await loadTransactions(testDataJson.transactions);
  await loadTransactionHashes(testDataJson.transactionhashes);
  await loadTransactionSignatures(testDataJson.transactionsignatures);
  await loadContractStates(testDataJson.contractStates);

  await generateTopicMessageLookups();
};

const generateTopicMessageLookups = async () => {
  if (!config.rest.query.topicMessageLookup) {
    return;
  }

  const {rows} = await ownerPool.query('select exists(select * from topic_message_lookup)');
  if (rows[0].exists) {
    // Don't generate if the test case has already created topic message lookup entries
    return;
  }

  const {rows: topicMessages} = await ownerPool.query(`select consensus_timestamp, sequence_number, topic_id
                                                       from topic_message
                                                       order by topic_id, sequence_number`);

  let topicMessageLookup = null;
  const topicMessageLookups = [];
  for (const topicMessage of topicMessages) {
    const {topic_id: topicId, sequence_number: sequenceNumber, consensus_timestamp: consensusTimestamp} = topicMessage;
    const date = new Date(Number(BigInt(consensusTimestamp) / constants.NANOSECONDS_PER_MILLISECOND));
    const partition = `topic_message_p${date.getUTCFullYear()}_${(date.getUTCMonth() + 1).toString().padStart(2, '0')}`;

    if (topicMessageLookup?.topic_id !== topicId || topicMessageLookup?.partition !== partition) {
      // Add it regardless if it's null, later we will filter out nulls
      topicMessageLookups.push(topicMessageLookup);
      topicMessageLookup = {
        partition,
        sequence_number_range: Range(sequenceNumber, sequenceNumber), // default is inclusive bounds '[]'
        timestamp_range: Range(consensusTimestamp, consensusTimestamp),
        topic_id: topicId,
      };
    } else {
      // extend the ranges
      topicMessageLookup.sequence_number_range.end = sequenceNumber;
      topicMessageLookup.timestamp_range.end = consensusTimestamp;
    }
  }

  topicMessageLookups.push(topicMessageLookup);

  for (const topicMessageLookup of topicMessageLookups) {
    if (topicMessageLookup === null) {
      continue;
    }

    await addTopicMessageLookup(topicMessageLookup);
  }
};

const loadAccounts = async (accounts) => {
  if (accounts == null) {
    return;
  }

  for (const account of accounts) {
    await addAccount(account);
  }
};

const loadAddressBooks = async (addressBooks) => {
  if (addressBooks == null) {
    return;
  }

  for (const addressBook of addressBooks) {
    await addAddressBook(addressBook);
  }
};

const loadAddressBookEntries = async (entries) => {
  if (entries == null) {
    return;
  }

  for (const addressBookEntry of entries) {
    await addAddressBookEntry(addressBookEntry);
  }
};

const loadAddressBookServiceEndpoints = async (endpoints) => {
  if (endpoints == null) {
    return;
  }

  for (const endpoint of endpoints) {
    await addAddressBookServiceEndpoint(endpoint);
  }
};

const loadAssessedCustomFees = async (assessedCustomFees) => {
  if (assessedCustomFees == null) {
    return;
  }

  for (const assessedCustomFee of assessedCustomFees) {
    await addAssessedCustomFee(assessedCustomFee);
  }
};

const loadBalances = async (balances) => {
  if (balances == null) {
    return;
  }

  for (const balance of balances) {
    await setAccountBalance(balance);
  }
};

const loadContracts = async (contracts) => {
  if (contracts == null) {
    return;
  }

  for (const contract of contracts) {
    await addContract(contract);
  }
};

const loadContractActions = async (contractActions) => {
  if (contractActions == null) {
    return;
  }

  for (const contractAction of contractActions) {
    await addContractAction(contractAction);
  }
};

const loadContractStates = async (contractStates) => {
  if (contractStates == null) {
    return;
  }

  for (const contractState of contractStates) {
    await addContractState(contractState);
  }
};

const loadContractResults = async (contractResults) => {
  if (contractResults == null) {
    return;
  }

  for (const contractResult of contractResults) {
    await addContractResult(contractResult);
  }
};

const loadContractLogs = async (contractLogs) => {
  if (contractLogs == null) {
    return;
  }

  for (const contractLog of contractLogs) {
    await addContractLog(contractLog);
  }
};

const loadContractStateChanges = async (contractStateChanges) => {
  if (contractStateChanges == null) {
    return;
  }

  for (const contractStateChange of contractStateChanges) {
    await addContractStateChange(contractStateChange);
  }
};

const loadContractTransactions = async (contractLogs, contractResults, contractStateChanges) => {
  const mergedLogs = (contractLogs || []).map((log) => {
    return {...contractLogDefaults, ...log};
  });
  const mergedResults = (contractResults || []).map((result) => {
    return {...contractResultDefaults, ...result};
  });
  const mergedStateChanges = (contractStateChanges || []).map((stateChange) => {
    return {...defaultContractStateChange, ...stateChange};
  });
  const consensusToDetailsMapping = mergedResults
    .concat(mergedLogs)
    .concat(mergedStateChanges)
    .reduce((map, obj) => {
      const key = obj.consensus_timestamp;
      let details = map[key];

      const payer = encodedIdFromSpecValue(obj.payer_account_id);
      const contractId = encodedIdFromSpecValue(obj.contract_id);
      if (!details) {
        details = {
          payerAccountId: payer,
          ids: [],
        };
        map[key] = details;
      }
      if (contractId !== null && contractId !== undefined && details.ids.indexOf(contractId) === -1) {
        details.ids.push(contractId);
      }
      if (payer != null && payer !== undefined && details.ids.indexOf(payer) === -1) {
        details.ids.push(payer);
        details.payerAccountId = details.payerAccountId || payer;
      }
      return map;
    }, {});

  for (const key in consensusToDetailsMapping) {
    const details = consensusToDetailsMapping[key];
    for (const id of details.ids) {
      await addContractTransaction({
        consensus_timestamp: key,
        entity_id: id,
        payer_account_id: details.payerAccountId,
        contract_ids: `{${details.ids.join(',')}}`,
      });
    }
  }
};
const loadCryptoAllowances = async (cryptoAllowances) => {
  if (cryptoAllowances == null) {
    return;
  }

  for (const cryptoAllowance of cryptoAllowances) {
    await addCryptoAllowance(cryptoAllowance);
  }
};

const loadCryptoTransfers = async (cryptoTransfers) => {
  if (cryptoTransfers == null) {
    return;
  }

  for (const cryptoTransfer of cryptoTransfers) {
    await addCryptoTransaction(cryptoTransfer);
  }
};

const loadCustomFees = async (customFees) => {
  if (customFees == null) {
    return;
  }

  for (const customFee of customFees) {
    await addCustomFee(customFee);
  }
};

const loadEntities = async (entities) => {
  if (entities == null) {
    return;
  }

  for (const entity of entities) {
    await addEntity({}, entity);
  }
};

const loadEntityStakes = async (entityStakes) => {
  if (entityStakes == null) {
    return;
  }

  for (const entityStake of entityStakes) {
    await addEntityStake(entityStake);
  }
};

const loadEthereumTransactions = async (ethereumTransactions) => {
  if (ethereumTransactions == null) {
    return;
  }

  for (const ethereumTransaction of ethereumTransactions) {
    await addEthereumTransaction(ethereumTransaction);
  }
};

const loadFileData = async (fileData) => {
  if (fileData == null) {
    return;
  }

  for (const data of fileData) {
    await addFileData(data);
  }
};

const loadNfts = async (nfts) => {
  if (nfts == null) {
    return;
  }

  for (const nft of nfts) {
    await addNft(nft);
  }
};

const loadNodes = async (nodes) => {
  if (nodes == null) {
    return;
  }

  for (const node of nodes) {
    await addNode(node);
  }
};

const loadNodeStakes = async (nodeStakes) => {
  if (nodeStakes == null) {
    return;
  }

  for (const nodeStake of nodeStakes) {
    await addNodeStake(nodeStake);
  }
};

const loadRecordFiles = async (recordFiles) => {
  if (recordFiles == null) {
    return;
  }
  for (const recordFile of recordFiles) {
    await addRecordFile(recordFile);
  }
};

const loadSchedules = async (schedules) => {
  if (schedules == null) {
    return;
  }

  for (const schedule of schedules) {
    await addSchedule(schedule);
  }
};

const loadStakingRewardTransfers = async (transfers) => {
  if (transfers == null) {
    return;
  }
  for (const transfer of transfers) {
    await addStakingRewardTransfer(transfer);
  }
};

const loadTransactionSignatures = async (transactionSignatures) => {
  if (transactionSignatures == null) {
    return;
  }

  for (const transactionSignature of transactionSignatures) {
    await addTransactionSignature(transactionSignature);
  }
};

const loadTokenAccounts = async (tokenAccounts) => {
  if (tokenAccounts == null) {
    return;
  }

  for (const tokenAccount of tokenAccounts) {
    await addTokenAccount(tokenAccount);
  }
};

const loadTokenAllowances = async (tokenAllowances) => {
  if (tokenAllowances == null) {
    return;
  }

  for (const tokenAllowance of tokenAllowances) {
    await addTokenAllowance(tokenAllowance);
  }
};

const loadTokenBalances = async (tokenBalances) => {
  if (tokenBalances == null) {
    return;
  }

  for (const tokenBalance of tokenBalances) {
    await addTokenBalance(tokenBalance);
  }
};

const loadTokens = async (tokens) => {
  if (tokens == null) {
    return;
  }

  for (const token of tokens) {
    await addToken(token);
  }
};

const loadTransactions = async (transactions) => {
  if (transactions == null) {
    return;
  }

  for (const transaction of transactions) {
    await addTransaction(transaction);
  }
};

const loadTransactionHashes = async (transactionHashes) => {
  if (transactionHashes == null) {
    return;
  }

  for (const transactionHash of transactionHashes) {
    await addTransactionHash(transactionHash);
  }
};

const loadTopicMessages = async (messages) => {
  if (messages == null) {
    return;
  }

  for (const message of messages) {
    await addTopicMessage(message);
  }
};

const loadTopicMessageLookups = async (topicMessageLookups) => {
  if (topicMessageLookups == null) {
    return;
  }

  for (const topicMessageLookup of topicMessageLookups) {
    topicMessageLookup.topic_id = encodedIdFromSpecValue(topicMessageLookup.topic_id);
    await addTopicMessageLookup(topicMessageLookup);
  }
};

const convertByteaFields = (fields, object) => fields.forEach((field) => _.update(object, field, valueToBuffer));

const addAddressBook = async (addressBookInput) => {
  const insertFields = ['start_consensus_timestamp', 'end_consensus_timestamp', 'file_id', 'node_count', 'file_data'];

  const addressBook = {
    start_consensus_timestamp: 0,
    end_consensus_timestamp: null,
    file_id: 102,
    node_count: 20,
    file_data: defaultFileData,
    ...addressBookInput,
  };

  addressBook.file_data =
    typeof addressBookInput.file_data === 'string'
      ? Buffer.from(addressBookInput.file_data, 'utf8')
      : Buffer.from(addressBook.file_data);

  addressBook.file_id = encodedIdFromSpecValue(addressBook.file_id);

  await insertDomainObject('address_book', insertFields, addressBook);
};

const addAddressBookEntry = async (addressBookEntryInput) => {
  const insertFields = [
    'consensus_timestamp',
    'memo',
    'public_key',
    'node_id',
    'node_account_id',
    'node_cert_hash',
    'description',
    'stake',
  ];

  const addressBookEntry = {
    consensus_timestamp: 0,
    memo: EntityId.parseString('3').toString(),
    public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    node_id: 2000,
    node_account_id: 3,
    node_cert_hash: '01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16',
    description: 'description',
    stake: 0,
    ...addressBookEntryInput,
  };

  // node_cert_hash is double hex encoded
  addressBookEntry.node_cert_hash =
    typeof addressBookEntryInput.node_cert_hash === 'string'
      ? Buffer.from(addressBookEntryInput.node_cert_hash, 'hex')
      : Buffer.from(addressBookEntry.node_cert_hash);

  addressBookEntry.node_account_id = encodedIdFromSpecValue(addressBookEntry.node_account_id);

  await insertDomainObject('address_book_entry', insertFields, addressBookEntry);
};

const addAddressBookServiceEndpoint = async (addressBookServiceEndpointInput) => {
  const insertFields = ['consensus_timestamp', 'domain_name', 'ip_address_v4', 'node_id', 'port'];

  const addressBookServiceEndpoint = {
    consensus_timestamp: 0,
    domain_name: '',
    ip_address_v4: '127.0.0.1',
    node_id: 0,
    port: 50211,
    ...addressBookServiceEndpointInput,
  };

  await insertDomainObject('address_book_service_endpoint', insertFields, addressBookServiceEndpoint);
};

const entityDefaults = {
  alias: null,
  auto_renew_account_id: null,
  auto_renew_period: null,
  balance: null,
  balance_timestamp: null,
  created_timestamp: null,
  decline_reward: false,
  deleted: false,
  ethereum_nonce: null,
  evm_address: null,
  expiration_timestamp: null,
  id: null,
  key: null,
  max_automatic_token_associations: null,
  memo: 'entity memo',
  num: 0,
  obtainer_id: null,
  permanent_removal: null,
  proxy_account_id: null,
  public_key: null,
  realm: config.common.realm,
  receiver_sig_required: false,
  shard: config.common.shard,
  staked_account_id: null,
  staked_node_id: -1,
  stake_period_start: -1,
  timestamp_range: '[0,)',
  type: constants.entityTypes.ACCOUNT,
};

const addEntity = async (defaults, custom) => {
  const insertFields = Object.keys(entityDefaults).sort();
  const entity = {
    ...entityDefaults,
    ...defaults,
    ...custom,
  };
  entity.id = EntityId.of(
    BigInt(entity.shard),
    BigInt(entity.realm),
    BigInt(entity.num || entity.id || entityDefaults.num)
  ).getEncodedId();
  entity.alias = base32.decode(entity.alias);
  entity.evm_address = valueToBuffer(entity.evm_address);
  entity.staked_account_id = encodedIdFromSpecValue(entity.staked_account_id);
  entity.obtainer_id = encodedIdFromSpecValue(entity.obtainer_id);
  entity.proxy_account_id = encodedIdFromSpecValue(entity.proxy_account_id);
  entity.auto_renew_account_id = encodedIdFromSpecValue(entity.auto_renew_account_id);

  if (typeof entity.key === 'string') {
    entity.key = Buffer.from(entity.key, 'hex');
  } else if (entity.key != null) {
    entity.key = Buffer.from(entity.key);
  }

  const table = getTableName('entity', entity);
  await insertDomainObject(table, insertFields, entity);
  return entity;
};

const SECONDS_PER_DAY = 86400;

const defaultEntityStake = {
  end_stake_period: 1,
  id: null,
  pending_reward: 0,
  staked_node_id_start: 1,
  staked_to_me: 0,
  stake_total_start: 0,
  timestamp_range: null,
};

const entityStakeFields = Object.keys(defaultEntityStake);

const addEntityStake = async (entityStake) => {
  entityStake = {
    ...defaultEntityStake,
    ...entityStake,
  };

  if (entityStake.timestamp_range === null) {
    const seconds = SECONDS_PER_DAY * (Number(entityStake.end_stake_period) + 1);
    const timestamp = BigInt(seconds) * BigInt(1_000_000_000) + BigInt(1);
    entityStake.timestamp_range = `[${timestamp},)`;
  }

  entityStake.id = encodedIdFromSpecValue(entityStake.id);
  await insertDomainObject(getTableName('entity_stake', entityStake), entityStakeFields, entityStake);
};

const ethereumTransactionDefaults = {
  access_list: null,
  call_data_id: null,
  call_data: null,
  chain_id: null,
  consensus_timestamp: '187654000123456',
  data: '0x000000000',
  gas_limit: 1000000,
  gas_price: '0x4a817c80',
  hash: '0x0000000000000000000000000000000000000000000000000000000000000123',
  max_fee_per_gas: null,
  max_gas_allowance: 10000,
  max_priority_fee_per_gas: null,
  nonce: 1,
  payer_account_id: 5001,
  recovery_id: 1,
  signature_r: '0xd693b532a80fed6392b428604171fb32fdbf953728a3a7ecc7d4062b1652c042',
  signature_s: '0x24e9c602ac800b983b035700a14b23f78a253ab762deab5dc27e3555a750b354',
  signature_v: '0x1b',
  to_address: '0x0000000000000000000000000000000000001389',
  type: 2,
  value: '0x0',
};

const addEthereumTransaction = async (ethereumTransaction) => {
  // any attribute starting with '_' is not a db column
  ethereumTransaction = _.omitBy(ethereumTransaction, (_v, k) => k.startsWith('_'));
  const ethTx = {
    ...ethereumTransactionDefaults,
    ...ethereumTransaction,
  };

  convertByteaFields(
    [
      'access_list',
      'call_data',
      'chain_id',
      'data',
      'gas_price',
      'hash',
      'max_fee_per_gas',
      'max_priority_fee_per_gas',
      'signature_r',
      'signature_s',
      'signature_v',
      'to_address',
      'value',
    ],
    ethTx
  );

  ethTx.payer_account_id = encodedIdFromSpecValue(ethTx.payer_account_id);
  ethTx.call_data_id = encodedIdFromSpecValue(ethTx.call_data_id);
  await insertDomainObject('ethereum_transaction', Object.keys(ethTx), ethTx);
};

const hexEncodedFileIds = [111, 112];

const addFileData = async (fileDataInput) => {
  const encoding = hexEncodedFileIds.includes(fileDataInput.entity_id) ? 'hex' : 'utf8';

  const fileData = {
    transaction_type: 17,
    ...fileDataInput,
  };

  // contract bytecode is provided as encoded hex string
  fileData.file_data =
    typeof fileDataInput.file_data === 'string'
      ? Buffer.from(fileDataInput.file_data, encoding)
      : Buffer.from(fileData.file_data);

  fileData.entity_id = encodedIdFromSpecValue(fileData.entity_id);

  await ownerPool.query(
    `insert into file_data (file_data, consensus_timestamp, entity_id, transaction_type)
     values ($1, $2, $3, $4)`,
    [fileData.file_data, fileData.consensus_timestamp, fileData.entity_id, fileData.transaction_type]
  );
};

const addAccount = async (account) => {
  await addEntity(
    {
      max_automatic_token_associations: 0,
      public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      type: constants.entityTypes.ACCOUNT,
    },
    account
  );
};
const addAssessedCustomFee = async (assessedCustomFee) => {
  assessedCustomFee = {
    effective_payer_account_ids: [],
    payer_account_id: EntityId.parseString('300').toString(),
    ...assessedCustomFee,
  };
  const {amount, collector_account_id, consensus_timestamp, effective_payer_account_ids, payer_account_id, token_id} =
    assessedCustomFee;
  const effectivePayerAccountIds = [
    '{',
    effective_payer_account_ids.map((payer) => encodedIdFromSpecValue(payer)).join(','),
    '}',
  ].join('');

  await ownerPool.query(
    `insert into assessed_custom_fee
     (amount, collector_account_id, consensus_timestamp, effective_payer_account_ids, token_id, payer_account_id)
     values ($1, $2, $3, $4, $5, $6);`,
    //TODO:// use encodedIdFromSpecValue?
    [
      amount,
      EntityId.parse(collector_account_id).getEncodedId(),
      consensus_timestamp.toString(),
      effectivePayerAccountIds,
      EntityId.parse(token_id, {isNullable: true}).getEncodedId(),
      EntityId.parse(payer_account_id).getEncodedId(),
    ]
  );
};

const addCustomFee = async (customFee) => {
  customFee.fixed_fees?.forEach(parseCustomFeeEntityIds);
  customFee.fractional_fees?.forEach(parseCustomFeeEntityIds);
  customFee.royalty_fees?.forEach((f) => {
    parseCustomFeeEntityIds(f);
    if (!_.isNil(f.fallback_fee?.denominating_token_id)) {
      f.fallback_fee.denominating_token_id = EntityId.parse(f.fallback_fee.denominating_token_id).getEncodedId();
    }
  });

  const fixed_fees = customFee.fixed_fees ? JSONStringify(customFee.fixed_fees) : null;
  const fractional_fees = customFee.fractional_fees ? JSONStringify(customFee.fractional_fees) : null;
  const royalty_fees = customFee.royalty_fees ? JSONStringify(customFee.royalty_fees) : null;
  const table = getTableName('custom_fee', customFee);
  await ownerPool.query(
    `insert into ${table} (entity_id,
                           fixed_fees,
                           fractional_fees,
                           royalty_fees,
                           timestamp_range)
     values ($1, $2, $3, $4, $5);`,
    [
      EntityId.parse(customFee.entity_id).getEncodedId(),
      fixed_fees,
      fractional_fees,
      royalty_fees,
      customFee.timestamp_range,
    ]
  );
};

const parseCustomFeeEntityIds = (fee) => {
  Object.assign(fee, {
    all_collectors_are_exempt: false,
    collector_account_id: EntityId.parseString('300').toString(),
    ...fee,
  });

  fee.collector_account_id = EntityId.parse(fee.collector_account_id).getEncodedId();
  if (!_.isNil(fee.denominating_token_id)) {
    fee.denominating_token_id = EntityId.parse(fee.denominating_token_id).getEncodedId();
  }
};

const setAccountBalance = async (balance) => {
  balance = {timestamp: 0, id: null, balance: 0, ...balance};
  const accountId = EntityId.parseString(`${balance.id}`);
  await ownerPool.query(
    `insert into account_balance (consensus_timestamp, account_id, balance)
     values ($1, $2, $3);`,
    [balance.timestamp, accountId.getEncodedId(), balance.balance]
  );

  if (balance.tokens) {
    const tokenBalances = balance.tokens.map((tokenBalance) => [
      balance.timestamp,
      accountId.getEncodedId(),
      tokenBalance.balance,
      encodedIdFromSpecValue(tokenBalance.token_num),
    ]);
    await ownerPool.query(
      pgformat(
        'insert into token_balance (consensus_timestamp, account_id, balance, token_id) values %L',
        tokenBalances
      )
    );
  }
};

const defaultTransaction = {
  batch_key: null,
  charged_tx_fee: NODE_FEE + NETWORK_FEE + SERVICE_FEE,
  consensus_timestamp: null,
  entity_id: null,
  high_volume: false,
  inner_transactions: null,
  max_custom_fees: [],
  max_fee: 33,
  nft_transfer: null,
  node_account_id: null,
  nonce: 0,
  parent_consensus_timestamp: null,
  payer_account_id: null,
  result: 22,
  scheduled: false,
  transaction_bytes: 'bytes',
  transaction_hash: Buffer.from([...Array(49).keys()].splice(1)),
  type: 14,
  valid_duration_seconds: 11,
  valid_start_ns: null,
  index: 1,
};
const transactionFields = Object.keys(defaultTransaction);

const addTransaction = async (transaction) => {
  transaction = {
    ...defaultTransaction,
    // transfer which aren't in the defaults
    transfers: [],
    ...transaction,
    valid_start_ns: transaction.valid_start_timestamp,
  };

  transaction.high_volume = transaction.high_volume ?? false;
  transaction.entity_id = encodedIdFromSpecValue(transaction.entity_id);
  transaction.node_account_id = encodedIdFromSpecValue(transaction.nodeAccountId);
  transaction.payer_account_id = encodedIdFromSpecValue(transaction.payerAccountId);

  if ((transaction.max_custom_fees ?? []).length !== 0) {
    const idDefaults = {shardNum: BigInt(config.common.shard), realmNum: BigInt(config.common.realm)};
    transaction.max_custom_fees = transaction.max_custom_fees.map((fee) => {
      if (fee.fees) {
        fee.fees = fee.fees.map((f) => {
          if (f.denominatingTokenId && Object.keys(f.denominatingTokenId).length) {
            f.denominatingTokenId = {...f.denominatingTokenId, ...idDefaults};
          }
          return f;
        });
      }
      fee.accountId = {...fee.accountId, ...idDefaults};
      return proto.CustomFeeLimit.encode(fee).finish();
    });
  }

  if (transaction.nft_transfer !== null) {
    transaction.nft_transfer.forEach((nftTransfer) => {
      ['receiver_account_id', 'sender_account_id', 'token_id'].forEach((key) => {
        _.update(nftTransfer, key, (value) => EntityId.parse(value, {isNullable: true}).getEncodedId());
      });
    });
    // use JSONStringify to handle BigInt values
    transaction.nft_transfer = JSONStringify(transaction.nft_transfer);
  }

  if (typeof transaction.batch_key === 'string') {
    transaction.batch_key = Buffer.from(transaction.batch_key, 'hex');
  } else if (transaction.batch_key != null) {
    transaction.batch_key = Buffer.from(transaction.batch_key);
  }
  transaction.transaction_hash = valueToBuffer(transaction.transaction_hash);

  if (transaction.valid_start_ns === undefined) {
    // set valid_start_ns to consensus_timestamp - 1 if not set
    const consensusTimestamp = BigInt(transaction.consensus_timestamp);
    transaction.valid_start_ns = consensusTimestamp - 1n;
  }

  if ((transaction.inner_transactions ?? []).length > 0) {
    const newInnerTransactions = [...transaction.inner_transactions];
    for (let i = 0; i < newInnerTransactions.length; i += 2) {
      newInnerTransactions[i] = encodedIdFromSpecValue(transaction.inner_transactions[i]);
    }
    transaction.inner_transactions = newInnerTransactions;
  }

  const {node_account_id: nodeAccount, payer_account_id: payerAccount} = transaction;
  await insertDomainObject('transaction', transactionFields, transaction);

  await addTransactionHash({
    consensus_timestamp: transaction.consensus_timestamp,
    hash: transaction.transaction_hash,
    payer_account_id: transaction.payer_account_id,
  });
  await insertTransfers(
    'crypto_transfer',
    transaction.consensus_timestamp,
    transaction.transfers,
    transaction.charged_tx_fee > 0,
    payerAccount,
    nodeAccount
  );

  await insertTokenTransfers(transaction.consensus_timestamp, transaction.token_transfer_list, payerAccount);
};

const addTransactionHash = async (transactionHash) => {
  const hashCopy = {...transactionHash};
  const hashBuffer = valueToBuffer(hashCopy.hash);
  hashCopy.hash = hashBuffer.subarray(0, 32);
  hashCopy.hash_suffix = hashBuffer.length > 32 ? hashBuffer.subarray(32) : null;
  hashCopy.payer_account_id = EntityId.parse(hashCopy.payer_account_id).getEncodedId();
  await insertDomainObject('transaction_hash', Object.keys(hashCopy), hashCopy);
};

const insertTransfers = async (
  tableName,
  consensusTimestamp,
  transfers,
  hasChargedTransactionFee,
  payerAccountId,
  nodeAccount
) => {
  if (transfers.length === 0 && hasChargedTransactionFee && payerAccountId) {
    // insert default crypto transfers to node and treasury
    await ownerPool.query(
      `insert into ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       values ($1, $2, $3, $4, $5);`,
      [
        consensusTimestamp.toString(),
        NODE_FEE,
        nodeAccount || encodedIdFromSpecValue(DEFAULT_NODE_ID),
        payerAccountId,
        false,
      ]
    );
    await ownerPool.query(
      `insert into ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       values ($1, $2, $3, $4, $5);`,
      [
        consensusTimestamp.toString(),
        NETWORK_FEE,
        encodedIdFromSpecValue(DEFAULT_FEE_COLLECTOR_NUM),
        payerAccountId,
        false,
      ]
    );
    await ownerPool.query(
      `insert into ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       values ($1, $2, $3, $4, $5);`,
      [consensusTimestamp.toString(), -(NODE_FEE + NETWORK_FEE), payerAccountId, payerAccountId, false]
    );
  }

  for (const transfer of transfers) {
    await ownerPool.query(
      `insert into ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       values ($1, $2, $3, $4, $5);`,
      [
        consensusTimestamp.toString(),
        transfer.amount,
        EntityId.parse(transfer.account).getEncodedId(),
        payerAccountId,
        transfer.is_approval,
      ]
    );
  }
};

const insertTokenTransfers = async (consensusTimestamp, transfers, payerAccountId) => {
  if (!transfers || transfers.length === 0) {
    return;
  }

  const tokenTransfers = transfers.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      encodedIdFromSpecValue(transfer.token_id),
      encodedIdFromSpecValue(transfer.account),
      transfer.amount,
      payerAccountId,
      transfer.is_approval,
    ];
  });

  await ownerPool.query(
    pgformat(
      'insert into token_transfer (consensus_timestamp, token_id, account_id, amount, payer_account_id, is_approval) values %L',
      tokenTransfers
    )
  );
};

const contractDefaults = {
  file_id: null,
  id: null,
  initcode: null,
  runtime_bytecode: null,
};
const addContract = async (custom) => {
  const entity = await addEntity(
    {
      max_automatic_token_associations: 0,
      memo: 'contract memo',
      receiver_sig_required: null,
      type: constants.entityTypes.CONTRACT,
    },
    custom
  );

  if (isHistory(entity)) {
    return;
  }

  const contract = {
    ...contractDefaults,
    ...entity,
    ...custom,
  };

  convertByteaFields(['initcode', 'runtime_bytecode'], contract);
  contract.file_id = encodedIdFromSpecValue(contract.file_id);

  await insertDomainObject('contract', Object.keys(contractDefaults), contract);
};

const contractActionDefaults = {
  call_depth: 1,
  call_operation_type: 1,
  call_type: 1,
  caller: 8001,
  caller_type: 'CONTRACT',
  consensus_timestamp: 1234510001,
  gas: 10000,
  gas_used: 5000,
  index: 1,
  input: null,
  payer_account_id: DEFAULT_PAYER_ACCOUNT_ID,
  recipient_account: null,
  recipient_address: null,
  recipient_contract: null,
  result_data: null,
  result_data_type: 11,
  value: 100,
};

const contractStateDefaults = {
  contract_id: null,
  created_timestamp: 1664365660048674966,
  modified_timestamp: 1664365660048674966,
  slot: '0000000000000000000000000000000000000000000000000000000000000001',
  value: 1,
};

const nodeDefaults = {
  account_id: null,
  admin_key: 'OiG1sc5qQaLDwUfqfBg8urypfFGcwlesArZzBDsEgvyr0MQ',
  created_timestamp: 1664365660048674966,
  decline_reward: false,
  deleted: false,
  grpc_proxy_endpoint: {
    domain_name: 'localhost',
    ip_address_v4: '',
    port: 80,
  },
  node_id: 0,
  timestamp_range: '[0,)',
};

const addContractAction = async (contractActionInput) => {
  const action = {
    ...contractActionDefaults,
    ...contractActionInput,
  };

  convertByteaFields(['input', 'recipient_address', 'result_data'], action);
  action.payer_account_id = encodedIdFromSpecValue(action.payer_account_id);
  action.caller = encodedIdFromSpecValue(action.caller);
  action.recipient_contract = encodedIdFromSpecValue(action.recipient_contract);

  await insertDomainObject('contract_action', Object.keys(contractActionDefaults), action);
};

const addContractState = async (contractStateInput) => {
  const state = {
    ...contractStateDefaults,
    ...contractStateInput,
  };

  convertByteaFields(['slot', 'value'], state);
  state.contract_id = encodedIdFromSpecValue(state.contract_id);

  await insertDomainObject('contract_state', Object.keys(contractStateDefaults), state);
};

const contractResultDefaults = {
  amount: 0,
  bloom: null,
  call_result: null,
  consensus_timestamp: 1234510001,
  contract_id: 0,
  created_contract_ids: [],
  error_message: '',
  failed_initcode: null,
  function_parameters: '0x010102020303',
  function_result: null,
  gas_consumed: null,
  gas_limit: 1000,
  gas_used: null,
  payer_account_id: DEFAULT_PAYER_ACCOUNT_ID,
  sender_id: DEFAULT_SENDER_ID,
  transaction_hash: Buffer.from([...Array(32).keys()]),
  transaction_index: 1,
  transaction_nonce: 0,
  transaction_result: 22,
};

const contractResultInsertFields = Object.keys(contractResultDefaults);

const contractTransactionHashDefaults = {
  consensus_timestamp: contractResultDefaults.consensus_timestamp,
  entity_id: contractResultDefaults.contract_id,
  hash: contractResultDefaults.transaction_hash,
  payer_account_id: contractResultDefaults.payer_account_id,
  transaction_result: contractResultDefaults.transaction_result,
};

const contractTransactionHashInsertFields = Object.keys(contractTransactionHashDefaults);

const addContractResult = async (contractResultInput) => {
  const contractResult = {
    ...contractResultDefaults,
    ...contractResultInput,
  };

  convertByteaFields(
    ['bloom', 'call_result', 'failed_initcode', 'function_parameters', 'function_result', 'transaction_hash'],
    contractResult
  );

  contractResult.contract_id = encodedIdFromSpecValue(contractResult.contract_id);
  contractResult.payer_account_id = encodedIdFromSpecValue(contractResult.payer_account_id);
  contractResult.sender_id = encodedIdFromSpecValue(contractResult.sender_id);
  contractResult.created_contract_ids = contractResult.created_contract_ids.map((id) => encodedIdFromSpecValue(id));

  const contractHash = {
    consensus_timestamp: contractResult.consensus_timestamp,
    entity_id: contractResult.contract_id,
    hash: contractResult.transaction_hash,
    payer_account_id: contractResult.payer_account_id,
    transaction_result: contractResult.transaction_result,
  };
  await insertDomainObject('contract_result', contractResultInsertFields, contractResult);
  await insertDomainObject('contract_transaction_hash', contractTransactionHashInsertFields, contractHash);
};

const contractLogDefaults = {
  bloom: '0x0123',
  consensus_timestamp: 1234510001,
  contract_id: 1,
  data: '0x0123',
  index: 0,
  payer_account_id: DEFAULT_PAYER_ACCOUNT_ID,
  root_contract_id: null,
  topic0: '0x97c1fc0a6ed5551bc831571325e9bdb365d06803100dc20648640ba24ce69750',
  topic1: '0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925',
  topic2: '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef',
  topic3: '0xe8d47b56e8cdfa95f871b19d4f50a857217c44a95502b0811a350fec1500dd67',
  transaction_hash: Buffer.from([...Array(32).keys()]),
  transaction_index: 0,
};

const addContractLog = async (contractLogInput) => {
  const contractLog = {
    ...contractLogDefaults,
    ...contractLogInput,
  };

  convertByteaFields(['bloom', 'data', 'topic0', 'topic1', 'topic2', 'topic3', 'transaction_hash'], contractLog);
  contractLog.contract_id = encodedIdFromSpecValue(contractLog.contract_id);
  contractLog.root_contract_id = encodedIdFromSpecValue(contractLog.root_contract_id);

  await insertDomainObject('contract_log', Object.keys(contractLog), contractLog);
};

const defaultContractStateChange = {
  consensus_timestamp: 1234510001,
  contract_id: 1,
  migration: false,
  payer_account_id: 2,
  slot: '0x1',
  value_read: '0x0101',
  value_written: '0xa1a1',
};

const addContractStateChange = async (contractStateChangeInput) => {
  const contractStateChange = {
    ...defaultContractStateChange,
    ...contractStateChangeInput,
  };

  convertByteaFields(['slot', 'value_read', 'value_written'], contractStateChange);

  contractStateChange.contract_id = encodedIdFromSpecValue(contractStateChange.contract_id);
  contractStateChange.payer_account_id = encodedIdFromSpecValue(contractStateChange.payer_account_id);

  await insertDomainObject('contract_state_change', Object.keys(contractStateChange), contractStateChange);
};

const addContractTransaction = async (contractTransaction) => {
  await insertDomainObject('contract_transaction', Object.keys(contractTransaction), contractTransaction);
};

const defaultCryptoAllowance = {
  amount: 0,
  amount_granted: 0,
  owner: 1000,
  payer_account_id: 101,
  spender: 2000,
  timestamp_range: '[0,)',
};

const cryptoAllowanceFields = Object.keys(defaultCryptoAllowance);

const addCryptoAllowance = async (cryptoAllowanceInput) => {
  const cryptoAllowance = {
    ...defaultCryptoAllowance,
    ...cryptoAllowanceInput,
  };

  cryptoAllowance.owner = encodedIdFromSpecValue(cryptoAllowance.owner);
  cryptoAllowance.payer_account_id = encodedIdFromSpecValue(cryptoAllowance.payer_account_id);
  cryptoAllowance.spender = encodedIdFromSpecValue(cryptoAllowance.spender);

  const table = getTableName('crypto_allowance', cryptoAllowance);
  await insertDomainObject(table, cryptoAllowanceFields, cryptoAllowance);
};

const addCryptoTransaction = async (cryptoTransfer) => {
  if (!('senderAccountId' in cryptoTransfer)) {
    cryptoTransfer.senderAccountId = cryptoTransfer.payerAccountId;
  }
  if (!('payerAccountId' in cryptoTransfer)) {
    cryptoTransfer.payerAccountId = cryptoTransfer.senderAccountId;
  }
  if (!('amount' in cryptoTransfer)) {
    cryptoTransfer.amount = NODE_FEE;
  }
  if (!('recipientAccountId' in cryptoTransfer)) {
    cryptoTransfer.recipientAccountId = cryptoTransfer.nodeAccountId;
  }

  if (!('transfers' in cryptoTransfer)) {
    cryptoTransfer.transfers = [
      {
        account: encodedIdFromSpecValue(cryptoTransfer.senderAccountId),
        amount: -NETWORK_FEE - BigInt(cryptoTransfer.amount),
        is_approval: false,
      },
      {
        account: encodedIdFromSpecValue(cryptoTransfer.recipientAccountId),
        amount: cryptoTransfer.amount,
        is_approval: false,
      },
      {account: encodedIdFromSpecValue(cryptoTransfer.treasuryAccountId), amount: NETWORK_FEE, is_approval: false},
    ];
  }
  await addTransaction(cryptoTransfer);
};

const addTopicMessage = async (message) => {
  const insertFields = [
    'chunk_num',
    'chunk_total',
    'consensus_timestamp',
    'initial_transaction_id',
    'message',
    'payer_account_id',
    'running_hash',
    'running_hash_version',
    'sequence_number',
    'topic_id',
    'valid_start_timestamp',
  ];

  const table = 'topic_message';

  message = {
    chunk_num: null,
    chunk_total: null,
    initial_transaction_id: null,
    message: 'message', // Base64 encoding: bWVzc2FnZQ==
    payer_account_id: 3,
    running_hash: 'running_hash', // Base64 encoding: cnVubmluZ19oYXNo
    running_hash_version: 2,
    valid_start_timestamp: null,
    ...message,
  };

  message.initial_transaction_id = valueToBuffer(message.initial_transaction_id);
  if (message.initial_transaction_id) {
    const initialTransactionIdProto = proto.TransactionID.decode(valueToBuffer(message.initial_transaction_id));
    initialTransactionIdProto.accountID = proto.AccountID.create({
      accountNum: initialTransactionIdProto.accountID.accountNum,
      shardNum: BigInt(config.common.shard),
      realmNum: BigInt(config.common.realm),
    });
    message.initial_transaction_id = proto.TransactionID.encode(initialTransactionIdProto).finish();
  }
  message.payer_account_id = encodedIdFromSpecValue(message.payer_account_id);
  message.topic_id = encodedIdFromSpecValue(message.topic_id);

  await insertDomainObject(table, insertFields, message);
};

const addTopicMessageLookup = async (topicMessageLookup) => {
  const insertFields = ['partition', 'timestamp_range', 'sequence_number_range', 'topic_id'];

  const table = 'topic_message_lookup';
  await insertDomainObject(table, insertFields, topicMessageLookup);
};

const addSchedule = async (schedule) => {
  schedule = {
    creator_account_id: '0.0.1024',
    payer_account_id: '0.0.1024',
    transaction_body: Buffer.from([1, 1, 2, 2, 3, 3]),
    wait_for_expiry: false,
    ...schedule,
  };

  schedule.creator_account_id = encodedIdFromSpecValue(schedule.creator_account_id);
  schedule.payer_account_id = encodedIdFromSpecValue(schedule.payer_account_id);
  schedule.schedule_id = encodedIdFromSpecValue(schedule.schedule_id);

  await ownerPool.query(
    `insert into schedule (consensus_timestamp,
                           creator_account_id,
                           executed_timestamp,
                           payer_account_id,
                           schedule_id,
                           transaction_body,
                           expiration_time,
                           wait_for_expiry)
     values ($1, $2, $3, $4, $5, $6, $7, $8)`,
    [
      schedule.consensus_timestamp,
      schedule.creator_account_id,
      schedule.executed_timestamp,
      schedule.payer_account_id,
      schedule.schedule_id,
      schedule.transaction_body,
      schedule.expiration_time,
      schedule.wait_for_expiry,
    ]
  );
};

const defaultStakingRewardTransfer = {
  account_id: 1001,
  amount: 100,
  consensus_timestamp: null,
  payer_account_id: 950,
};

const addStakingRewardTransfer = async (transfer) => {
  const account_id = transfer.account_id
    ? EntityId.parse(transfer.account_id).getEncodedId()
    : defaultStakingRewardTransfer.account_id;
  const payer_account_id = transfer.payer_account_id
    ? EntityId.parse(transfer.payer_account_id).getEncodedId()
    : defaultStakingRewardTransfer.payer_account_id;

  const stakingRewardTransfer = {
    ...defaultStakingRewardTransfer,
    ...transfer,
    account_id: account_id,
    payer_account_id: payer_account_id,
  };

  const insertFields = ['account_id', 'amount', 'consensus_timestamp', 'payer_account_id'];
  await insertDomainObject('staking_reward_transfer', insertFields, stakingRewardTransfer);
};

const addTransactionSignature = async (transactionSignature) => {
  await ownerPool.query(
    `insert into transaction_signature (consensus_timestamp,
                                        public_key_prefix,
                                        entity_id,
                                        signature,
                                        type)
     values ($1, $2, $3, $4, $5)`,
    [
      transactionSignature.consensus_timestamp,
      Buffer.from(transactionSignature.public_key_prefix),
      EntityId.parse(transactionSignature.entity_id, {isNullable: true}).getEncodedId(),
      Buffer.from(transactionSignature.signature),
      transactionSignature.type,
    ]
  );
};

const tokenDefaults = {
  created_timestamp: 0,
  decimals: 1000,
  fee_schedule_key: null,
  freeze_default: false,
  freeze_key: null,
  freeze_status: 0,
  initial_supply: 1000000,
  kyc_key: null,
  kyc_status: 0,
  max_supply: '9223372036854775807', // max long, cast to string to avoid error from JavaScript Number cast
  metadata: [],
  metadata_key: null,
  name: 'Token name',
  pause_key: null,
  pause_status: 'NOT_APPLICABLE',
  supply_key: null,
  supply_type: 'INFINITE',
  symbol: 'YBTJBOAZ',
  timestamp_range: null,
  token_id: '0.0.0',
  total_supply: 1000000,
  treasury_account_id: '0.0.98',
  type: 'FUNGIBLE_COMMON',
  wipe_key: null,
};

const addToken = async (custom) => {
  const insertFields = Object.keys(tokenDefaults).sort();
  const token = {
    ...tokenDefaults,
    ...custom,
  };

  if (token.type === 'NON_FUNGIBLE_UNIQUE') {
    token.decimals = 0;
    token.initial_supply = 0;
  }

  if (!token.timestamp_range) {
    token.timestamp_range = `[${token.created_timestamp},)`;
  }

  token.token_id = encodedIdFromSpecValue(token.token_id);
  token.treasury_account_id = encodedIdFromSpecValue(token.treasury_account_id);

  convertByteaFields(['metadata'], token);
  const table = getTableName('token', token);
  await insertDomainObject(table, insertFields, token);

  if (!token.custom_fees) {
    // if there is no custom fees schedule for the token, add the default empty fee schedule at created_timestamp
    await addCustomFee({
      entity_id: token.token_id,
      timestamp_range: `[${token.created_timestamp},)`,
    });
  } else {
    await loadCustomFees(token.custom_fees);
  }
};

const defaultTokenAccount = {
  account_id: null,
  associated: true,
  automatic_association: false,
  balance: 0,
  balance_timestamp: 0,
  created_timestamp: 0,
  freeze_status: null,
  kyc_status: null,
  timestamp_range: null,
  token_id: null,
};

const addTokenAccount = async (tokenAccount) => {
  // create token account object
  tokenAccount = {
    ...defaultTokenAccount,
    ...tokenAccount,
  };

  tokenAccount.account_id = encodedIdFromSpecValue(tokenAccount.account_id);
  tokenAccount.token_id = encodedIdFromSpecValue(tokenAccount.token_id);
  if (tokenAccount.timestamp_range === null) {
    tokenAccount.timestamp_range = `[${tokenAccount.created_timestamp},)`;
  }

  const insertFields = Object.keys(defaultTokenAccount).sort();
  const table = getTableName('token_account', tokenAccount);
  await insertDomainObject(table, insertFields, tokenAccount);
};

const defaultTokenAllowance = {
  amount: 0,
  amount_granted: 0,
  owner: 1000,
  payer_account_id: 1000,
  spender: 2000,
  token_id: 3000,
  timestamp_range: '[0,)',
};

const tokenAllowanceFields = Object.keys(defaultTokenAllowance);

const addTokenAllowance = async (tokenAllowance) => {
  tokenAllowance = {
    ...defaultTokenAllowance,
    ...tokenAllowance,
  };

  tokenAllowance.owner = EntityId.parseString(`${tokenAllowance.owner}`).getEncodedId();
  tokenAllowance.payer_account_id = EntityId.parseString(`${tokenAllowance.payer_account_id}`).getEncodedId();
  tokenAllowance.spender = EntityId.parseString(`${tokenAllowance.spender}`).getEncodedId();
  tokenAllowance.token_id = EntityId.parseString(`${tokenAllowance.token_id}`).getEncodedId();
  const table = getTableName('token_allowance', tokenAllowance);
  await insertDomainObject(table, tokenAllowanceFields, tokenAllowance);
};

const addTokenBalance = async (tokenBalance) => {
  // create token account object
  tokenBalance = {
    consensus_timestamp: 0,
    account_id: '0.0.0',
    balance: 0,
    token_id: '0.0.0',
    ...tokenBalance,
  };

  await ownerPool.query(
    `insert into token_balance (consensus_timestamp, account_id, balance, token_id)
     values ($1, $2, $3, $4);`,
    [
      tokenBalance.consensus_timestamp,
      EntityId.parse(tokenBalance.account_id).getEncodedId(),
      tokenBalance.balance,
      EntityId.parse(tokenBalance.token_id).getEncodedId(),
    ]
  );
};

const nftDefaults = {
  account_id: '0.0.0',
  created_timestamp: 0,
  delegating_spender: null,
  deleted: false,
  metadata: '\\x',
  serial_number: 0,
  spender: null,
  timestamp_range: null,
  token_id: '0.0.0',
};

const addNft = async (custom) => {
  const insertFields = Object.keys(nftDefaults).sort();
  const nft = {
    ...nftDefaults,
    ...custom,
  };

  if (!nft.timestamp_range) {
    nft.timestamp_range = `[${nft.created_timestamp},)`;
  }

  ['account_id', 'delegating_spender', 'spender'].forEach((key) =>
    _.update(nft, key, (v) => EntityId.parse(v, {isNullable: true}).getEncodedId())
  );
  nft.token_id = EntityId.parse(nft.token_id).getEncodedId();

  await insertDomainObject(getTableName('nft', nft), insertFields, nft);
};

const addNode = async (nodeInput) => {
  const node = {
    ...nodeDefaults,
    ...nodeInput,
  };

  convertByteaFields(['admin_key'], node);
  node.grpc_proxy_endpoint = node.grpc_proxy_endpoint ? JSONStringify(node.grpc_proxy_endpoint) : null;
  node.account_id = encodedIdFromSpecValue(node.account_id);
  await insertDomainObject('node', Object.keys(nodeDefaults), node);
};

const addNodeStake = async (nodeStakeInput) => {
  const stakingPeriodEnd = 86_400_000_000_000n - 1n;
  const nodeStake = {
    consensus_timestamp: 0,
    epoch_day: 0,
    max_stake: 2000,
    min_stake: 1,
    node_id: 0,
    reward_rate: 0,
    stake: 0,
    stake_not_rewarded: 0,
    stake_rewarded: 0,
    staking_period: stakingPeriodEnd,
    ...nodeStakeInput,
  };
  const insertFields = Object.keys(nodeStake)
    .filter((k) => !k.startsWith('_'))
    .sort();

  await insertDomainObject('node_stake', insertFields, nodeStake);
};

const addRecordFile = async (recordFileInput) => {
  const insertFields = [
    'bytes',
    'gas_used',
    'consensus_end',
    'consensus_start',
    'count',
    'digest_algorithm',
    'file_hash',
    'index',
    'hapi_version_major',
    'hapi_version_minor',
    'hapi_version_patch',
    'hash',
    'load_end',
    'load_start',
    'logs_bloom',
    'name',
    'prev_hash',
    'size',
    'version',
  ];

  const recordFile = {
    bytes: '0x010102020303',
    consensus_end: 1628751573995691000,
    consensus_start: 1628751572000852000,
    count: 1200,
    digest_algorithm: 0,
    file_hash: 'dee34bdd8bbe32fdb53ce7e3cf764a0495fa5e93b15ca567208cfb384231301bedf821de07b0d8dc3fb55c5b3c90ac61',
    gas_used: 0,
    hapi_version_major: 0,
    hapi_version_minor: 11,
    hapi_version_patch: 0,
    hash: 'ed55d98d53fd55c9caf5f61affe88cd2978d37128ec54af5dace29b6fd271cbd079ebe487bda5f227087e2638b1100cf',
    index: 123456789,
    load_end: 1629298236,
    load_start: 1629298233,
    logs_bloom: Buffer.alloc(0),
    name: '2021-08-12T06_59_32.000852000Z.rcd',
    prev_hash: '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    size: 6,
    version: 5,
    ...recordFileInput,
  };

  convertByteaFields(['bytes', 'logs_bloom'], recordFile);
  recordFile.size = recordFile.bytes !== null ? recordFile.bytes.length : recordFile.size;

  await insertDomainObject('record_file', insertFields, recordFile);
};

const insertDomainObject = async (table, fields, obj) => {
  const positions = _.range(1, fields.length + 1).map((position) => `$${position}`);
  await ownerPool.query(
    `insert into ${table} (${fields})
     values (${positions});`,
    fields.map((f) => obj[f])
  );
};

// for a pair of current and history tables, if the timestamp range is open-ended, use the current table, otherwise
// use the history table
const getTableName = (base, entity) => (isHistory(entity) ? `${base}_history` : base);

const isHistory = (entity) => 'timestamp_range' in entity && !entity.timestamp_range.endsWith(',)');

export default {
  addAccount,
  addCryptoTransaction,
  addNft,
  addStakingRewardTransfer,
  addToken,
  loadAccounts,
  loadAddressBookEntries,
  loadAddressBookServiceEndpoints,
  loadAddressBooks,
  loadBalances,
  loadContractActions,
  loadContractLogs,
  loadContractResults,
  loadContractStateChanges,
  loadContractStates,
  loadContracts,
  loadContractTransactions,
  loadCryptoAllowances,
  loadCryptoTransfers,
  loadEntities,
  loadEthereumTransactions,
  loadFileData,
  loadNodes,
  loadNodeStakes,
  loadRecordFiles,
  loadStakingRewardTransfers,
  loadTokens,
  loadTokenAccounts,
  loadTopicMessageLookups,
  loadTransactions,
  loadTransactionHashes,
  loadEntityStakes,
  setAccountBalance,
  setup,
};
