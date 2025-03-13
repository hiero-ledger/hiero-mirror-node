// SPDX-License-Identifier: Apache-2.0

describe('treasuryAccount', () => {
  const {common} = getMirrorConfig();
  afterEach(() => {
    common.realm = 0n;
    common.shard = 0n;
  });

  test.each`
    shard | realm | expected
    ${0}  | ${0}  | ${'0.0.2'}
    ${1}  | ${2}  | ${'1.2.2'}
  `(`returns treasury account $expected given shard=$shard, realm=$realm`, ({shard, realm, expected}) => {
    common.shard = BigInt(shard);
    common.realm = BigInt(realm);
    const expectedEntityId = EntityId.parse(expected);
    expect(SystemEntity.treasuryAccount).toEqual(expectedEntityId);
  });
});
