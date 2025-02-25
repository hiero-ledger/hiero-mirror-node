// SPDX-License-Identifier: Apache-2.0

import {Logging} from '@google-cloud/logging';

class Downloader {
  #completed = false;
  #filter;
  #projectId;
  #sinks;

  constructor(filter, projectId, ...sinks) {
    this.#filter = filter;
    this.#projectId = projectId;
    this.#sinks = sinks;
  }

  async download() {
    if (this.#completed) {
      return;
    }

    log(`Downloading logs for project '${this.#projectId}' with filter '${this.#filter}'`);

    const logging = new Logging({projectId: this.#projectId});
    let pageToken = undefined;
    let count = 0;
    let requestIndex = 1;
    const sinks = this.#sinks;

    while (true) {
      const request = {
        filter: this.#filter,
        orderBy: 'timestamp asc',
        pageSize: 100000,
        pageToken,
        resourceNames: [`projects/${this.#projectId}`],
      };
      const response = (await logging.getEntries(request))[2];
      const entries = response.entries;
      pageToken = response.nextPageToken;

      entries.forEach((entry) => sinks.forEach((sink) => sink.accept(entry.textPayload)));

      count += entries.length;
      log(`Request #${requestIndex++} - downloaded ${entries.length} entries`);

      if (!pageToken) {
        break;
      }
    }

    sinks.forEach((sink) => sink.close());
    this.#completed = true;
  }
}

export default Downloader;
