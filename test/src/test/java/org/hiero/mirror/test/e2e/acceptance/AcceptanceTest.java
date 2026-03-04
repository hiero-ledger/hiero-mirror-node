// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance;

import io.cucumber.java.ParameterType;
import io.cucumber.spring.CucumberContextConfiguration;
import org.hiero.mirror.rest.model.TokenInfo.PauseStatusEnum;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import org.hiero.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import org.junit.jupiter.api.Tag;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@SpringBootTest(properties = "spring.main.banner-mode=off")
@CucumberContextConfiguration
@SuppressWarnings("java:S2187") // Ignore no tests in file warning
@Tag("acceptance")
public class AcceptanceTest {

    @ParameterType("\"?(([A-Z]+)(_[A-Z]+)*)\"?")
    public AccountNameEnum account(String name) {
        return AccountNameEnum.valueOf(name);
    }

    @ParameterType("[Tt][Rr][Uu][Ee]|[Ff][Aa][Ll][Ss][Ee]")
    public boolean bool(String value) {
        return Boolean.parseBoolean(value);
    }

    @ParameterType("\"?(([A-Z]+)(_[A-Z]+)*)\"?")
    public PauseStatusEnum pauseStatus(String name) {
        return PauseStatusEnum.valueOf(name);
    }

    @ParameterType("\"?(([A-Z]+)(_[A-Z]+)*)\"?")
    public TokenNameEnum token(String name) {
        return TokenNameEnum.valueOf(name);
    }

    @ParameterType("\"?([A-Z]+)\"?")
    public NodeNameEnum node(String name) {
        return NodeNameEnum.valueOf(name);
    }
}
