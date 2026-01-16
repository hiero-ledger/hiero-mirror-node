// SPDX-License-Identifier: Apache-2.0

export const log = (message) => {
  const timestamp = new Date().toISOString();
  console.log(`${timestamp} ${message}`);
};
