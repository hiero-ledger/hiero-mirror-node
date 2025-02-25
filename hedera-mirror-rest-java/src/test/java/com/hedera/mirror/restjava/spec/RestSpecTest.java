// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec;

import static com.hedera.mirror.restjava.spec.config.SpecTestConfig.REST_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.spec.builder.SpecDomainBuilder;
import com.hedera.mirror.restjava.spec.config.SpecTestConfig;
import com.hedera.mirror.restjava.spec.model.RestSpec;
import com.hedera.mirror.restjava.spec.model.RestSpecNormalized;
import com.hedera.mirror.restjava.spec.model.SpecTestNormalized;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = SpecTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true"})
public class RestSpecTest extends RestJavaIntegrationTest {
    private static final Pattern INCLUDED_SPEC_DIRS = Pattern.compile(
            "^(accounts|accounts/\\{id}/allowances.*|accounts/\\{id}/rewards.*|blocks.*|contracts|network/exchangerate.*|network/fees.*|network/stake.*)$");
    private static final String RESPONSE_HEADER_FILE = "responseHeaders.json";
    private static final int JS_REST_API_CONTAINER_PORT = 5551;
    private static final Path REST_BASE_PATH = Path.of("..", "hedera-mirror-rest", "__tests__", "specs");

    private static final IOFileFilter SPEC_FILE_FILTER = new IOFileFilter() {
        @Override
        public boolean accept(File file) {
            var directory = file.isDirectory() ? file : file.getParentFile();
            var dirName = directory.getPath().replace(REST_BASE_PATH + "/", "");
            return INCLUDED_SPEC_DIRS.matcher(dirName).matches() && !RESPONSE_HEADER_FILE.equals(file.getName());
        }

        @Override
        public boolean accept(File dir, String name) {
            return accept(dir);
        }
    };
    private static final List<Path> SELECTED_SPECS =
            FileUtils.listFiles(REST_BASE_PATH.toFile(), SPEC_FILE_FILTER, TrueFileFilter.INSTANCE).stream()
                    .map(File::toPath)
                    .toList();
    private final ResourceDatabasePopulator databaseCleaner;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final SpecDomainBuilder specDomainBuilder;

    @Autowired
    private DispatcherServletRegistrationBean dispatcherServletRegistration;

    RestSpecTest(
            @Value("classpath:cleanup.sql") Resource cleanupSqlResource,
            DataSource dataSource,
            @Qualifier(REST_API) GenericContainer<?> jsRestApi,
            ObjectMapper objectMapper,
            SpecDomainBuilder specDomainBuilder) {
        this.databaseCleaner = new ResourceDatabasePopulator(cleanupSqlResource);
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.specDomainBuilder = specDomainBuilder;

        var baseJsRestApiUrl =
                "http://%s:%d".formatted(jsRestApi.getHost(), jsRestApi.getMappedPort(JS_REST_API_CONTAINER_PORT));
        this.restClient = RestClient.builder()
                .baseUrl(baseJsRestApiUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com")
                .build();
    }

    @TestFactory
    Stream<DynamicContainer> generateTestsFromSpecs() {
        var dynamicContainers = new ArrayList<DynamicContainer>();
        for (var specFilePath : specsToTest()) {
            RestSpecNormalized normalizedSpec;
            try {
                normalizedSpec = RestSpecNormalized.from(objectMapper.readValue(specFilePath.toFile(), RestSpec.class));
            } catch (IOException e) {
                dynamicContainers.add(dynamicContainer(
                        REST_BASE_PATH.relativize(specFilePath).toString(),
                        Stream.of(dynamicTest("Unable to parse spec file", () -> {
                            throw e;
                        }))));
                continue;
            }

            // Skip tests that require rest application config
            if (normalizedSpec.setup().config() != null) {
                log.info("Skipping spec file: {} (setup not yet supported)", specFilePath);
                continue;
            }

            var normalizedSpecTests = normalizedSpec.tests();
            for (var test : normalizedSpecTests) {
                var testCases =
                        test.urls().stream().map(url -> dynamicTest(url, () -> testSpecUrl(url, test, normalizedSpec)));

                dynamicContainers.add(dynamicContainer(
                        "%s: '%s'".formatted(REST_BASE_PATH.relativize(specFilePath), normalizedSpec.description()),
                        Stream.concat(
                                Stream.of(dynamicTest("Setup database", () -> setupDatabase(normalizedSpec))),
                                testCases)));
            }
        }
        return Stream.of(dynamicContainer(
                "Dynamic test cases from spec files, base: %s".formatted(REST_BASE_PATH), dynamicContainers));
    }

    private List<Path> specsToTest() {
        return SELECTED_SPECS;
    }

    private void setupDatabase(RestSpecNormalized normalizedRestSpec) {
        /*
         * JUnit 5 dynamic tests do not enjoy the benefit of lifecycle methods (e.g. @BeforeEach etc.), so
         * the DB needs to first be cleaned explicitly prior to creating the spec file defined entities.
         */
        databaseCleaner.execute(dataSource);

        specDomainBuilder.customizeAndPersistEntities(normalizedRestSpec.setup());
    }

    @SneakyThrows
    private void testSpecUrl(String url, SpecTestNormalized specTest, RestSpecNormalized spec) {

        var response = restClient
                .get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // Override default handling of 4xx errors, and proceed to evaluate the response.
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    // Override default handling of 5xx errors, and proceed to evaluate the response.
                })
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(specTest.responseStatus());
        JSONAssert.assertEquals(specTest.responseJson(), response.getBody(), JSONCompareMode.LENIENT);
    }
}
