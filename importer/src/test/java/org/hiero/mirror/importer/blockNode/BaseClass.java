package org.hiero.mirror.importer.blockNode;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.hiero.block.api.protoc.BlockAccessServiceGrpc;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.mirror.common.config.CommonTestConfiguration.FilteringConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public class BaseClass {
    protected static GenericContainer<?> blockNodeContainer;
    protected static PostgreSQLContainer<?> dbContainer;
    protected static GenericContainer<?> importerContainer;
    protected static GenericContainer<?> simulatorContainer;
    protected static Network network;
    /** Container running the Block Node Application */

    /** Port that is used by the Block Node Application */
    protected static int blockNodePort;
    protected static int dbPort;

    /** Port that is used by the Block Node Application for metrics */
    protected static int blockNodeMetricsPort;

    /** gRPC channel for connecting to Block Node */
    protected static ManagedChannel channel;

    /** gRPC client stub for BlockAccessService */
    protected static BlockAccessServiceGrpc.BlockAccessServiceBlockingStub blockAccessStub;

    /** gRPC client stub for BlockNodeService */
    protected static BlockNodeServiceGrpc.BlockNodeServiceBlockingStub blockServiceStub;

    private static final String SIMULATOR_IMAGE_NAME = "local/block-stream-simulator";



    public BaseClass() {
        // No additional setup required
    }

    /**
     * Setup method to be executed before each test.
     *
     * <p>This method initializes the Block Node server container using Testcontainers.
     */
    @BeforeEach
    public void setup() {
        network = Network.newNetwork();
        dbContainer = createDBContainer();
        dbContainer.start();
        blockNodeContainer = createBlockNodeContainer();
        blockNodeContainer.start();
//        blockAccessStub = initializeBlockAccessGrpcClient();
//        blockServiceStub = initializeBlockNodeServiceGrpcClient();
        simulatorContainer = createSimulatorContainer();
        simulatorContainer.start();
        importerContainer = createImporterContainer();
        importerContainer.start();

//        blockAccessStub = initializeBlockAccessGrpcClient();
//        blockServiceStub = initializeBlockNodeServiceGrpcClient();
    }

    protected static GenericContainer<?> createImporterContainer() {
//        Path dockerContext = Paths.get("../../../../../../../../../../../Mirror-node-HELPER/hiero-mirror-node/importer");
        importerContainer = new GenericContainer<>("gcr.io/mirrornode/hedera-mirror-importer:latest")
//        importerContainer = new GenericContainer<>(
//                new ImageFromDockerfile()
//                        .withFileFromPath(".", dockerContext) // build context root (e.g., Dockerfile + build/libs/*.jar in this dir)
//                        .withBuildArg("VERSION", "latest") // optional build args
//        )
                .withExposedPorts(8080, 5005)
                .withEnv("JAVA_TOOL_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
                .withNetwork(network)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/mirror_node")
                .withEnv("SPRING_DATASOURCE_USERNAME", dbContainer.getUsername())
                .withEnv("SPRING_DATASOURCE_PASSWORD", dbContainer.getPassword())
                .withEnv("HIERO_MIRROR_IMPORTER_BLOCK_ENABLED", "true")
                .withEnv("HIERO_MIRROR_IMPORTER_BLOCK_SOURCETYPE", "BLOCK_NODE")
                .withEnv("HIERO_MIRROR_IMPORTER_DOWNLOADER_RECORD_ENABLED", "false")
                .withEnv("HIERO_MIRROR_IMPORTER_DOWNLOADER_BALANCE_ENABLED", "false")
                .withEnv("HIERO_MIRROR_IMPORTER_BLOCK_NODES_0_HOST", "block-node")
                .withEnv("HIERO_MIRROR_IMPORTER_BLOCK_NODES_0_PORT", String.valueOf(blockNodeContainer.getMappedPort(8080)))
                .withEnv("HIERO_MIRROR_IMPORTER_BLOCK_NODES_0_PRIORITY", "0")
                .withEnv("HIERO_MIRROR_IMPORTER_DOWNLOADER_BUCKETNAME", "test")
                .withEnv("HIERO_MIRROR_IMPORTER_IMPORTHISTORICALACCOUNTINFO", "false")
                .withEnv("HIERO_MIRROR_IMPORTER_MIGRATION_ASYNC_ENABLED", "false")
                .withEnv("HIERO_MIRROR_IMPORTER_MIGRATION_DUMMYMIGRATION_CHECKSUM", "5")
                .withEnv("HIERO_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_PERSIST_PENDINGREWARD", "false")
                .withEnv("HIERO_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_REDIS_ENABLED", "true")
                .withEnv("HIERO_MIRROR_IMPORTER_PARSER_RECORD_RETRY_MAXATTEMPTS", "2")
                .withEnv("HIERO_MIRROR_IMPORTER_STARTDATE", "1970-01-01T00:00:00Z")
                .withEnv("HIERO_MIRROR_IMPORTER_TEST_PERFORMANCE_DOWNLOADER_ENABLED", "true")
                .withEnv("HIERO_MIRROR_IMPORTER_TEST_PERFORMANCE_DOWNLOADER_SCENARIO", "simple")
                .withEnv("HIERO_MIRROR_IMPORTER_TEST_PERFORMANCE_PARSER_ENABLED", "true")
                .withEnv("HIERO_MIRROR_IMPORTER_TEST_PERFORMANCE_PARSER_SCENARIO", "simple")
                .withCreateContainerCmdModifier(cmd -> cmd.withName("importer"))
                .waitingFor(Wait.forHttp("/actuator/health/liveness").forStatusCode(200))
                .dependsOn(dbContainer, blockNodeContainer);

        return importerContainer;
    }

    protected static GenericContainer<?> createSimulatorContainer() {
        simulatorContainer = new GenericContainer<>("hedera-block-simulator:latest")
                .withNetwork(network)
                .withNetworkAliases("simulator")
                .withEnv("BLOCK_NODE_HOST", "block-node")  // Use Docker alias of block node
                .withEnv("BLOCK_NODE_PORT", "8080")
                .withCreateContainerCmdModifier(cmd -> cmd.withName("simulator"))
                .dependsOn(blockNodeContainer);
//                .waitingFor(Wait.forLogMessage(".*Sending blocks.*", 1));

        return simulatorContainer;
    }

    protected static GenericContainer<?> createBlockNodeContainer() {
        String blockNodeVersion = "0.15.0-SNAPSHOT";
        blockNodePort = 8080;
        blockNodeMetricsPort = 9999;
        List<String> portBindings = new ArrayList<>();
        portBindings.add(String.format("%d:%2d", blockNodePort, blockNodePort));
        portBindings.add(String.format("%d:%2d", blockNodeMetricsPort, blockNodeMetricsPort));
        blockNodeContainer = new GenericContainer<>(DockerImageName.parse("block-node-server:" + blockNodeVersion))
                .withExposedPorts(blockNodePort)
                .withEnv("VERSION", blockNodeVersion)
                .waitingFor(Wait.forListeningPort())
                .withNetwork(network)
                .withNetworkAliases("block-node")
                .withCreateContainerCmdModifier(cmd -> cmd.withName("block-node"))
                .waitingFor(Wait.forHealthcheck());

        blockNodeContainer.setPortBindings(portBindings);
        return blockNodeContainer;
    }

    protected static PostgreSQLContainer<?> createDBContainer() {
        var dockerImageName = DockerImageName.parse("postgres:16-alpine");
        var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
        var excluded = "terminating connection due to unexpected postmaster exit";
        var logConsumer = new FilteringConsumer(
                new Slf4jLogConsumer(logger, true),
                o -> !StringUtils.contains(o.getUtf8StringWithoutLineEnding(), excluded));
        dbPort = 5432;
        List<String> portBindings = new ArrayList<>();
        portBindings.add(String.format("%d:%2d", dbPort, dbPort));
        dbContainer = new PostgreSQLContainer<>(dockerImageName)
                .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_WRITE)
                .withDatabaseName("mirror_node")
                .withNetwork(network)
                .withLogConsumer(logConsumer)
                .withPassword("mirror_node_pass")
                .withCreateContainerCmdModifier(cmd -> cmd.withName("db"))
                .withUsername("mirror_node")
                .withNetworkAliases("postgres");;
        dbContainer.setPortBindings(portBindings);
        return dbContainer;
    }

    ImageFromDockerfile image = new ImageFromDockerfile(SIMULATOR_IMAGE_NAME, false)
            .withDockerfile(Paths.get("importer/src/test/resources/block-node-images/SimulatorDockerfile"))
            .withFileFromPath(".", Paths.get("."));


    private static String getBlockNodeVersion() {
        String version = System.getProperty("block.node.version");
        if (version == null) {
            throw new IllegalStateException(
                    "block.node.version system property is not set. This should be set by Gradle.");
        }
        return version;
    }

    /**
     * Initializes the gRPC client for connecting to the Block Node with BlockAccessStub for requesting single blocks.
     */
    protected static BlockAccessServiceGrpc.BlockAccessServiceBlockingStub initializeBlockAccessGrpcClient() {
        String host = blockNodeContainer.getHost();
        int port = blockNodePort;

        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // For testing only
                .build();

        return BlockAccessServiceGrpc.newBlockingStub(channel);
    }

    protected static BlockNodeServiceGrpc.BlockNodeServiceBlockingStub initializeBlockNodeServiceGrpcClient() {
        final String host = blockNodeContainer.getHost();
        final int port = blockNodePort;
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        return BlockNodeServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void teardown() throws InterruptedException {
        if (blockNodeContainer != null) {
            blockNodeContainer.stop();
            blockNodeContainer.close();
        }
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

}

