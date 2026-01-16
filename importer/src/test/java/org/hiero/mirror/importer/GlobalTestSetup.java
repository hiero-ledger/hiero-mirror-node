// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.nio.file.Files;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.springframework.util.ResourceUtils;

@CustomLog
public class GlobalTestSetup implements LauncherSessionListener {

    private final TestExecutionListener commonTestSetup = new org.hiero.mirror.common.GlobalTestSetup();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
            @Override
            public void testPlanExecutionStarted(TestPlan testPlan) {
                commonTestSetup.testPlanExecutionStarted(testPlan);

                var commonProperties = CommonProperties.getInstance();
                updateAddressBook(commonProperties, "mainnet");
                updateAddressBook(commonProperties, "testnet");
                updateAddressBook(commonProperties, "test-v1");
                updateAddressBook(commonProperties, "test-v6-4n.bin");
                updateAddressBook(commonProperties, "test-v6-sidecar-4n.bin");
            }
        });
    }

    @SneakyThrows
    @SuppressWarnings("deprecation")
    private void updateAddressBook(CommonProperties commonProperties, String filename) {
        var addressBookPath =
                ResourceUtils.getFile("classpath:addressbook/" + filename).toPath();
        byte[] data = Files.readAllBytes(addressBookPath);
        var addressBook = NodeAddressBook.parseFrom(data);
        var builder = NodeAddressBook.newBuilder();

        for (var nodeAddress : addressBook.getNodeAddressList()) {
            var accountStr = nodeAddress.getMemo().toStringUtf8();
            accountStr = accountStr.replaceFirst(
                    "^\\d+.\\d+.", String.format("%d.%d.", commonProperties.getShard(), commonProperties.getRealm()));
            var nodeAddressBuilder = nodeAddress.toBuilder().setMemo(ByteString.copyFromUtf8(accountStr));
            if (nodeAddressBuilder.hasNodeAccountId()) {
                nodeAddressBuilder.setNodeAccountId(EntityId.of(accountStr).toAccountID());
            }

            builder.addNodeAddress(nodeAddressBuilder);
        }

        Files.write(addressBookPath, builder.build().toByteArray());
    }
}
