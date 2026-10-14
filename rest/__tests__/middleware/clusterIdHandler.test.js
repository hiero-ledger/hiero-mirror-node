// SPDX-License-Identifier: Apache-2.0

import express from 'express';
import request from 'supertest';

import {clusterIdHeader} from '../../constants.js';
import extendExpress from '../../extendExpress.js';
import {clusterId, clusterIdHandler} from '../../middleware/clusterIdHandler.js';

const app = extendExpress(express());
app.useExt(clusterIdHandler);
app.get('/ok', (req, res) => {
  res.status(200).json({ok: true});
});
app.get('/error', (req, res) => {
  res.status(400).json({message: 'bad'});
});

describe('Cluster id header', () => {
  test('success response', async () => {
    const response = await request(app).get('/ok');

    expect(response.status).toBe(200);
    expect(response.headers[clusterIdHeader]).toBe('9ef9396');
    expect(clusterId).toBe('9ef9396');
  });

  test('error response', async () => {
    const response = await request(app).get('/error');

    expect(response.status).toBe(400);
    expect(response.headers[clusterIdHeader]).toBe(clusterId);
  });
});
