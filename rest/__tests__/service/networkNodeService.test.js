// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import {NetworkNodeService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import * as utils from '../../utils';
import EntityId from '../../entityId';

setupIntegrationTest();

const defaultNodeFilter = 'abe.node_id = $2';

const bigIntRange = (from, to) => {
  const result = [];
  for (let i = from; i <= to; i++) {
    result.push(i);
  }
  return result;
};

describe('NetworkNodeService.getNetworkNodesWithFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = NetworkNodeService.getNetworkNodesWithFiltersQuery([], [102], 'asc', 5);
    const expected = `with adb as (select start_consensus_timestamp, end_consensus_timestamp, file_id
                                   from address_book
                                   where file_id = $1
                                   order by start_consensus_timestamp desc
                                   limit 1),
                           ns as (select max_stake,
                                         min_stake,
                                         node_id,
                                         reward_rate,
                                         stake,
                                         stake_not_rewarded,
                                         stake_rewarded,
                                         staking_period
                                  from node_stake
                                  where consensus_timestamp = (select max(consensus_timestamp) from node_stake)),
                           n as (select admin_key, decline_reward, grpc_proxy_endpoint, node_id, account_id
                                 from node)
                      select abe.description,
                             abe.memo,
                             abe.node_id,
                             coalesce(n.account_id, abe.node_account_id) as node_account_id,
                             abe.node_cert_hash,
                             abe.public_key,
                             adb.file_id,
                             adb.start_consensus_timestamp,
                             adb.end_consensus_timestamp,
                             n.admin_key,
                             n.decline_reward,
                             n.grpc_proxy_endpoint,
                             ns.max_stake,
                             ns.min_stake,
                             ns.reward_rate,
                             ns.stake,
                             ns.stake_not_rewarded,
                             ns.stake_rewarded,
                             ns.staking_period,
                             coalesce(
                                     (select jsonb_agg(
                                                     jsonb_build_object('ip_address_v4', ip_address_v4, 'port', port,
                                                                        'domain_name', domain_name)
                                                     order by ip_address_v4 asc,port asc)
                                      from address_book_service_endpoint abse
                                      where abse.consensus_timestamp = abe.consensus_timestamp
                                        and abse.node_id = abe.node_id),
                                     '[]'
                             ) as service_endpoints
                      from address_book_entry abe
                               join adb on adb.start_consensus_timestamp = abe.consensus_timestamp
                               left join ns on abe.node_id = ns.node_id
                               left join n on abe.node_id = n.node_id
                      order by abe.node_id asc
                      limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([102, 5]);
  });

  test('Verify node file query', async () => {
    const [query, params] = NetworkNodeService.getNetworkNodesWithFiltersQuery([defaultNodeFilter], [102, 3], 'asc', 5);
    const expected = `with adb as (select start_consensus_timestamp, end_consensus_timestamp, file_id
                                   from address_book
                                   where file_id = $1
                                   order by start_consensus_timestamp desc
                                   limit 1),
                           ns as (select max_stake,
                                         min_stake,
                                         node_id,
                                         reward_rate,
                                         stake,
                                         stake_not_rewarded,
                                         stake_rewarded,
                                         staking_period
                                  from node_stake
                                  where consensus_timestamp = (select max(consensus_timestamp) from node_stake)),
                           n as (select admin_key, decline_reward, grpc_proxy_endpoint, node_id, account_id
                                 from node)
                      select abe.description,
                             abe.memo,
                             abe.node_id,
                             coalesce(n.account_id, abe.node_account_id) as node_account_id,
                             abe.node_cert_hash,
                             abe.public_key,
                             adb.file_id,
                             adb.start_consensus_timestamp,
                             adb.end_consensus_timestamp,
                             n.admin_key,
                             n.decline_reward,
                             n.grpc_proxy_endpoint,
                             ns.max_stake,
                             ns.min_stake,
                             ns.reward_rate,
                             ns.stake,
                             ns.stake_not_rewarded,
                             ns.stake_rewarded,
                             ns.staking_period,
                             coalesce(
                                     (select jsonb_agg(
                                                     jsonb_build_object('ip_address_v4', ip_address_v4, 'port', port,
                                                                        'domain_name', domain_name)
                                                     order by ip_address_v4 asc,port asc)
                                      from address_book_service_endpoint abse
                                      where abse.consensus_timestamp = abe.consensus_timestamp
                                        and abse.node_id = abe.node_id),
                                     '[]'
                             ) as service_endpoints
                      from address_book_entry abe
                               join adb on adb.start_consensus_timestamp = abe.consensus_timestamp
                               left join ns on abe.node_id = ns.node_id
                               left join n on abe.node_id = n.node_id
                      where abe.node_id = $2
                      order by abe.node_id asc
                      limit $3`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([102, 3, 5]);
  });
});

const defaultInputAddressBooks = [
  {
    start_consensus_timestamp: 1,
    file_id: EntityId.systemEntity.addressBookFile101.toString(),
    node_count: 3,
  },
  {
    start_consensus_timestamp: 2,
    file_id: EntityId.systemEntity.addressBookFile102.toString(),
    node_count: 4,
  },
];

const nodeAccount3 = EntityId.parseString('3');
const nodeAccount4 = EntityId.parseString('4');
const defaultInputAddressBookEntries = [
  {
    consensus_timestamp: 1,
    memo: 'memo 1',
    node_id: 0,
    node_account_id: nodeAccount3.toString(),
    node_cert_hash: '[0,)',
    description: 'desc 1',
    stake: 0,
  },
  {
    consensus_timestamp: 1,
    memo: 'memo 2',
    node_id: 1,
    node_account_id: nodeAccount4.toString(),
    node_cert_hash: '[0,)',
    description: 'desc 2',
    stake: 1000,
  },
  {
    consensus_timestamp: 2,
    memo: nodeAccount3.toString(),
    node_id: 0,
    node_account_id: nodeAccount3.toString(),
    node_cert_hash: '[0,)',
    description: 'desc 3',
    stake: 1000,
  },
  {
    consensus_timestamp: 2,
    memo: nodeAccount4.toString(),
    node_id: 1,
    node_account_id: nodeAccount4.toString(),
    node_cert_hash: '[0,)',
    description: 'desc 4',
    stake: null,
  },
];

const defaultInputServiceEndpointBooks = [
  {
    consensus_timestamp: 1,
    ip_address_v4: '127.0.0.1',
    node_id: 0,
    port: 50211,
  },
  {
    consensus_timestamp: 1,
    ip_address_v4: '127.0.0.2',
    node_id: 1,
    port: 50212,
  },
  {
    consensus_timestamp: 2,
    ip_address_v4: '128.0.0.1',
    node_id: 0,
    port: 50212,
  },
  {
    consensus_timestamp: 2,
    ip_address_v4: '128.0.0.2',
    node_id: 1,
    port: 50212,
  },
];

const defaultNodeStakes = [
  {
    consensus_timestamp: 1,
    epoch_day: 0,
    max_stake: 100,
    min_stake: 1,
    node_id: 0,
    reward_rate: 1,
    stake: 1,
    stake_not_rewarded: 0,
    stake_rewarded: 1,
    staking_period: 1,
  },
  {
    consensus_timestamp: 1,
    epoch_day: 0,
    max_stake: 200,
    min_stake: 2,
    node_id: 1,
    reward_rate: 2,
    stake: 2,
    stake_not_rewarded: 1,
    stake_rewarded: 1,
    staking_period: 2,
  },
  {
    consensus_timestamp: 2,
    epoch_day: 1,
    max_stake: 300,
    min_stake: 2,
    node_id: 0,
    reward_rate: 3,
    stake: 3,
    stake_not_rewarded: 1,
    stake_rewarded: 2,
    staking_period: 1654991999999999999n,
  },
  {
    consensus_timestamp: 2,
    epoch_day: 1,
    max_stake: 400,
    min_stake: 1,
    node_id: 1,
    reward_rate: 4,
    stake: 4,
    stake_not_rewarded: 1,
    stake_rewarded: 3,
    staking_period: BigInt('1655251199999999999'),
  },
];

const defaultExpectedNetworkNode101 = [
  {
    addressBook: {
      startConsensusTimestamp: 1,
      fileId: EntityId.systemEntity.addressBookFile101.getEncodedId(),
      endConsensusTimestamp: null,
    },
    addressBookEntry: {
      description: 'desc 2',
      memo: 'memo 2',
      nodeAccountId: nodeAccount4.getEncodedId(),
      nodeId: 1,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '127.0.0.2',
        port: 50212,
      },
    ],
    nodeStake: {
      maxStake: 400,
      minStake: 1,
      stake: 4,
      stakeNotRewarded: 1,
      stakeRewarded: 3,
      stakingPeriod: 1655251199999999999n,
    },
  },
  {
    addressBook: {
      startConsensusTimestamp: 1,
      fileId: EntityId.systemEntity.addressBookFile101.getEncodedId(),
      endConsensusTimestamp: null,
    },
    addressBookEntry: {
      description: 'desc 1',
      memo: 'memo 1',
      nodeAccountId: nodeAccount3.getEncodedId(),
      nodeId: 0,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '127.0.0.1',
        port: 50211,
      },
    ],
    nodeStake: {
      maxStake: 300,
      minStake: 2,
      rewardRate: 3,
      stake: 3,
      stakeNotRewarded: 1,
      stakeRewarded: 2,
      stakingPeriod: 1654991999999999999n,
    },
  },
];

const defaultExpectedNetworkNode102 = [
  {
    addressBook: {
      endConsensusTimestamp: null,
      fileId: EntityId.systemEntity.addressBookFile102.getEncodedId(),
      startConsensusTimestamp: 2,
    },
    addressBookEntry: {
      description: 'desc 3',
      memo: nodeAccount3.toString(),
      nodeAccountId: nodeAccount3.getEncodedId(),
      nodeId: 0,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '128.0.0.1',
        port: 50212,
      },
    ],
    nodeStake: {
      maxStake: 300,
      minStake: 2,
      rewardRate: 3,
      stake: 3,
      stakeNotRewarded: 1,
      stakeRewarded: 2,
      stakingPeriod: 1654991999999999999n,
    },
  },
  {
    addressBook: {
      endConsensusTimestamp: null,
      fileId: EntityId.systemEntity.addressBookFile102.getEncodedId(),
      startConsensusTimestamp: 2,
    },
    addressBookEntry: {
      description: 'desc 4',
      memo: nodeAccount4.toString(),
      nodeAccountId: nodeAccount4.getEncodedId(),
      nodeId: 1,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '128.0.0.2',
        port: 50212,
      },
    ],
    nodeStake: {
      maxStake: 400,
      minStake: 1,
      rewardRate: 4,
      stake: 4,
      stakeNotRewarded: 1,
      stakeRewarded: 3,
      stakingPeriod: 1655251199999999999n,
    },
  },
];

const defaultExpectedNetworkNodeEmptyNodeStake = [
  {
    addressBook: {
      endConsensusTimestamp: null,
      fileId: EntityId.systemEntity.addressBookFile102.getEncodedId(),
      startConsensusTimestamp: 2,
    },
    addressBookEntry: {
      description: 'desc 3',
      memo: nodeAccount3.toString(),
      nodeAccountId: nodeAccount3.getEncodedId(),
      nodeId: 0,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '128.0.0.1',
        port: 50212,
      },
    ],
    nodeStake: {
      rewardRate: null,
      stake: null,
      stakeRewarded: null,
      stakingPeriod: null,
    },
  },
  {
    addressBook: {
      endConsensusTimestamp: null,
      fileId: EntityId.systemEntity.addressBookFile102.getEncodedId(),
      startConsensusTimestamp: 2,
    },
    addressBookEntry: {
      description: 'desc 4',
      memo: nodeAccount4.toString(),
      nodeAccountId: nodeAccount4.getEncodedId(),
      nodeId: 1,
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '128.0.0.2',
        port: 50212,
      },
    ],
    nodeStake: {
      rewardRate: null,
      stake: null,
      stakeRewarded: null,
      stakingPeriod: null,
    },
  },
];

describe('NetworkNodeService.getNetworkNodes tests', () => {
  test('NetworkNodeService.getNetworkNodes - No match', async () => {
    await expect(NetworkNodeService.getNetworkNodes([], [2], 'asc', 5)).resolves.toStrictEqual([]);
  });

  test('NetworkNodeService.getNetworkNodes - Matching 101 entity', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);
    await integrationDomainOps.loadNodeStakes(defaultNodeStakes);

    await expect(
      NetworkNodeService.getNetworkNodes([], [EntityId.systemEntity.addressBookFile101.getEncodedId()], 'desc', 5)
    ).resolves.toMatchObject(defaultExpectedNetworkNode101);
  });

  test('NetworkNodeService.getNetworkNodes - Matching 102 entity', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);
    await integrationDomainOps.loadNodeStakes(defaultNodeStakes);

    await expect(
      NetworkNodeService.getNetworkNodes([], [EntityId.systemEntity.addressBookFile102.getEncodedId()], 'asc', 5)
    ).resolves.toMatchObject(defaultExpectedNetworkNode102);
  });

  test('NetworkNodeService.getNetworkNodes - Empty node stakes', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);

    await expect(
      NetworkNodeService.getNetworkNodes([], [EntityId.systemEntity.addressBookFile102.getEncodedId()], 'asc', 5)
    ).resolves.toMatchObject(defaultExpectedNetworkNodeEmptyNodeStake);
  });
});

describe('NetworkNodeService.getNetworkNodes tests node filter', () => {
  test('NetworkNodeService.getNetworkNodes - No match on nodes', async () => {
    await expect(NetworkNodeService.getNetworkNodes([defaultNodeFilter], [2, 0], 'asc', 5)).resolves.toStrictEqual([]);
  });

  const expectedNetworkNode101 = [
    {
      addressBook: {
        startConsensusTimestamp: 1,
        fileId: EntityId.systemEntity.addressBookFile101.getEncodedId(),
        endConsensusTimestamp: null,
      },
      addressBookEntry: {
        description: 'desc 1',
        memo: 'memo 1',
        nodeAccountId: nodeAccount3.getEncodedId(),
        nodeId: 0,
      },
      addressBookServiceEndpoints: [
        {
          ipAddressV4: '127.0.0.1',
          port: 50211,
        },
      ],
      nodeStake: {
        rewardRate: 3,
        stake: 3,
        stakeRewarded: 2,
        stakingPeriod: 1654991999999999999n,
      },
    },
  ];

  const expectedNetworkNode102 = [
    {
      addressBook: {
        endConsensusTimestamp: null,
        fileId: EntityId.systemEntity.addressBookFile102.getEncodedId(),
        startConsensusTimestamp: 2,
      },
      addressBookEntry: {
        description: 'desc 3',
        memo: nodeAccount3.toString(),
        nodeAccountId: nodeAccount3.getEncodedId(),
        nodeId: 0,
      },
      addressBookServiceEndpoints: [
        {
          ipAddressV4: '128.0.0.1',
          port: 50212,
        },
      ],
      nodeStake: {
        maxStake: 300,
        minStake: 2,
        rewardRate: 3,
        stake: 3,
        stakeNotRewarded: 1,
        stakeRewarded: 2,
        stakingPeriod: 1654991999999999999n,
      },
    },
  ];

  test('NetworkNodeService.getNetworkNodes - Matching 101 entity node', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);
    await integrationDomainOps.loadNodeStakes(defaultNodeStakes);

    await expect(
      NetworkNodeService.getNetworkNodes(
        [defaultNodeFilter],
        [EntityId.systemEntity.addressBookFile101.getEncodedId(), 0],
        'desc',
        5
      )
    ).resolves.toMatchObject(expectedNetworkNode101);
  });

  test('NetworkNodeService.getNetworkNodes - Matching 102 entity node', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);
    await integrationDomainOps.loadNodeStakes(defaultNodeStakes);

    await expect(
      NetworkNodeService.getNetworkNodes(
        [defaultNodeFilter],
        [EntityId.systemEntity.addressBookFile102.getEncodedId(), 0],
        'asc',
        5
      )
    ).resolves.toMatchObject(expectedNetworkNode102);
  });
});

describe('NetworkNodeService node_account_id override logic', () => {
  const nodeAccount1000 = EntityId.parseString('1000');
  const nodeAccount1001 = EntityId.parseString('1001');

  test('override node_account_id when present in node table', async () => {
    const addressBooks = [
      {
        start_consensus_timestamp: 10,
        file_id: EntityId.systemEntity.addressBookFile102.toString(),
        node_count: 2,
      },
    ];

    const entries = [
      {
        consensus_timestamp: 10,
        node_id: 0,
        node_account_id: EntityId.parseString('3').toString(),
        description: 'desc 1',
        memo: 'memo 1',
      },
      {
        consensus_timestamp: 10,
        node_id: 1,
        node_account_id: EntityId.parseString('4').toString(),
        description: 'desc 2',
        memo: 'memo 2',
      },
    ];

    const nodes = [
      {node_id: 0, account_id: nodeAccount1000.getEncodedId(), deleted: false},
      {node_id: 1, account_id: nodeAccount1001.getEncodedId(), deleted: false},
    ];

    await integrationDomainOps.loadAddressBooks(addressBooks);
    await integrationDomainOps.loadAddressBookEntries(entries);
    await integrationDomainOps.loadNodes(nodes);

    const result = await NetworkNodeService.getNetworkNodes(
      [],
      [EntityId.systemEntity.addressBookFile102.getEncodedId()],
      'asc',
      10
    );

    expect(result).toHaveLength(2);
    expect(result[0].addressBookEntry.nodeAccountId).toEqual(nodeAccount1000.getEncodedId());
    expect(result[1].addressBookEntry.nodeAccountId).toEqual(nodeAccount1001.getEncodedId());
  });

  test('keep existing node_account_id when node table account_id is null', async () => {
    const addressBooks = [
      {
        start_consensus_timestamp: 20,
        file_id: EntityId.systemEntity.addressBookFile102.toString(),
        node_count: 1,
      },
    ];

    const nodeAccountOriginal = EntityId.parseString('3');
    const entries = [
      {
        consensus_timestamp: 20,
        node_id: 0,
        node_account_id: nodeAccountOriginal.toString(),
        description: 'desc 1',
        memo: 'memo 1',
      },
    ];

    const nodes = [{node_id: 0, account_id: null, deleted: false}];

    await integrationDomainOps.loadAddressBooks(addressBooks);
    await integrationDomainOps.loadAddressBookEntries(entries);
    await integrationDomainOps.loadNodes(nodes);

    const result = await NetworkNodeService.getNetworkNodes(
      [],
      [EntityId.systemEntity.addressBookFile102.getEncodedId()],
      'asc',
      10
    );

    expect(result).toHaveLength(1);
    expect(result[0].addressBookEntry.nodeAccountId).toEqual(nodeAccountOriginal.getEncodedId());
  });

  test('use address_book_entry when node table has no matching row', async () => {
    const addressBooks = [
      {
        start_consensus_timestamp: 30,
        file_id: EntityId.systemEntity.addressBookFile102.toString(),
        node_count: 1,
      },
    ];

    const nodeAccountOriginal = EntityId.parseString('5');
    const entries = [
      {
        consensus_timestamp: 30,
        node_id: 2,
        node_account_id: nodeAccountOriginal.toString(),
        description: 'desc without node table entry',
        memo: 'memo 3',
      },
    ];

    await integrationDomainOps.loadAddressBooks(addressBooks);
    await integrationDomainOps.loadAddressBookEntries(entries);

    const result = await NetworkNodeService.getNetworkNodes(
      [],
      [EntityId.systemEntity.addressBookFile102.getEncodedId()],
      'asc',
      10
    );

    expect(result).toHaveLength(1);
    expect(result[0].addressBookEntry.nodeAccountId).toEqual(nodeAccountOriginal.getEncodedId());
  });
});

describe(`NetworkNodeService.getSupply`, () => {
  test('Without timestamp', async () => {
    const accounts = [];
    const timestamp = utils.nowInNs();
    EntityId.systemEntity.unreleasedSupplyAccounts.forEach((range) => {
      const from = range.from.num;
      const to = range.to.num;
      bigIntRange(BigInt(from), BigInt(to)).forEach((id) => {
        accounts.push({balance: 1n, balance_timestamp: timestamp, num: id});
      });
    });
    await integrationDomainOps.loadAccounts(accounts);
    await expect(NetworkNodeService.getSupply([])).resolves.toEqual({
      consensus_timestamp: timestamp,
      unreleased_supply: '548',
    });
  });
  test('With timestamp', async () => {
    const balances = [];
    const timestamp = utils.nowInNs();
    EntityId.systemEntity.unreleasedSupplyAccounts.forEach((range) => {
      const from = range.from.num;
      const to = range.to.num;
      bigIntRange(BigInt(from), BigInt(to)).forEach((id) => {
        balances.push({balance: 1n, timestamp: timestamp, id: id});
      });
    });
    await integrationDomainOps.loadBalances(balances);
    await expect(NetworkNodeService.getSupply([`ab.consensus_timestamp <= ${timestamp}`])).resolves.toEqual({
      consensus_timestamp: timestamp,
      unreleased_supply: '548',
    });
  });
});
