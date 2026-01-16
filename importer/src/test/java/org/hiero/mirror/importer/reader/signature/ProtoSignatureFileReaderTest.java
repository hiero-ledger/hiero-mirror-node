// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Bytes;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtoSignatureFileReaderTest extends AbstractSignatureFileReaderTest {

    private static final String FILENAME = "2022-06-16T10_18_37.496046001Z.rcd_sig";

    private static final byte VERSION = 6;

    private final ProtoSignatureFileReader protoSignatureFileReader = new ProtoSignatureFileReader();

    private static Stream<Arguments> testReadValidFileArgumentProvider() {
        return Stream.of(
                Arguments.of(
                        "2022-06-16T10_18_37.496046001Z.rcd_sig",
                        "3e546619bd9c59fe0ee03be25ff2371718ff206f31323868207bb621bc85212669eb66b8a1556d4e686c8fbcd14f9e97",
                        "9147ae3132a356a816e2d66dff3ccfcfa00af49c827468a47c5a66cf9ca4f3352832b5561085c55e2be7807d99ccbe2475e277c3b6f1334b57d350a57986f0ccace2056a0f14a258ad2bc54d9225ef56ef2c1ff2c7a614448c8dbd2c4227afe8784fff08a8cd7c78ac64e5544671c06b24a9f163b8c5a993eb3efdc6fb729964521d907e7fcce4fd22fbed9dd2a9522b2b4c4cd1cb5350f16c076bd43ca3f6fd61a73c852e9af4d120ffb3d3996810dfa8c5a1c9c71aff3b8c9a24d541ee361e35b8b6debd57a19492a56e57bb85b600e818d1336e71a1374eb17f457260d8df99b1b22b725aad01ff6787847c14b9b5578ff75a97f5d645a6ffb9016b55d18f595d4dcf3aec8c504f3996030d4295b271300ea9171c3375c1291e5725a548c8ac0b571984f17ad7a07bf17a4681bf67d6099271cb2e80d7444f6a7aace3e575af5bed2f02ba99f16f432a7b4ac2cca784f3694fe36085c9c17a3464c0759a2cae9109bf5e36fb138f17382d532ecac19889ac0c360c663847b93e3989004823",
                        "01a43036a6ce80082680fa64b4702aecb659b8ddd02a06201c6c83d2b71def7b2ee1b5ac09bb7eb9b2f04dad5547e8e9",
                        "867d0c076f947c3f1cf33f2e56894006572a51e9a984c037b436fa9fcd1f50bee6114c486bacc21cec80492709b0aa3a2edcca4c3f9a4efc2143f4a567fcf7e0f72a17d147e24c48968faa455b8b649b0c00c5525d5efd48839f7fe1c53c61bb6d2cfe62ed23a41be9f5a038f8932e960926752100f1cdb874b72d3b0ceb419020ddc9093316d482138a0d64cf008cad3069d059f342c9a9a5936c130bc0c7248e65dd3fd99b9fbe828df6990e1754790e3c8846d8ababc99a92b2ec61b53787ab374559b806fa00f4de07f1d1b78282cd9fbf4f8268e80d21856300d319ab149cb4bf1eb3693567be415bea5a8963e98079a299c5f920537ad2983d201ee5115892bafe0c79d3a88675e4398851acfd15ee8e240f4282b253f6cab70b90c58277c30db68839982e28613e338329a42fd4a60eb20dda183ce1dd9ae250e191debd8bc1a8df9cfe4980aca7ebe3cc2b55e7bd799516d70b86420e68532799759e2f865f1ee155cea5492800a7e8b7cec16c974ca838998b350e2499d416d6687c"),
                Arguments.of(
                        "2022-06-16T10_18_40.720801003Z.rcd_sig",
                        "b5947d59a5661f591c0950e8dd0c6a4d42f49dcf7454bc05c911fef67aedead41fb00f01ac32b1de9ac2b3130fb14346",
                        "8c23d928ae9a0986e1398fc58af59f0081a025e7438eddea8768e6c84171970fd874d9862fc06207ee0f3a049d6ae1b9daca4d568850830c39b99ac35e24e00fd59e8c01ed37891989b0988e38de4e1f9c55f8cc8d7b9fdeeb3f3c8eb91e85332fe57b7a6bb59a404b035ee01cd472a111a94edc864ce9c561e3176cb1b0cd54e905e5e82909ee346eb32e1869a0ac0eea39122ba4d17eb9eae63569676fe3345f2ba350a38781e36b973fd47ba4587f7443007f0b11453fd4da94b6bd7368b1df73aeb5897252d63cd6780ef8ccc54f9baeb772aa06d06530433f4357a578acfbe63ed487d75505bf7ded25661c8fa8d94d97cf8914caab41cfbec48160a647980063796e643c118aa45a5044beed44f8e6ace59796679cfb7ecbfaac954886ba4a10f6761d9abc45edf12b11b5e45f88c3a9dcbb1943b6c1d4eacdb47d8917246d2cadac4653138f92aa307dcfdb6b9c58350c04ae692cf029d7c8bf66e48ff292028fc09eddd89e9ea833e52ea9dbe0f8d2438fe65df37abb1b1b1c6469c4",
                        "c8766bf8449e2d4598a4b7cc41c7a54f16dc869bd9cf81af2536ba1a8ec8c86d1e33134995d45f67ce26a79249d96666",
                        "73e629389b23202311e62c11a5a740ff6dd6deff2402c1427f892a8cf1bf863933eb2b6ad39e4208bbdb241bb2bda107b4adcd72a9aff4da2f9ae5495e521a029b56a7bfc219fd81e474160adb6a551f76fccf46554c1f611ad72d27b141e3a6a206d9600296fe7c0f6c0b9a4c2fde8d26b609520576bb7b3aa1ee2e766f21ca5cb835f5b8cbdde63e3660634f56fb318fdb3318d6159c31894dfd1f732c79c4c85fb6ff59ab50ac2ce56cbcc8d14450cda030ef7c83871b86477f7cc10ce8ea44c9c5f97530bd2fdccadf87435ce9dd55cc7cc39b1382db8566d4d77ae7e3444303a8e6712bca05f7571679a7c9483532b3ca22335584cb5ded47ad2622ad19264a1666e66490dccab7996136336c0191a4472a6e570bb4167c2a0183dea146717ec302a8739975e6a175ee1bb7884d6584dba30492f8fd695b3207f6f5d02f3b4f3f2ad48160336bbf2b633b4d6cab0a0364bc251becade29e52877d8b172cb1fa8e7e3bdb56eeff9eaf77e37ce44cfdbbc32c643308720c5e4e5d37074682"));
    }

    @ParameterizedTest
    @MethodSource("testReadValidFileArgumentProvider")
    void testReadValidFile(
            String filename,
            String entireFileHashAsHex,
            String entireFileSignatureHex,
            String metadataHashAsHex,
            String metadataSignatureAsHex) {
        var signatureFile = TestUtils.getResource(
                Path.of("data", "signature", "v6", filename).toString());
        var streamFileData = StreamFileData.from(signatureFile);
        var streamFileSignature = protoSignatureFileReader.read(streamFileData);

        assertThat(streamFileSignature)
                .isNotNull()
                .returns(streamFileData.getBytes(), StreamFileSignature::getBytes)
                .returns(filename, s -> s.getFilename().toString())
                .returns(entireFileHashAsHex, StreamFileSignature::getFileHashAsHex)
                .returns(entireFileSignatureHex, f -> DomainUtils.bytesToHex(f.getFileHashSignature()))
                .returns(metadataHashAsHex, StreamFileSignature::getMetadataHashAsHex)
                .returns(metadataSignatureAsHex, f -> DomainUtils.bytesToHex(f.getMetadataHashSignature()))
                .returns(StreamFileSignature.SignatureType.SHA_384_WITH_RSA, StreamFileSignature::getSignatureType)
                .returns(VERSION, StreamFileSignature::getVersion);
    }

    @Test
    void testReadFileEmpty() {
        var streamFileData = StreamFileData.from(FILENAME, new byte[] {});
        var exception =
                assertThrows(InvalidStreamFileException.class, () -> protoSignatureFileReader.read(streamFileData));
        assertThat(exception.getMessage()).contains(FILENAME);
    }

    @Test
    void testReadFileEmptyFileSignature() {
        var bytes = ProtoSignatureFile.of(SignatureFile.Builder::clearFileSignature);
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var exception =
                assertThrows(InvalidStreamFileException.class, () -> protoSignatureFileReader.read(streamFileData));
        var expected = String.format("The file %s does not have a file signature", FILENAME);
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void testReadFileEmptyMetadataSignature() {
        var bytes = ProtoSignatureFile.of(SignatureFile.Builder::clearMetadataSignature);
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var exception =
                assertThrows(InvalidStreamFileException.class, () -> protoSignatureFileReader.read(streamFileData));
        var expected = String.format("The file %s does not have a file metadata signature", FILENAME);
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void testReadFileSignatureTypeUnknown() {
        var bytes = ProtoSignatureFile.of(b -> {
            b.getFileSignatureBuilder().setType(SignatureType.SIGNATURE_TYPE_UNKNOWN);
            b.getMetadataSignatureBuilder().setType(SignatureType.SIGNATURE_TYPE_UNKNOWN);
            return b;
        });
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var exception =
                assertThrows(InvalidStreamFileException.class, () -> protoSignatureFileReader.read(streamFileData));
        assertThat(exception.getMessage()).contains(FILENAME);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadFileWrongVersion() {
        var bytes = ProtoSignatureFile.of((byte) 5);
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var exception =
                assertThrows(InvalidStreamFileException.class, () -> protoSignatureFileReader.read(streamFileData));
        var expected = String.format("Expected file %s with version 6, got 5", FILENAME);
        assertEquals(expected, exception.getMessage());
    }

    private static class ProtoSignatureFile {

        private static final byte VERSION = 6;

        public static byte[] of() {
            return of(VERSION);
        }

        private static byte[] of(byte version) {
            return of(version, Function.identity());
        }

        private static byte[] of(Function<SignatureFile.Builder, SignatureFile.Builder> customizer) {
            return of(VERSION, customizer);
        }

        private static byte[] of(byte version, Function<SignatureFile.Builder, SignatureFile.Builder> customizer) {
            return Bytes.concat(
                    new byte[] {version},
                    customizer.apply(getDefaultSignatureFileBuilder()).build().toByteArray());
        }

        private static SignatureFile.Builder getDefaultSignatureFileBuilder() {
            var hashObject =
                    HashObject.newBuilder().setAlgorithm(HashAlgorithm.SHA_384).setLength(48);
            var signatureObject = SignatureObject.newBuilder()
                    .setType(SignatureType.SHA_384_WITH_RSA)
                    .setLength(384);
            return SignatureFile.newBuilder()
                    .setFileSignature(signatureObject
                            .setSignature(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(384)))
                            .setHashObject(hashObject
                                    .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                                    .build())
                            .build())
                    .setMetadataSignature(signatureObject
                            .setSignature(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(384)))
                            .setHashObject(hashObject
                                    .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                                    .build())
                            .build());
        }
    }
}
