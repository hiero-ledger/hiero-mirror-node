// SPDX-License-Identifier: Apache-2.0

const maxWorkers = process.env.CI ? 2 : '50%'; // 2 workers in CI and 50% of cores number of works in local envs
const config = {
  collectCoverage: true,
  coverageDirectory: 'build/coverage/',
  coveragePathIgnorePatterns: [
    '<rootDir>/build/',
    '<rootDir>/check-state-proof/',
    '<rootDir>/monitoring/',
    '<rootDir>/node_modules/',
    '<rootDir>/__tests__/',
  ],
  globalSetup: './__tests__/globalSetup.js',
  globalTeardown: './__tests__/globalTeardown.js',
  maxWorkers,
  reporters: [['github-actions', {silent: false}], 'jest-junit', ['summary', {summaryThreshold: 0}]],
  setupFilesAfterEnv: ['./__tests__/jestSetup.js'],
  testEnvironment: 'node',
  testPathIgnorePatterns: ['/build/', '/node_modules/'],
  testRegex: '/__tests__/.*\\.test\\.js$',
  verbose: true,
};

export default config;
