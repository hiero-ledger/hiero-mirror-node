// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.addressbook;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_ADDRESS_BOOK;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import jakarta.inject.Named;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.ConsensusMode;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.repository.AddressBookRepository;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.repository.NodeStakeRepository;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

@CustomLog
@Named
@CacheConfig(cacheNames = CACHE_NAME, cacheManager = CACHE_ADDRESS_BOOK)
@RequiredArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {

    public static final int INITIAL_NODE_ID_ACCOUNT_ID_OFFSET = 3;

    private final AddressBookRepository addressBookRepository;
    private final CommonProperties commonProperties;
    private final FileDataRepository fileDataRepository;
    private final ImporterProperties importerProperties;
    private final NodeStakeRepository nodeStakeRepository;
    private final SystemEntity systemEntity;
    private final TransactionTemplate transactionTemplate;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Collection<Long> addressBookFileIds = toAddressBookIds();

    @Override
    @CacheEvict(allEntries = true)
    public void refresh() {
        log.info("Clearing node cache");
    }

    /**
     * Updates mirror node with new address book details provided in fileData object
     *
     * @param fileData file data entry containing address book bytes
     */
    @Override
    @CacheEvict(allEntries = true)
    public void update(FileData fileData) {
        if (!isAddressBook(fileData.getEntityId())) {
            log.warn("Not an address book File ID. Skipping processing ...");
            return;
        }

        fileDataRepository.save(fileData);

        if (fileData.getFileData() == null || fileData.getFileData().length == 0) {
            log.warn("Byte array contents were empty. Skipping processing ...");
            return;
        }

        log.info("Received an address book update: {}", fileData);

        // ensure address_book table is populated with latest addressBook prior to additions
        validateAndCompleteAddressBookList(fileData);

        parse(fileData);
    }

    @Override
    public AddressBook getCurrent() {
        long consensusTimestamp = DomainUtils.convertToNanosMax(Instant.now());
        long fileId = systemEntity.addressBookFile102().getId();

        // retrieve latest address book. If address_book is empty parse initial and historic address book files
        return addressBookRepository.findLatest(consensusTimestamp, fileId).orElseGet(this::migrate);
    }

    @Cacheable
    @Override
    public Collection<ConsensusNode> getNodes() {
        var addressBook = getCurrent();
        var totalStake = new AtomicLong(0L);
        var nodes = new TreeSet<ConsensusNode>();
        var nodeStakes = new HashMap<Long, NodeStake>();
        var consensusMode = importerProperties.getConsensusMode();
        var nodesInAddressBook = addressBook.getEntries().stream()
                .map(AddressBookEntry::getNodeId)
                .collect(Collectors.toSet());

        var nodeStakeTimestamp = new AtomicLong(0L);
        nodeStakeRepository.findLatest().forEach(nodeStake -> {
            if (consensusMode == ConsensusMode.EQUAL) {
                nodeStake.setStake(1L);
            }

            if (consensusMode != ConsensusMode.STAKE_IN_ADDRESS_BOOK
                    || nodesInAddressBook.contains(nodeStake.getNodeId())) {
                totalStake.addAndGet(nodeStake.getStake());
            }
            nodeStakes.put(nodeStake.getNodeId(), nodeStake);
            // all the node stake rows have the same consensus timestamp
            nodeStakeTimestamp.compareAndSet(0L, nodeStake.getConsensusTimestamp());
        });

        long nodeCount = (consensusMode == ConsensusMode.STAKE_IN_ADDRESS_BOOK || nodeStakes.isEmpty())
                ? addressBook.getNodeCount()
                : nodeStakes.size();

        // if only including address book nodes in stake count, warn if any nodes are excluded
        if (consensusMode == ConsensusMode.STAKE_IN_ADDRESS_BOOK
                && addressBook.getNodeCount() != nodeStakes.size()
                && !nodeStakes.isEmpty()) {
            log.warn(
                    "Using address book {} with {} nodes and node stake {} with {} nodes",
                    addressBook.getStartConsensusTimestamp(),
                    addressBook.getNodeCount(),
                    nodeStakeTimestamp.get(),
                    nodeStakes.size());
        }

        addressBook.getEntries().forEach(e -> {
            if (StringUtils.isNotBlank(importerProperties.getNodePublicKey())) {
                e.setPublicKey(importerProperties.getNodePublicKey());
            }
            var nodeStake = nodeStakes.get(e.getNodeId());
            nodes.add(new ConsensusNodeWrapper(e, nodeStake, nodeCount, totalStake.get()));
        });

        if (nodes.isEmpty()) {
            throw new InvalidDatasetException("Unable to find a valid address book");
        }

        return Collections.unmodifiableCollection(nodes);
    }

    /**
     * Checks if provided EntityId is a valid AddressBook file EntityId
     *
     * @param entityId file  entity id
     * @return returns true if valid address book EntityId
     */
    @Override
    public boolean isAddressBook(EntityId entityId) {
        return systemEntity.addressBookFile101().equals(entityId)
                || systemEntity.addressBookFile102().equals(entityId);
    }

    /**
     * Creates a AddressBook object given address book byte array contents. Attempts to convert contents into a valid
     * NodeAddressBook proto. If successful the NodeAddressBook details including AddressBookEntry entries are extracted
     * and added to AddressBook object.
     *
     * @param fileData fileData object representing address book data
     * @return
     */
    protected AddressBook buildAddressBook(FileData fileData) {
        long startConsensusTimestamp = getAddressBookStartConsensusTimestamp(fileData);
        var addressBookBuilder = AddressBook.builder()
                .fileData(fileData.getFileData())
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileId(fileData.getEntityId());

        try {
            var nodeAddressBook = NodeAddressBook.parseFrom(fileData.getFileData());

            if (nodeAddressBook != null && nodeAddressBook.getNodeAddressCount() > 0) {
                addressBookBuilder.nodeCount(nodeAddressBook.getNodeAddressCount());
                Collection<AddressBookEntry> addressBookEntryCollection =
                        retrieveNodeAddressesFromAddressBook(nodeAddressBook, startConsensusTimestamp);

                addressBookBuilder.entries(new ArrayList<>(addressBookEntryCollection));
            }
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
            return null;
        }

        return addressBookBuilder.build();
    }

    private long getAddressBookStartConsensusTimestamp(FileData fileData) {
        return fileData.getConsensusTimestamp() + 1;
    }

    /**
     * Migrates address book data by searching file_data table for applicable 101 and 102 files. These files are
     * converted to AddressBook objects and used to populate address_book and address_book_entry tables. Migrate flow
     * will populate initial addressBook where applicable and consider all file_data present
     *
     * @return Latest AddressBook from historical files
     */
    @Override
    public synchronized AddressBook migrate() {
        long consensusTimestamp = DomainUtils.convertToNanosMax(Instant.now());
        long fileId = systemEntity.addressBookFile102().getId();
        var currentAddressBook =
                addressBookRepository.findLatest(consensusTimestamp, fileId).orElse(null);

        if (currentAddressBook != null) {
            // verify no file_data 102 entries exists after current addressBook
            List<FileData> fileDataList = fileDataRepository.findAddressBooksBetween(
                    currentAddressBook.getStartConsensusTimestamp(), Long.MAX_VALUE, getAddressBookFileIds(), 1);
            if (CollectionUtils.isEmpty(fileDataList)) {
                log.trace("All valid address books exist in db, skipping migration");
                return currentAddressBook;
            }

            log.warn("Valid address book file data entries exist in db after current address book");
        }

        log.info("Empty or incomplete list of address books found in db, proceeding with migration");
        return transactionTemplate.execute(status -> {
            log.info("Searching for address book on file system");
            var initialAddressBook =
                    currentAddressBook == null ? parse(getInitialAddressBookFileData()) : currentAddressBook;

            if (initialAddressBook == null) {
                throw new InvalidDatasetException("Unable to load starting address book");
            }

            // Parse all applicable addressBook file_data entries are processed
            AddressBook latestAddressBook =
                    parseHistoricAddressBooks(initialAddressBook.getStartConsensusTimestamp() - 1, consensusTimestamp);

            // set latestAddressBook as newest addressBook from file_data entries or initial addressBook from filesystem
            latestAddressBook = latestAddressBook == null ? initialAddressBook : latestAddressBook;

            log.info("Migration complete. Current address book to db: {}", latestAddressBook);
            return latestAddressBook;
        });
    }

    /**
     * Ensure all addressBook file_data entries prior to this fileData and after the current address book have been
     * parsed. If not parse them and populate the address_book tables to complete the list. This does not handle initial
     * startup and only ensure any unprocessed address book files are processed.
     *
     * @param fileData
     */
    private void validateAndCompleteAddressBookList(FileData fileData) {
        AddressBook currentAddressBook = getCurrent();
        long startConsensusTimestamp = currentAddressBook == null ? 0 : currentAddressBook.getStartConsensusTimestamp();

        transactionTemplate.executeWithoutResult(status ->
                // Parse all applicable missed addressBook file_data entries in range
                parseHistoricAddressBooks(startConsensusTimestamp, fileData.getConsensusTimestamp()));
    }

    /**
     * Parses provided fileData object into an AddressBook object if valid and stores into db. Also updates previous
     * address book endConsensusTimestamp based on new address book's startConsensusTimestamp.
     *
     * @param fileData file data with timestamp, contents, entityId and transaction type for parsing
     * @return Parsed AddressBook from fileData object
     */
    private AddressBook parse(FileData fileData) {
        if (addressBookRepository.existsById(getAddressBookStartConsensusTimestamp(fileData))) {
            // skip as address book already exists
            log.info("Address book from fileData {} already exists, skip parsing", fileData);
            return null;
        }

        byte[] addressBookBytes = null;
        if (fileData.transactionTypeIsAppend()) {
            // concatenate bytes from partial address book file data in db
            addressBookBytes = combinePreviousFileDataContents(fileData);
            if (addressBookBytes == null) {
                log.info("Missing corresponding FileCreate/FileUpdate entry for fileData {}. skip parsing", fileData);
                return null;
            }
        } else {
            addressBookBytes = fileData.getFileData();
        }

        var addressBook = buildAddressBook(new FileData(
                fileData.getConsensusTimestamp(),
                addressBookBytes,
                fileData.getEntityId(),
                fileData.getTransactionType()));
        if (addressBook != null) {
            addressBook = addressBookRepository.save(addressBook);
            var nodeIds = addressBook.getEntries().stream()
                    .map(AddressBookEntry::getNodeId)
                    .collect(Collectors.toCollection(TreeSet::new));
            log.info(
                    "Processed new address book at timestamp {} with {} nodes: {}",
                    addressBook.getEndConsensusTimestamp(),
                    addressBook.getNodeCount(),
                    nodeIds);

            // update previous addressBook
            updatePreviousAddressBook(fileData);
        }

        return addressBook;
    }

    /**
     * Concatenates byte arrays of first fileCreate/fileUpdate transaction and intermediate fileAppend entries that make
     * up the potential addressBook
     *
     * @param fileData file data entry containing address book bytes
     * @return
     */
    private byte[] combinePreviousFileDataContents(FileData fileData) {
        FileData firstPartialAddressBook = fileDataRepository
                .findLatestMatchingFile(
                        fileData.getConsensusTimestamp(),
                        fileData.getEntityId().getId(),
                        List.of(TransactionType.FILECREATE.getProtoId(), TransactionType.FILEUPDATE.getProtoId()))
                .orElse(null);
        if (firstPartialAddressBook == null) {
            return null;
        }
        List<FileData> appendFileDataEntries = fileDataRepository.findFilesInRange(
                getAddressBookStartConsensusTimestamp(firstPartialAddressBook),
                fileData.getConsensusTimestamp() - 1,
                firstPartialAddressBook.getEntityId().getId(),
                TransactionType.FILEAPPEND.getProtoId());

        try (var bos = new ByteArrayOutputStream(firstPartialAddressBook.getFileData().length)) {
            bos.write(firstPartialAddressBook.getFileData());
            for (var i = 0; i < appendFileDataEntries.size(); i++) {
                bos.write(appendFileDataEntries.get(i).getFileData());
            }

            bos.write(fileData.getFileData());
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error concatenating partial address book fileData entries", ex);
        }
    }

    /**
     * Extracts a collection of AddressBookEntry domain objects from NodeAddressBook proto. Sets provided
     * consensusTimestamp as the consensusTimestamp of each address book entry to ensure mapping to a single
     * AddressBook
     *
     * @param nodeAddressBook    node address book proto
     * @param consensusTimestamp transaction consensusTimestamp
     * @return
     */
    private Collection<AddressBookEntry> retrieveNodeAddressesFromAddressBook(
            NodeAddressBook nodeAddressBook, long consensusTimestamp) throws UnknownHostException {
        Map<Long, AddressBookEntry> addressBookEntries = new LinkedHashMap<>(); // node id to entry

        for (NodeAddress nodeAddressProto : nodeAddressBook.getNodeAddressList()) {
            Pair<Long, EntityId> nodeIds = getNodeIds(nodeAddressProto);
            AddressBookEntry addressBookEntry = addressBookEntries.computeIfAbsent(
                    nodeIds.getLeft(), k -> getAddressBookEntry(nodeAddressProto, consensusTimestamp, nodeIds));

            Set<AddressBookServiceEndpoint> updatedList = new HashSet<>(addressBookEntry.getServiceEndpoints());
            updatedList.addAll(getAddressBookServiceEndpoints(nodeAddressProto, consensusTimestamp));
            addressBookEntry.setServiceEndpoints(updatedList);
        }

        return addressBookEntries.values();
    }

    /**
     * Get Node id and account id
     *
     * @param nodeAddressProto
     * @return Pair of nodeId and nodeAccountId
     */
    @SuppressWarnings({"deprecation", "java:S1874"})
    private Pair<Long, EntityId> getNodeIds(NodeAddress nodeAddressProto) {
        var memo = nodeAddressProto.getMemo().toStringUtf8();
        var nodeAccountId = nodeAddressProto.hasNodeAccountId()
                ? EntityId.of(nodeAddressProto.getNodeAccountId())
                : EntityId.isValid(memo) ? EntityId.of(memo) : EntityId.EMPTY;

        if (EntityId.isEmpty(nodeAccountId)
                || nodeAccountId.getRealm() != commonProperties.getRealm()
                || nodeAccountId.getShard() != commonProperties.getShard()) {
            throw new InvalidDatasetException("Invalid NodeAddress.nodeAccountId: " + nodeAddressProto);
        }

        var nodeId = nodeAddressProto.getNodeId();
        // ensure valid nodeId. In early versions of initial addressBook (entityNum < 20) all nodeIds are set to 0
        if (nodeId == 0 && nodeAccountId.getNum() < 20 && nodeAccountId.getNum() != INITIAL_NODE_ID_ACCOUNT_ID_OFFSET) {
            nodeId = nodeAccountId.getNum() - INITIAL_NODE_ID_ACCOUNT_ID_OFFSET;
        }

        return Pair.of(nodeId, nodeAccountId);
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private AddressBookEntry getAddressBookEntry(
            NodeAddress nodeAddressProto, long consensusTimestamp, Pair<Long, EntityId> nodeIds) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .description(nodeAddressProto.getDescription())
                .nodeAccountId(nodeIds.getRight())
                .nodeId(nodeIds.getLeft())
                .publicKey(nodeAddressProto.getRSAPubKey())
                .serviceEndpoints(Set.of())
                .stake(nodeAddressProto.getStake());

        if (!nodeAddressProto.getNodeCertHash().isEmpty()) {
            builder.nodeCertHash(nodeAddressProto.getNodeCertHash().toByteArray());
        }

        if (!nodeAddressProto.getMemo().isEmpty()) {
            builder.memo(nodeAddressProto.getMemo().toStringUtf8());
        }

        return builder.build();
    }

    private Set<AddressBookServiceEndpoint> getAddressBookServiceEndpoints(
            NodeAddress nodeAddressProto, long consensusTimestamp) throws UnknownHostException {
        var nodeId = nodeAddressProto.getNodeId();
        Set<AddressBookServiceEndpoint> serviceEndpoints = new HashSet<>();

        // create an AddressBookServiceEndpoint for deprecated port and IP if populated
        AddressBookServiceEndpoint deprecatedServiceEndpoint =
                getAddressBookServiceEndpoint(nodeAddressProto, consensusTimestamp, nodeId);
        if (deprecatedServiceEndpoint != null) {
            serviceEndpoints.add(deprecatedServiceEndpoint);
        }

        // create an AddressBookServiceEndpoint for every ServiceEndpoint found
        for (ServiceEndpoint serviceEndpoint : nodeAddressProto.getServiceEndpointList()) {
            serviceEndpoints.add(getAddressBookServiceEndpoint(serviceEndpoint, consensusTimestamp, nodeId));
        }

        return serviceEndpoints;
    }

    @SuppressWarnings("deprecation")
    private AddressBookServiceEndpoint getAddressBookServiceEndpoint(
            NodeAddress nodeAddressProto, long consensusTimestamp, long nodeId) {
        String ip = nodeAddressProto.getIpAddress().toStringUtf8();
        if (StringUtils.isBlank(ip)) {
            return null;
        }

        AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
        addressBookServiceEndpoint.setConsensusTimestamp(consensusTimestamp);
        addressBookServiceEndpoint.setDomainName(StringUtils.EMPTY);
        addressBookServiceEndpoint.setIpAddressV4(ip);
        addressBookServiceEndpoint.setPort(nodeAddressProto.getPortno());
        addressBookServiceEndpoint.setNodeId(nodeId);
        return addressBookServiceEndpoint;
    }

    private AddressBookServiceEndpoint getAddressBookServiceEndpoint(
            ServiceEndpoint serviceEndpoint, long consensusTimestamp, long nodeId) throws UnknownHostException {
        AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
        addressBookServiceEndpoint.setConsensusTimestamp(consensusTimestamp);
        addressBookServiceEndpoint.setDomainName(serviceEndpoint.getDomainName());
        addressBookServiceEndpoint.setIpAddressV4(StringUtils.EMPTY);
        addressBookServiceEndpoint.setNodeId(nodeId);
        addressBookServiceEndpoint.setPort(serviceEndpoint.getPort());

        if (serviceEndpoint.getIpAddressV4().size() == 4) {
            var ip = InetAddress.getByAddress(DomainUtils.toBytes(serviceEndpoint.getIpAddressV4()))
                    .getHostAddress();
            addressBookServiceEndpoint.setIpAddressV4(ip);
        }

        return addressBookServiceEndpoint;
    }

    /**
     * Address book updates currently span record files as well as a network shutdown. To account for this verify start
     * and end of addressBook are set after a record file is processed. If not set based on first and last transaction
     * in record file
     *
     * @param fileData FileData of current transaction
     */
    private void updatePreviousAddressBook(FileData fileData) {
        long currentTimestamp = getAddressBookStartConsensusTimestamp(fileData);
        addressBookRepository
                .findLatest(
                        fileData.getConsensusTimestamp(), fileData.getEntityId().getId())
                .ifPresent(previousAddressBook -> {
                    // set EndConsensusTimestamp of addressBook as first transaction - 1ns in record file if not set
                    if (previousAddressBook.getStartConsensusTimestamp() != currentTimestamp
                            && previousAddressBook.getEndConsensusTimestamp() == null) {
                        previousAddressBook.setEndConsensusTimestamp(fileData.getConsensusTimestamp());
                        addressBookRepository.save(previousAddressBook);
                        log.info(
                                "Setting endConsensusTimestamp of previous AddressBook ({}) to {}",
                                previousAddressBook.getStartConsensusTimestamp(),
                                fileData.getConsensusTimestamp());
                    }
                });
    }

    /**
     * Retrieve the initial address book file for the network from either the file system or class path
     *
     * @return Address book fileData object
     */
    private FileData getInitialAddressBookFileData() {
        byte[] addressBookBytes;

        try {
            Path initialAddressBook = importerProperties.getInitialAddressBook();
            if (initialAddressBook != null) {
                log.info("Loading bootstrap address book from {}", initialAddressBook);
                addressBookBytes = Files.readAllBytes(initialAddressBook);
            } else {
                final var resourcePath = resolveBootstrapAddressBookResourcePath();
                log.info("Loading bootstrap address book from classpath:/{}", resourcePath);

                final var resource = new ClassPathResource(resourcePath);
                try (var in = resource.getInputStream()) {
                    addressBookBytes = in.readAllBytes();
                }
            }

            log.info("Loaded bootstrap address book of {} B", addressBookBytes.length);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load bootstrap address book", e);
        }

        return new FileData(
                0L, addressBookBytes, systemEntity.addressBookFile102(), TransactionType.FILECREATE.getProtoId());
    }

    private String resolveBootstrapAddressBookResourcePath() throws IOException {
        final var targetNanos = getTargetNanos();
        final var network = importerProperties.getNetwork();
        final var prefix = network + "-";

        final var resolver =
                new PathMatchingResourcePatternResolver(Thread.currentThread().getContextClassLoader());
        final var resources = resolver.getResources("classpath:/addressbook/" + network + "*");

        long bestTs = -1L;
        String bestPath = null;

        for (var resource : resources) {
            String filename = resource.getFilename();
            long ts = parseTimestampedFilename(filename, prefix).orElse(-1L);

            if (ts <= targetNanos && ts > bestTs) {
                bestTs = ts;
                bestPath = "addressbook/" + filename;
            }
        }

        if (bestPath != null) {
            log.info(
                    "Selected timestamped bootstrap address book '{}' (chosenTs={} targetTs={})",
                    bestPath,
                    bestTs,
                    targetNanos);
            return bestPath;
        }

        String plain = "addressbook/" + network;
        log.info(
                "Selected default bootstrap address book '{}' (no timestamped file <= targetTs={})",
                plain,
                targetNanos);

        return plain;
    }

    private long getTargetNanos() {
        var start = importerProperties.getStartDate();
        return DomainUtils.convertToNanosMax(start != null ? start : Instant.now());
    }

    private static OptionalLong parseTimestampedFilename(String filename, String prefix) {
        if (filename == null || !filename.startsWith(prefix)) {
            return OptionalLong.empty();
        }

        String tsPart = filename.substring(prefix.length());
        if (tsPart.isBlank() || !tsPart.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(Long.parseLong(tsPart));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Batch parse all 101 and 102 addressBook fileData objects and update the address_book table. Uses page size and
     * timestamp counters to batch query file data entries within timestamp range
     */
    private AddressBook parseHistoricAddressBooks(long startTimestamp, long endTimestamp) {
        var fileDataEntries = 0;
        long currentConsensusTimestamp = startTimestamp;
        AddressBook lastAddressBook = null;

        // retrieve pages of fileData entries for historic address books within range
        var fileIds = getAddressBookFileIds();
        int pageSize = 1000;
        var fileDataList =
                fileDataRepository.findAddressBooksBetween(currentConsensusTimestamp, endTimestamp, fileIds, pageSize);
        while (!CollectionUtils.isEmpty(fileDataList)) {
            log.info("Retrieved {} file_data rows for address book processing", fileDataList.size());

            for (FileData fileData : fileDataList) {
                if (fileData.getFileData() != null && fileData.getFileData().length > 0) {
                    // convert and ingest address book fileData contents
                    lastAddressBook = parse(fileData);
                    fileDataEntries++;
                }

                // update timestamp counter to ensure next query doesn't reconsider files in this time range
                currentConsensusTimestamp = fileData.getConsensusTimestamp();
            }

            fileDataList = fileDataRepository.findAddressBooksBetween(
                    currentConsensusTimestamp, endTimestamp, fileIds, pageSize);
        }

        log.info("Processed {} historic address books", fileDataEntries);
        return lastAddressBook;
    }

    private Collection<Long> toAddressBookIds() {
        return List.of(
                systemEntity.addressBookFile101().getId(),
                systemEntity.addressBookFile102().getId());
    }
}
