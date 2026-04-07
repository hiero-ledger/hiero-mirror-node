// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import BaseService from '../../service/baseService';
import {FileService} from '../../service';
import {NotFoundError} from '../../errors';

describe('FileService unit tests', () => {
  test('getFileById maps row correctly', async () => {
    const mockRow = {
      id: 123n,
      memo: 'test memo',
      deleted: false,
      expiration_timestamp: 2000000000n,
      auto_renew_period: null,
      key: Buffer.from('010203', 'hex'),
      created_timestamp: 1000000000n,
    };

    const spy = jest.spyOn(BaseService.prototype, 'getSingleRow').mockResolvedValue(mockRow);

    const res = await FileService.getFileById(123n);
    expect(res.file_id).toBe(123n);
    expect(res.memo).toBe('test memo');
    expect(res.deleted).toBe(false);
    expect(res.created_timestamp).toBeDefined();
    expect(res.key === null || typeof res.key === 'object').toBe(true);

    spy.mockRestore();
  });

  test('getFileById throws NotFoundError when no row', async () => {
    const spy = jest.spyOn(BaseService.prototype, 'getSingleRow').mockResolvedValue(null);

    await expect(FileService.getFileById(999n)).rejects.toThrow(NotFoundError);

    spy.mockRestore();
  });
});
