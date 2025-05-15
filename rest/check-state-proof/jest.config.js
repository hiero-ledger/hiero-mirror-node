// SPDX-License-Identifier: Apache-2.0

const maxWorkers = process.env.CI ? 2 : '50%'; // 2 workers in CI and 50% of cores number of works in local envs
const config = {
  collectCoverage: true,
  coverageDirectory: 'build/coverage/',
  coveragePathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/output/', '<rootDir>/sample/', '<rootDir>/tests/'],
  maxWorkers,
  reporters: ['jest-standard-reporter', 'jest-junit'],
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.test.js'],
  verbose: true,
};

export default config;
