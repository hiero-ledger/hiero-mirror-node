// SPDX-License-Identifier: Apache-2.0

import {normalizeRequestQueryParams} from '../../middleware/requestNormalizer.js';
import request from 'supertest';
import {openApiValidator} from '../../middleware/index.js';
import express from 'express';

const app = express();
openApiValidator(app);

describe('request normalizer', () => {
  const testSpecs = [
    {
      description: 'Unknown path',
      input: '/api/v1/unknown/123?limit=2',
      expected: '/api/v1/unknown/123?limit=2',
    },
    {
      description: 'Block query parameters are added',
      input: '/api/v1/blocks',
      expected: '/api/v1/blocks?limit=25&order=desc',
    },
    {
      description: 'Non sortable parameters are not sorted',
      input: '/api/v1/blocks?order=asc&block.number=3&block.number=2',
      expected: '/api/v1/blocks?block.number=3&block.number=2&limit=25&order=asc',
    },
    {
      description: 'Account parameters are added',
      input: '/api/v1/accounts',
      expected: '/api/v1/accounts?balance=true&limit=25&order=asc',
    },
    {
      description: 'Eq and ne parameters',
      input: '/api/v1/accounts?account.id=eq:1001&account.id=ne:3',
      expected: '/api/v1/accounts?account.id=eq:1001&account.id=ne:3&balance=true&limit=25&order=asc',
    },
    {
      description: 'Default query parameters are added to existing query parameters and sorted',
      input: '/api/v1/accounts?limit=3&account.id=gt:0.0.20&account.id=lt:0.0.21',
      expected: '/api/v1/accounts?account.id=gt:0.0.20&account.id=lt:0.0.21&balance=true&limit=3&order=asc',
    },
    {
      description: 'Multiple instances of the same parameter are allowed',
      input: '/api/v1/accounts?limit=2&limit=3&limit=5',
      expected: '/api/v1/accounts?balance=true&limit=2&limit=3&limit=5&order=asc',
    },
    {
      description: 'Accounts with path parameter has default parameter added',
      input: '/api/v1/accounts/0.0.1001/nfts',
      expected: '/api/v1/accounts/0.0.1001/nfts?limit=25&order=desc',
    },
    {
      description:
        'Accounts with path parameter and query parameters have default parameter added and parameters sorted',
      input: '/api/v1/accounts/0.0.1001/nfts?serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500&order=asc',
      expected:
        '/api/v1/accounts/0.0.1001/nfts?limit=25&order=asc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      description: 'Contract result log parameters are added and sorted',
      input: '/api/v1/contracts/results/logs?index=lt:1&timestamp=1639010141.000000000&topic0=A',
      expected: '/api/v1/contracts/results/logs?index=lt:1&limit=25&order=desc&timestamp=1639010141.000000000&topic0=A',
    },
    {
      description: 'No parameters are added',
      input: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd',
      expected: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd',
    },
    {
      description:
        'Contracts with path parameter and query parameter have default parameters added and parameters sorted',
      input: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd/results?limit=3',
      expected: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd/results?internal=false&limit=3&order=desc',
    },
    {
      description: 'Accounts nfts with path parameter and query parameters',
      input: '/api/v1/accounts/0.0.1001/nfts?token.id=gte:1500&serialnumber=gte:2&spender.id=gte:2004&order=asc',
      expected:
        '/api/v1/accounts/0.0.1001/nfts?limit=25&order=asc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      description: 'Token nfts with path parameter',
      input: '/api/v1/tokens/1500/nfts/2/transactions',
      expected: '/api/v1/tokens/1500/nfts/2/transactions?limit=25&order=desc',
    },
    {
      description: 'Token nfts with shard realm num path parameter',
      input: '/api/v1/tokens/0.0.1500/nfts/2/transactions',
      expected: '/api/v1/tokens/0.0.1500/nfts/2/transactions?limit=25&order=desc',
    },
    {
      description: 'Token nfts with path parameter and query parameters',
      input: '/api/v1/tokens/1500/nfts/2/transactions?timestamp=gte:1234567890.000000005&order=asc',
      expected: '/api/v1/tokens/1500/nfts/2/transactions?limit=25&order=asc&timestamp=gte:1234567890.000000005',
    },
    {
      description: 'Accounts nfts with shard realm num path parameter and query parameters',
      input: '/api/v1/accounts/0.0.1001/nfts?token.id=gte:1500&serialnumber=gte:2&spender.id=gte:2004',
      expected:
        '/api/v1/accounts/0.0.1001/nfts?limit=25&order=desc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      description: 'Topics messages with path parameter',
      input: '/api/v1/topics/7/messages',
      expected: '/api/v1/topics/7/messages?limit=25&order=asc',
    },
    {
      description: 'Balance is collapsed to the last value',
      input: '/api/v1/accounts?limit=3&balance=true&balance=false',
      expected: '/api/v1/accounts?balance=false&limit=3&order=asc',
    },
    {
      description: 'Two collapsable params are collapsed',
      input:
        '/api/v1/transactions/0xae8bebf1c9fa0f309356e48057f6047af7cde63037d0509d16ddc3b20e085158bfdf14d15345c1b18b199b72fed4ac6f?scheduled=false&scheduled=true&nonce=2&nonce=1',
      expected:
        '/api/v1/transactions/0xae8bebf1c9fa0f309356e48057f6047af7cde63037d0509d16ddc3b20e085158bfdf14d15345c1b18b199b72fed4ac6f?nonce=1&scheduled=true',
    },
    {
      description: 'Timestamp values are not sorted',
      input: '/api/v1/transactions?timestamp=1639010141.000000001&timestamp=1639010141.000000000',
      expected:
        '/api/v1/transactions?limit=25&order=desc&timestamp=1639010141.000000001&timestamp=1639010141.000000000',
    },
  ];

  const setupRoute = (spec) => {
    const route = spec.input.split('?')[0];
    app.get(route, (req, res) => {
      const actual = normalizeRequestQueryParams(req.openapi?.openApiRoute, req.path, req.query);
      res.set('actual', actual);
      res.sendStatus(200);
    });
  };

  testSpecs.forEach((spec) => {
    setupRoute(spec);
    test(spec.description, async () => {
      await request(app)
        .get(spec.input)
        .then((res) => {
          expect(res.get('actual')).toEqual(spec.expected);
        });
    });
  });
});
