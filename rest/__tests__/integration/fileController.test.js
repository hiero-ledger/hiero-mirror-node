// SPDX-License-Identifier: Apache-2.0

import request from 'supertest';
import EntityId from '../../entityId';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import server from '../../server';
import {apiPrefix} from '../../constants';

setupIntegrationTest();

describe('FileController integration tests', () => {
  test('GET /files/{id} returns metadata and size', async () => {
    const entityId = EntityId.parseString('123');
    const entity = {
      id: entityId.getEncodedId(),
      memo: 'file memo',
      deleted: false,
      expiration_timestamp: null,
      auto_renew_period: null,
      key: null,
      created_timestamp: 1000000000n,
      ...entityId,
    };

    await integrationDomainOps.loadEntities([entity]);

    const files = [
      {
        consensus_timestamp: 2000000000n,
        entity_id: entityId.toString(),
        file_data: Buffer.from('0a0b0c', 'hex'),
        transaction_type: 17,
      },
    ];

    await integrationDomainOps.loadFileData(files);

    const res = await request(server).get(`${apiPrefix}/files/${entityId.toString()}`).expect(200);
    expect(res.body.file_id).toBe(entityId.toString());
    expect(res.body.memo).toBe('file memo');
    expect(res.body.size_bytes).toBe(3);
    expect(res.body.consensus_timestamp).toBeDefined();
  });

  test('GET /files/{id} returns 404 for missing file', async () => {
    const entityId = EntityId.parseString('9999');
    await request(server).get(`${apiPrefix}/files/${entityId.toString()}`).expect(404);
  });
});
