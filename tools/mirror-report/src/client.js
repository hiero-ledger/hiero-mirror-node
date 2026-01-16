// SPDX-License-Identifier: Apache-2.0

import JSONBigFactory from 'json-bigint';
import {log} from './logger.js';

const JSONBig = JSONBigFactory({useNativeBigInt: true});
const OPTIONS = {compress: true, headers: {Accept: 'application/json'}};
const PREFIX = '/api/v1';

export class MirrorNodeClient {
  constructor(network) {
    this.network = network;
  }

  _getUrl(path) {
    const prefixedPath = path.startsWith(PREFIX) ? path : PREFIX + path;
    return `https://${this.network}.mirrornode.hedera.com${prefixedPath}`;
  }

  get(path) {
    const url = this._getUrl(path);
    log(`Invoking ${url}`);

    return fetch(url, OPTIONS)
      .then(async (res) => JSONBig.parse(await res.text()))
      .then((res) => {
        if (res._status != null) {
          log(`Error invoking URL: ${JSONBig.stringify(res._status.messages)}`);
          return null;
        }
        return res;
      })
      .catch((e) => log(`Error invoking URL: ${e}`));
  }
}
