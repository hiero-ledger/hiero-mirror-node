// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import com.google.protobuf.BytesValue;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.util.DomainUtils;

@UtilityClass
public class TestRecordFiles {

    private static final String INITCODE =
            "60806040526008600055348015601457600080fd5b5060a1806100236000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063086949b7146044575b600080fd5b348015604f57600080fd5b506056606c565b6040518082815260200191505060405180910390f35b600060079050905600a165627a7a723058202e097bbe122ad5d86e840be60aab41d160ad5b86745aa7aa0099a6bbfc2652180029";

    private static final String RUNTIME_BYTECODE =
            "608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063086949b7146044575b600080fd5b348015604f57600080fd5b506056606c565b6040518082815260200191505060405180910390f35b600060079050905600a165627a7a723058202e097bbe122ad5d86e840be60aab41d160ad5b86745aa7aa0099a6bbfc2652180029";

    @SneakyThrows
    public Map<String, RecordFile> getAll() {
        var digestAlgorithm = DigestAlgorithm.SHA_384;

        var recordFileVersionOneOne = RecordFile.builder()
                .consensusStart(1561990380317763000L)
                .consensusEnd(1561990399074934000L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a")
                .hash(
                        "333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a")
                .name("2019-07-01T14_13_00.317763Z.rcd")
                .previousHash(
                        "f423447a3d5a531a07426070e511555283daae063706242590949116f717a0524e4dd18f9d64e66c73982d475401db04")
                .size(4898)
                .version(1)
                .build();
        var recordFileVersionOneTwo = RecordFile.builder()
                .consensusStart(1561991340302068000L)
                .consensusEnd(1561991353226225001L)
                .count(69L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830")
                .hash(
                        "1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830")
                .name("2019-07-01T14_29_00.302068Z.rcd")
                .previousHash(recordFileVersionOneOne.getFileHash())
                .size(22347)
                .version(1)
                .build();
        var recordFileVersionTwoOne = RecordFile.builder()
                .consensusStart(1567188600419072000L)
                .consensusEnd(1567188604906443001L)
                .count(19L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .hash(
                        "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .name("2019-08-30T18_10_00.419072Z.rcd")
                .previousHash(digestAlgorithm.getEmptyHash())
                .size(8515)
                .version(2)
                .build();
        var recordFileVersionTwoTwo = RecordFile.builder()
                .consensusStart(1567188605249678000L)
                .consensusEnd(1567188609705382001L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .hash(
                        "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .name("2019-08-30T18_10_05.249678Z.rcd")
                .previousHash(recordFileVersionTwoOne.getFileHash())
                .size(6649)
                .version(2)
                .build();
        var recordFileVersionFiveOne = RecordFile.builder()
                .consensusStart(1610402964063739000L)
                .consensusEnd(1610402964063739000L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "e8adaac05a62a655a3c476b43f1383f6c5f5bba4bfa6c7b087dc4ee3a9089e232b5d5977bde7fba858fd56987792ece3")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .hash(
                        "151bd3358db59fc7936eff15f1cb6734354e444cf85549a5643e55c9c929cb500be712abccd588cd8d20eb92ca55ff49")
                .metadataHash(
                        "ffe56840b99145f7b3370367fa5784cbe225278afd1c4c078dfe5b950fee22e2b9e9a04bde32023c3ba07c057cb54406")
                .name("2021-01-11T22_09_24.063739000Z.rcd")
                .previousHash(digestAlgorithm.getEmptyHash())
                .size(498)
                .softwareVersionMajor(0)
                .softwareVersionMinor(9)
                .softwareVersionPatch(0)
                .version(5)
                .build();
        var recordFileVersionFiveTwo = RecordFile.builder()
                .consensusStart(1610402974097416003L)
                .consensusEnd(1610402974097416003L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "06fb76873dcdc3a4fdb67202e64ed735feaf6a6bb80d4f57fd3511df49ef61fc69d7a2414315028b7d77e168169fad22")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .hash(
                        "514e361089074cb06f984e5a943a20fba2a0d601b766f8adb432d03214c48c3ff14898e6b78292520340f484e820ea84")
                .metadataHash(
                        "912869b5204ffbb7e437aaa6e7a09e9d53da98ead27942fdf7017e850827e857fadb1167e8877cfb8175883adcd74f7d")
                .name("2021-01-11T22_09_34.097416003Z.rcd")
                .previousHash(recordFileVersionFiveOne.getHash())
                .size(498)
                .softwareVersionMajor(0)
                .softwareVersionMinor(9)
                .softwareVersionPatch(0)
                .version(5)
                .build();
        var recordFileVersionSixOne = RecordFile.builder()
                .consensusStart(1657701968041986003L)
                .consensusEnd(1657701968041986003L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "69a4354de5aeb12fbc989ae086fc291cfc1b61415391b4a46915b32aef722a511c4ceba60ecda72d527cc4866a4d235b")
                .hapiVersionMajor(0)
                .hapiVersionMinor(28)
                .hapiVersionPatch(0)
                .hash(
                        "a6c241fad2c636f68a6aa0da9293245a5ef0ebef345cd139858068ff7998716cefe0fd3afa0d21304725507061975279")
                .index(5L)
                .metadataHash(
                        "361c0176ba28cc55525c50e7e75e58618b451f3dca402d9be19626159d939e7a569534658125732c1af31ff6f4a8e283")
                .name("2022-07-13T08_46_08.041986003Z.rcd.gz")
                .previousHash(
                        "13d2594b9e9dbb73dad0cad67a96ad7a0e249af8693aa894003876c9ddd5534b3143e4d785e04fc0c461945a03e85178")
                .sidecarCount(0)
                .size(509)
                .softwareVersionMajor(0)
                .softwareVersionMinor(28)
                .softwareVersionPatch(0)
                .version(6)
                .build();
        var transactionSidecarRecord1 = TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(1657701971304284003L))
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1002L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {3, -21})))))
                        .addContractStateChanges(ContractStateChange.newBuilder()
                                .setContractId(ContractID.newBuilder().setContractNum(1003L))
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {8}))))))
                .build();
        var transactionSidecarRecord2 = TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(1657701971304284004L))
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(ContractID.newBuilder().setContractNum(1003L))
                        .setInitcode(DomainUtils.fromBytes(Hex.decodeHex(INITCODE)))
                        .setRuntimeBytecode(DomainUtils.fromBytes(Hex.decodeHex(RUNTIME_BYTECODE))))
                .build();
        var transactionSidecarRecords = List.of(transactionSidecarRecord1, transactionSidecarRecord2);
        var sidecarFileHash = Hex.decodeHex(
                "1ed54ea01aab5e726a087e94a0dd52c0f49b149d7a773ae71a3dc099f623bcf1840393db68f8db476ab11e6159f030f2");
        var recordFileVersionSixTwo = RecordFile.builder()
                .consensusStart(1657701971304284003L)
                .consensusEnd(1657701971304284004L)
                .count(2L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "ed518c8d05f470d4540db35ea8665ab158f9aeb0bcaa3332d171c1efba119da52c1ee510df599269b022d963d4d1e474")
                .hapiVersionMajor(0)
                .hapiVersionMinor(28)
                .hapiVersionPatch(0)
                .hash(
                        "3064b824b8b9f9ece011f88a26c030c9f6b822f30fbcabcc7240a220ea42bbdbf305db415a4e41307d0630d5cefe4550")
                .index(6L)
                .metadataHash(
                        "b13a2b638c5688dbec43b97dbee8ad637d2d42376fc313c628a990ac65aefdbd39832cf5ece42b925a520ed2d2bf8eac")
                .name("2022-07-13T08_46_11.304284003Z.rcd.gz")
                .previousHash(recordFileVersionSixOne.getHash())
                .size(805)
                .sidecarCount(1)
                .sidecars(List.of(SidecarFile.builder()
                        .actualHash(sidecarFileHash)
                        .consensusEnd(1657701971304284004L)
                        .count(2)
                        .hashAlgorithm(DigestAlgorithm.SHA_384)
                        .hash(sidecarFileHash)
                        .index(1)
                        .name("2022-07-13T08_46_11.304284003Z_01.rcd.gz")
                        .records(transactionSidecarRecords)
                        .size(279)
                        .types(List.of(1, 3))
                        .build()))
                .softwareVersionMajor(0)
                .softwareVersionMinor(28)
                .softwareVersionPatch(0)
                .version(6)
                .build();

        var allFiles = List.of(
                recordFileVersionOneOne,
                recordFileVersionOneTwo,
                recordFileVersionTwoOne,
                recordFileVersionTwoTwo,
                recordFileVersionFiveOne,
                recordFileVersionFiveTwo,
                recordFileVersionSixOne,
                recordFileVersionSixTwo);
        return Collections.unmodifiableMap(allFiles.stream().collect(Collectors.toMap(RecordFile::getName, rf -> rf)));
    }

    public List<RecordFile> getV2V5Files() {
        RecordFile recordFileV2 = RecordFile.builder()
                .consensusStart(1611188151568507001L)
                .consensusEnd(1611188151568507001L)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(
                        "e7d9e71efd239bde3adcad8eb0571c38f91f77ae76a4af69bb44f19b2785ad3594ac1d265351a592ab14301da9bb1950")
                .hash(
                        "e7d9e71efd239bde3adcad8eb0571c38f91f77ae76a4af69bb44f19b2785ad3594ac1d265351a592ab14301da9bb1950")
                .name("2021-01-21T00_15_51.568507001Z.rcd")
                .previousHash(
                        "d27ba83c736bfa2ffc9a6f062b27ea4856800bbbe820b77b32e08faf3d7475d81ef5a16f90ce065d35eefa999677edaa")
                .size(389)
                .version(2)
                .build();
        RecordFile recordFileV5 = RecordFile.builder()
                .consensusStart(1611188383558496000L)
                .consensusEnd(1611188383558496000L)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(
                        "42717bae0e538bac34563784b08b5a5b50a9964c9435452c93134bf13355c9778a1c64cfdc30f33fe52dd7f76dbdda70")
                .hapiVersionMajor(0)
                .hapiVersionMinor(11)
                .hapiVersionPatch(0)
                .hash(
                        "e6c1d7bfe956b6b2c8061bee5c43e512111cbccb21099bb0c49e2a7c74cf617cf5b6bf65070f29eb43a80d9cef2d8242")
                .metadataHash(
                        "1d83206a166a06c8579f9de637cf50a565341928b55bfbdc774ce85ac2169b46c23db42729723e7c39e5a042bd9e3b98")
                .name("2021-01-21T00_19_43.558496000Z.rcd")
                .previousHash(recordFileV2.getHash())
                .size(495)
                .softwareVersionMajor(0)
                .softwareVersionMinor(11)
                .softwareVersionPatch(0)
                .version(5)
                .build();
        return List.of(recordFileV2, recordFileV5);
    }

    public List<RecordFile> getV5V6Files() {
        RecordFile recordFileV5 = RecordFile.builder()
                .name("2022-06-21T09_14_34.364804003Z.rcd")
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .index(0L)
                .size(492)
                .version(5)
                .build();

        RecordFile recordFileV6 = RecordFile.builder()
                .name("2022-06-21T09_15_38.325469003Z.rcd.gz")
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .index(-9223372036854775797L)
                .size(788)
                .version(6)
                .build();

        return List.of(recordFileV5, recordFileV6);
    }
}
