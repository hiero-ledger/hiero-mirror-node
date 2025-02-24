// SPDX-License-Identifier: Apache-2.0

import {InvalidArgumentError} from 'commander';

const ACCOUNT_PATTERN = /^(\d+\.\d+\.)(\d+)(-(\d+\.\d+\.)(\d+))?$/;
const DATE_PATTERN = /^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$/;
const TINYBARS_PER_HBAR = 100000000n;
const TINYBARS_LENGTH = TINYBARS_PER_HBAR.toString().length - 1;
const ZERO = '0';

const abs = (n) => (n === -0 || n < 0n ? -n : n);

const padZeros = (str, length) => {
  const padLength = Math.max(0, length - str.length);
  return ZERO.repeat(padLength) + str;
};

const parseAccount = (accountId, previous) => {
  const match = ACCOUNT_PATTERN.exec(accountId);

  if (!match) {
    throw new InvalidArgumentError(
      `error: Invalid account ID ${accountId}. Expected a single account like 0.0.2 or a range like 0.0.2-0.0.3`
    );
  }

  let accountIds = previous instanceof Set ? previous : new Set();

  // Not a range-based account
  if (!match[3]) {
    accountIds.add(accountId);
  } else {
    const prefix = match[1];
    const start = parseInt(match[2]);
    const end = parseInt(match[5]);

    if (end <= start) {
      throw new InvalidArgumentError(`error: Account ID range end ${end} is not after start ${start}`);
    }

    if (prefix !== match[4]) {
      throw new InvalidArgumentError(`error: Account ID range start and end have a different shard and realm prefix`);
    }

    for (let i = start; i <= end; ++i) {
      accountIds.add(`${prefix}${i}`);
    }
  }

  return accountIds;
};

const toHbars = (tinybars) => {
  if (typeof tinybars != 'bigint') {
    return '';
  }

  const sign = tinybars < 0 ? '-' : '';
  const hbars = abs(tinybars) / TINYBARS_PER_HBAR;
  const fraction = abs(tinybars) % TINYBARS_PER_HBAR;
  const paddedFraction = fraction === 0n ? ZERO : padZeros(fraction.toString(), TINYBARS_LENGTH);
  return `${sign}${hbars}.${paddedFraction}`;
};

const toIsoString = (timestamp) => {
  if (!timestamp) {
    return '';
  }

  const parts = timestamp.split('.');
  const seconds = parts.length >= 1 ? parseInt(parts[0]) : 0;
  const nanos = parts.length > 1 ? padZeros(parts[1], 9) : '000000000';
  const date = new Date(seconds * 1000).toISOString().slice(0, 20);
  return `${date}${nanos}Z`;
};

const validateDate = (date, _) => {
  if (!DATE_PATTERN.test(date)) {
    throw new InvalidArgumentError(`error: Invalid date ${date}. Expected YYYY-MM-DD`);
  }
  return date;
};

export {parseAccount, toHbars, toIsoString, validateDate};
