// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.downloader.record.StreamFileWriter.SigningConsensusNode;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.test.performance.PerformanceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.EnabledIf;

@CustomLog
@EnabledIf(expression = "${hiero.mirror.importer.test.performance.downloader.enabled}", loadContext = true)
@RequiredArgsConstructor
@Tag("performance")
@TestPropertySource(properties = "hiero.mirror.importer.downloader.cloudProvider=LOCAL")
class RecordFileDownloaderPerformanceTest extends ImporterIntegrationTest {

    // These keys are only used in tests and not copied from any public environment
    private static final String PRIVATE_KEY =
            "308206fb020100300d06092a864886f70d0101010500048206e5308206e10201000282018100c868c85a4cabdeaa5c228a5f3eed3d40557f319eb4b55f6dfc5191083fd19f1a63c1c45f4d352ec548d2d420c88e86e70858a0a4fb9db6eb05597ce5d6a76769317af320e9b3dda430eac3cca8763409a955d24343029468cf6d75d9ea6dd6815be1bc9f07ec77c7f3e72d5fffb83d81710ca8bdeafcb8c5a2df2e0562b692dd52744d65fc102f791480eb676d2640157e0a8282d9ce7cc4259a2b7b6a74d64b5d727c0e3eaaaf4dd05b771b1b8c52c91497b5fe113ef7999abc2dce0807bb1facc63b77d35215c923c6d0f7556d6222e80d764ddf8dbe5814a43578e6fd746614cc138658a84027b5b4da117c3c8ab1ade577bfd6e8a268cca2fab31f4af3a0a79fc6f58391d481fc10c923da4abee5a348c75270fbbb742bacb3528620a03cf56055fa05fed0ec3c329e4c5447a058b40dbbef38433ed227376dce28e697a7612900478ae512a1b07620502e033ca677a30a15842b405610785c4f9b8b390a8ad915384e35d645d39248f6ecfaa24615a62aa4065895fcf492326aa0beb47702030100010282017f1f451dc4f9319f291237350aa77fceab495681206cf56e191ec59b5b9feeea9d41ba96a24822a0a99988a13b5fd6dd522f25ddeafcad02b2e1f822cbf2dcf31b1339d4d23fa4df3794a345cd6209f314ab491c5b0d0b68392b40dadb89389a31788840661e39f204dd6d430ac5cd96041c74eb6d9a4b0d6d1e6042a35b9904c32e7df723e45b4cd417a605f89d5668c5d5485cbf7fcae2e5b814b9b42484a4b2569b3f4e4f0719fdfc04bb49c69f104bc1873fd01da7e033421c37096ec58d259d6632ee90d98d5b5826b4e6e1c7f33eb93c16a19044d96436897b804adbdcc7312995d58312007cf3068a683e549f834be281a9db595c7eae40472a9f27de1036f3ecd1c0fc92c82be219be7330b9bd49975384b875cfc6979d6ad69703bcfcb341bb4c962da6833bd1f04b204b2171d481591acd383724cc2434597aebcf5fe8e94d8dfbdb11fb3b7f6e82f72d76337f4189c3a6c6666253b7cc26e57377b0bec3600fec4d8cfebfbbb15d40ed7a6bff1fb1783c1d2111e9588375d20c4d0281c100dfb67f62cb0c0aa3597074425b11bfe56c8f7e251f1a27fb8ba54385e03a5d5d413c9a689d1d0aebe4c0f7b69901d4b0e8c95d6a0559f8ecc12fe319d6dbe2e957c95c13f64a54ef9df6fdfe5b68a9790fa1c419e815e8c97bfe3ddfd4b763d3a70c6e6173e760800408c69f54a255ba57d1dedfaa9dd7030d70a2d00261dcc5ee15d6c0337b26bc042c8d40b0218cf8eae2e4d2707a9ab23ab1a1b492aeba1c52edc07be012b8068b3f625e2bc0223ddb42831dbf4e861c8c8eba0fafce5c4b0281c100e5554a290df1f8bba379ff14e6509a9895f83b3e90ab5599bba83d8b141f06d8c4467c57db7d3b9fd2a542eb67396e042256b0a2887c87978c30104b3d1c8803562490a88159570a9161c0b7dd4d32b6e8690b105b75d127905673f7cf31b9bae13cd792421fe3a096e075bf1914837c13356184cd6fe385ac10d6cce6799cdb6968acc5f2deb72d6f8952e95748b1e115bf904d9c848600058b89f2ce2a467aeb4f524b6e4a186ba5ed16651d5ad6cdff592e3979c6926ffd61f2a7d7f655050281c023d5d5fdfb4c887fb619ff6589b5042a3a039a4f53f61aa57eaf106be78931df784dbee63714a9e533957d98b055ccbe31ebdf9cd6129d7f3f4aee73fb28e9e63afbb45636439deb6c405e5b5a451fb096c270e93d7614fe0170ae74a65c620f4b59006d77e57e5dc347ac99653343cc3fb90c9c8376193511f812fc83052e1a3e931cfd58c1c768a2ba883dee78d15e26320639776f0c4cb47e33cafb1c260fd770e23e759c4a0232580ae7ced64e25de737f114acdc749d7721d77d9cf92950281c04625966f845275307fca7b199b71203877f6dbb8416c06dce5278adb95a5ffe421f52567823d861556cb31f2eb3a07cabfee204d36ce50732c702f2bb45f9bc2d98089b6e109c0b3fcd017b0a5c24d36e153f00c6acd58d26f35e276f42b539233fef639487c9495b450a7f371ea72656b42b2b77a573512d814b67f2a281cb088477a417a0e619d46368ce3ceccef8bd7a926ac76a99a8b3b26f9650966a8f44431990c7589b87a84e0462f5b91438ef302063f925e08c0b4925734bbbc1f390281c01bffcfb3fa9f1dac7f1ed22d25f91f7327faf5c670059de286d292f00d9af48499e167189bdfd967cff53e9cd90038f42cd91215e97e5c645d4b5e3312bfbb70b49c7009f64773b9372be876b015c8f84b216ace588717144a7253b63bd50fd20ebda5e16d4115cd3ef4dc23a480d4b5af451055e32abe94b3a4471aa26072a448a1ece5be87650b482bd68a8dbb7836d03e2810248487d32765d61f044a2a701d4a26fc1402b34222f78e82e973e963c60320dd960a9e274cbad2b1f26587f8";
    private static final String PUBLIC_KEY =
            "308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c868c85a4cabdeaa5c228a5f3eed3d40557f319eb4b55f6dfc5191083fd19f1a63c1c45f4d352ec548d2d420c88e86e70858a0a4fb9db6eb05597ce5d6a76769317af320e9b3dda430eac3cca8763409a955d24343029468cf6d75d9ea6dd6815be1bc9f07ec77c7f3e72d5fffb83d81710ca8bdeafcb8c5a2df2e0562b692dd52744d65fc102f791480eb676d2640157e0a8282d9ce7cc4259a2b7b6a74d64b5d727c0e3eaaaf4dd05b771b1b8c52c91497b5fe113ef7999abc2dce0807bb1facc63b77d35215c923c6d0f7556d6222e80d764ddf8dbe5814a43578e6fd746614cc138658a84027b5b4da117c3c8ab1ade577bfd6e8a268cca2fab31f4af3a0a79fc6f58391d481fc10c923da4abee5a348c75270fbbb742bacb3528620a03cf56055fa05fed0ec3c329e4c5447a058b40dbbef38433ed227376dce28e697a7612900478ae512a1b07620502e033ca677a30a15842b405610785c4f9b8b390a8ad915384e35d645d39248f6ecfaa24615a62aa4065895fcf492326aa0beb4770203010001";

    private final ConsensusNodeService consensusNodeService;
    private final RecordFileDownloader downloader;
    private final ImporterProperties importerProperties;
    private final PerformanceProperties performanceProperties;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordFileRepository recordFileRepository;
    private final StreamFileWriter streamFileWriter;

    @TempDir
    private Path dataPath;

    private Collection<SigningConsensusNode> nodes;

    @BeforeEach
    void setup() throws Exception {
        importerProperties.setDataPath(dataPath);
        importerProperties.setNodePublicKey(PUBLIC_KEY);
        var keySpec = new PKCS8EncodedKeySpec(Hex.decodeHex(PRIVATE_KEY));
        var keyFactory = KeyFactory.getInstance("RSA");
        var privateKey = keyFactory.generatePrivate(keySpec);
        consensusNodeService.refresh();
        nodes = consensusNodeService.getNodes().stream()
                .map(n -> new SigningConsensusNode(n, privateKey))
                .toList();
    }

    @Test
    void scenarios() {
        var previous = recordFileRepository.findLatest().orElse(null);
        var properties = performanceProperties.getDownloader();
        var scenarioName = properties.getScenario();
        var scenarios = performanceProperties.getScenarios().getOrDefault(scenarioName, List.of());

        for (var scenario : scenarios) {
            if (!scenario.isEnabled()) {
                log.info("Scenario {} is disabled", scenario.getDescription());
                continue;
            }

            log.info("Executing scenario: {}", scenario);
            long interval = StreamType.RECORD.getFileCloseInterval().toMillis();
            long duration = scenario.getDuration().toMillis();
            long startTime = System.currentTimeMillis();
            long endTime = startTime;
            var stats = new SummaryStatistics();
            var stopwatch = Stopwatch.createStarted();
            var builder = recordFileBuilder.recordFile();

            scenario.getTransactions().forEach(p -> {
                int count = (int) (p.getTps() * interval / 1000);
                builder.recordItems(i -> i.count(count)
                        .entities(p.getEntities())
                        .entityAutoCreation(true)
                        .subType(p.getSubType())
                        .type(p.getType()));
            });

            while (endTime - startTime < duration) {
                var recordFile = builder.previous(previous).build();
                long startNanos = System.nanoTime();
                streamFileWriter.write(recordFile, nodes);
                stats.addValue(System.nanoTime() - startNanos);
                previous = recordFile;

                downloader.download();

                long sleep = interval - (System.currentTimeMillis() - endTime);
                if (sleep > 0) {
                    Uninterruptibles.sleepUninterruptibly(sleep, TimeUnit.MILLISECONDS);
                }
                endTime = System.currentTimeMillis();
            }

            long mean = (long) (stats.getMean() / 1_000_000.0);
            log.info(
                    "Scenario {} took {} to process {} files for a mean of {} ms per file",
                    scenario.getDescription(),
                    stopwatch,
                    stats.getN(),
                    mean);
            assertThat(Duration.ofMillis(mean))
                    .as("Scenario {} had a latency of {} ms", scenario.getDescription(), mean)
                    .isLessThanOrEqualTo(properties.getLatency());
        }
    }
}
