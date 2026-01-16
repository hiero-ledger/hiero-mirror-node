// SPDX-License-Identifier: Apache-2.0

class ServiceEndpoint {
  static DOMAIN_NAME = 'domain_name';
  static IP_ADDRESS_V4 = `ip_address_v4`;
  static PORT = 'port';

  constructor(serviceEndpoint) {
    this.domainName = serviceEndpoint.domain_name;
    this.ipAddressV4 = serviceEndpoint.ip_address_v4;
    this.port = serviceEndpoint.port;
  }
}

export default ServiceEndpoint;
