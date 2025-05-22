// SPDX-License-Identifier: Apache-2.0

import log4js from 'log4js';

export default async () => {
  for (const dbContainer of globalThis.__DB_CONTAINERS__.values()) {
    await dbContainer.stop();
  }
  globalThis.__DB_CONTAINER_SERVER__.close();
  log4js.shutdown();
};
