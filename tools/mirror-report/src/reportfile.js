// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import {log} from './logger.js';

export class ReportFile {
  static HEADER = 'timestamp,sender,receiver,fees,amount,balance,hashscan\n';

  constructor() {
    this.data = [];
  }

  append(text) {
    this.data.push(text);
  }

  write(filename) {
    if (this.data.length > 0) {
      const text = ReportFile.HEADER + this.data.sort().join('');
      fs.writeFileSync(filename, text);
      log(`Generated report successfully at ${filename} with ${this.data.length} entries`);
      this.data = [];
    }
  }
}
