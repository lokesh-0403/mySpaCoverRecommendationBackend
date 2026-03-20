package zasyaSolutions.mySpaCoverSkuRecommendation.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import zasyaSolutions.mySpaCoverSkuRecommendation.config.AppProperties;
import zasyaSolutions.mySpaCoverSkuRecommendation.exception.BadRequestException;
import zasyaSolutions.mySpaCoverSkuRecommendation.exception.ConflictException;
import zasyaSolutions.mySpaCoverSkuRecommendation.exception.NotFoundException;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.CsvDimensionReader;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.InventoryResponseOrganizer;
import zasyaSolutions.mySpaCoverSkuRecommendation.utils.PayloadGenerator;

@Service
public class TestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestExecutionService.class);
    private static final String REPORT_FILENAME_PREFIX = "inventory_report_";

    private final InventoryApiService inventoryApiService;
    private final Path storageRoot;
    private final Path uploadsRoot;
    private final Path resultsRoot;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, ExecutionRecord> executionRecords = new ConcurrentHashMap<>();

    public TestExecutionService(AppProperties appProperties, InventoryApiService inventoryApiService) {
        this.inventoryApiService = inventoryApiService;
        this.storageRoot = appProperties.getStorageRootPath();
        this.uploadsRoot = appProperties.getUploadsRootPath();
        this.resultsRoot = appProperties.getResultsRootPath();
        this.executorService = Executors.newFixedThreadPool(Math.max(1, appProperties.getExecution().getMaxParallelJobs()));

        createDirectoryIfMissing(storageRoot);
        createDirectoryIfMissing(uploadsRoot);
        createDirectoryIfMissing(resultsRoot);
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public Path getUploadsRoot() {
        return uploadsRoot;
    }

    public Path getResultsRoot() {
        return resultsRoot;
    }

    public String storeUploadedCsv(MultipartFile file) {
        validateCsvUpload(file);
        ensureWritableDirectory(uploadsRoot, "upload storage");

        String fileId = UUID.randomUUID().toString();
        String originalFilename = sanitizeFilename(Objects.requireNonNullElse(file.getOriginalFilename(), "dimensions.csv"));
        Path uploadDirectory = uploadsRoot.resolve(fileId);
        Path uploadedFilePath = uploadDirectory.resolve(originalFilename);

        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(file.getInputStream(), uploadedFilePath, StandardCopyOption.REPLACE_EXISTING);

            if (CsvDimensionReader.readDimensions(uploadedFilePath).isEmpty()) {
                throw new BadRequestException("Uploaded CSV does not contain any dimension rows");
            }
        } catch (IOException exception) {
            cleanupFailedUpload(uploadDirectory, uploadedFilePath);
            throw new IllegalStateException(
                "Failed to store uploaded CSV at " + uploadedFilePath + ": " + exception.getMessage(),
                exception
            );
        } catch (RuntimeException exception) {
            cleanupFailedUpload(uploadDirectory, uploadedFilePath);
            throw exception;
        }

        ExecutionRecord record = new ExecutionRecord(fileId, originalFilename, uploadedFilePath);
        record.status = "uploaded";
        record.uploadTime = Instant.now();
        executionRecords.put(fileId, record);

        log.info("Stored upload for fileId={} at {}", fileId, uploadedFilePath);
        return fileId;
    }

    public CompletableFuture<String> executeTestsAsync(String fileId) {
        ExecutionRecord record = getOrLoadRecord(fileId);
        markRunning(record);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeInternal(record);
            } catch (RuntimeException exception) {
                markFailed(record, exception);
                throw exception;
            }
        }, executorService);
    }

    public String executeTests(String fileId) {
        ExecutionRecord record = getOrLoadRecord(fileId);
        markRunning(record);

        try {
            return executeInternal(record);
        } catch (RuntimeException exception) {
            markFailed(record, exception);
            throw exception;
        }
    }

    public Path getResultFilePath(String fileId) {
        ExecutionRecord record = getOrLoadRecord(fileId);
        Path resultPath = record.excelReportPath != null ? record.excelReportPath : defaultExcelReportPath(fileId);

        if (!Files.exists(resultPath)) {
            throw new NotFoundException("Result file not found for fileId: " + fileId);
        }

        return resultPath;
    }

    public Map<String, Object> getExecutionStatus(String fileId) {
        ExecutionRecord record = getOrLoadRecord(fileId);
        return record.toResponse();
    }

    public Map<String, Object> deleteFilesForId(String fileId) {
        ExecutionRecord record = getOrLoadRecord(fileId);

        List<String> deletedFiles = new ArrayList<>();
        deleteTrackedPath(record.excelReportPath, deletedFiles);
        deleteTrackedPath(record.csvReportPath, deletedFiles);

        deleteDirectory(resultsRoot.resolve(fileId), deletedFiles);
        deleteTrackedPath(record.uploadFilePath, deletedFiles);
        deleteDirectory(uploadsRoot.resolve(fileId), deletedFiles);

        executionRecords.remove(fileId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("deletedFiles", deletedFiles);
        response.put("deletedCount", deletedFiles.size());
        response.put("message", "Cleaned up generated files for fileId: " + fileId);
        return response;
    }

    public Map<String, Object> deleteAllGeneratedReports() {
        List<String> deletedFiles = new ArrayList<>();
        deleteDirectoryContents(resultsRoot, deletedFiles);

        executionRecords.values().forEach(record -> {
            record.csvReportPath = null;
            record.excelReportPath = null;
            if (!"uploaded".equals(record.status)) {
                record.status = "uploaded";
                record.endTime = null;
                record.error = null;
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("deletedFiles", deletedFiles);
        response.put("deletedCount", deletedFiles.size());
        response.put("message", "Deleted all generated reports");
        return response;
    }

    public Map<String, Object> deleteAllBackups() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("deletedFiles", List.of());
        response.put("deletedCount", 0);
        response.put("message", "Legacy backup cleanup is no longer required because uploads are stored per fileId");
        return response;
    }

    public Map<String, Object> cleanupAll() {
        List<String> deletedFiles = new ArrayList<>();
        deleteDirectoryContents(resultsRoot, deletedFiles);
        deleteDirectoryContents(uploadsRoot, deletedFiles);
        executionRecords.clear();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("deletedFiles", deletedFiles);
        response.put("deletedCount", deletedFiles.size());
        response.put("message", "Deleted all generated files and uploads");
        return response;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    private String executeInternal(ExecutionRecord record) {
        ensureUploadExists(record);

        List<List<String>> skuPayloads = PayloadGenerator.generatePayloads(record.uploadFilePath);
        if (skuPayloads.isEmpty()) {
            throw new BadRequestException("No payloads could be generated from the uploaded CSV");
        }

        Path resultDirectory = resultsRoot.resolve(record.fileId);
        createDirectoryIfMissing(resultDirectory);

        Path csvReportPath = defaultCsvReportPath(record.fileId);
        Path excelReportPath = defaultExcelReportPath(record.fileId);
        deleteIfExists(csvReportPath);
        deleteIfExists(excelReportPath);

        String authToken = inventoryApiService.login();
        for (List<String> skuPayload : skuPayloads) {
            String responseJson = inventoryApiService.searchInventory(skuPayload, authToken);
            InventoryResponseOrganizer.processAndSaveInventory(responseJson, skuPayload, csvReportPath.toString());
        }

        if (!Files.exists(excelReportPath)) {
            throw new IllegalStateException("Excel report was not generated for fileId: " + record.fileId);
        }

        synchronized (record) {
            record.status = "completed";
            record.endTime = Instant.now();
            record.error = null;
            record.csvReportPath = csvReportPath;
            record.excelReportPath = excelReportPath;
        }

        log.info("Completed execution for fileId={} with report {}", record.fileId, excelReportPath);
        return excelReportPath.toString();
    }

    private void markRunning(ExecutionRecord record) {
        synchronized (record) {
            if ("running".equals(record.status)) {
                throw new ConflictException("Execution is already running for fileId: " + record.fileId);
            }
            record.status = "running";
            record.startTime = Instant.now();
            record.endTime = null;
            record.error = null;
            record.csvReportPath = null;
            record.excelReportPath = null;
        }
    }

    private void markFailed(ExecutionRecord record, RuntimeException exception) {
        synchronized (record) {
            record.status = "failed";
            record.error = exception.getMessage();
            record.endTime = Instant.now();
        }
        log.error("Execution failed for fileId={}", record.fileId, exception);
    }

    private ExecutionRecord getOrLoadRecord(String fileId) {
        ExecutionRecord existingRecord = executionRecords.get(fileId);
        if (existingRecord != null) {
            return existingRecord;
        }

        Path uploadDirectory = uploadsRoot.resolve(fileId);
        if (!Files.isDirectory(uploadDirectory)) {
            throw new NotFoundException("No upload found for fileId: " + fileId);
        }

        try (Stream<Path> files = Files.list(uploadDirectory)) {
            Path uploadedFile = files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Uploaded CSV not found for fileId: " + fileId));

            ExecutionRecord restoredRecord = new ExecutionRecord(fileId, uploadedFile.getFileName().toString(), uploadedFile);
            restoredRecord.status = Files.exists(defaultExcelReportPath(fileId)) ? "completed" : "uploaded";
            restoredRecord.csvReportPath = Files.exists(defaultCsvReportPath(fileId)) ? defaultCsvReportPath(fileId) : null;
            restoredRecord.excelReportPath = Files.exists(defaultExcelReportPath(fileId)) ? defaultExcelReportPath(fileId) : null;
            executionRecords.putIfAbsent(fileId, restoredRecord);
            return executionRecords.get(fileId);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to restore execution state for fileId: " + fileId, exception);
        }
    }

    private void validateCsvUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.toLowerCase().endsWith(".csv")) {
            throw new BadRequestException("Only CSV files are allowed");
        }
    }

    private void ensureUploadExists(ExecutionRecord record) {
        if (!Files.exists(record.uploadFilePath)) {
            throw new NotFoundException("Uploaded CSV not found for fileId: " + record.fileId);
        }
    }

    private Path defaultCsvReportPath(String fileId) {
        return resultsRoot.resolve(fileId).resolve(REPORT_FILENAME_PREFIX + fileId + ".csv");
    }

    private Path defaultExcelReportPath(String fileId) {
        return resultsRoot.resolve(fileId).resolve(REPORT_FILENAME_PREFIX + fileId + ".xlsx");
    }

    private void createDirectoryIfMissing(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize storage directory: " + directory, exception);
        }
    }

    private void ensureWritableDirectory(Path directory, String description) {
        createDirectoryIfMissing(directory);
        if (!Files.isWritable(directory)) {
            throw new IllegalStateException("Storage directory is not writable for " + description + ": " + directory);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete existing file: " + path, exception);
        }
    }

    private void deleteTrackedPath(Path path, List<String> deletedFiles) {
        if (path == null) {
            return;
        }

        try {
            if (Files.deleteIfExists(path)) {
                deletedFiles.add(path.toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + path, exception);
        }
    }

    private void deleteDirectoryContents(Path directory, List<String> deletedFiles) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths
                .filter(path -> !path.equals(directory))
                .sorted(Comparator.reverseOrder())
                .forEach(path -> deletePath(path, deletedFiles));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete directory contents: " + directory, exception);
        }
    }

    private void deleteDirectory(Path directory, List<String> deletedFiles) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    if (!path.equals(directory)) {
                        deletePath(path, deletedFiles);
                    } else {
                        deletePath(path, null);
                    }
                });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete directory: " + directory, exception);
        }
    }

    private void deleteDirectory(Path directory) {
        deleteDirectory(directory, new ArrayList<>());
    }

    private void cleanupFailedUpload(Path uploadDirectory, Path uploadedFilePath) {
        try {
            Files.deleteIfExists(uploadedFilePath);
        } catch (IOException cleanupException) {
            log.warn("Failed to delete partial upload {}", uploadedFilePath, cleanupException);
        }

        try {
            deleteDirectory(uploadDirectory);
        } catch (RuntimeException cleanupException) {
            log.warn("Failed to delete partial upload directory {}", uploadDirectory, cleanupException);
        }
    }

    private void deletePath(Path path, List<String> deletedFiles) {
        try {
            boolean wasDirectory = Files.isDirectory(path);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted && deletedFiles != null && !wasDirectory) {
                deletedFiles.add(path.toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete path: " + path, exception);
        }
    }

    private String sanitizeFilename(String originalFilename) {
        String sanitized = originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(sanitized) ? sanitized : "dimensions.csv";
    }

    private static final class ExecutionRecord {

        private final String fileId;
        private final String originalFileName;
        private final Path uploadFilePath;
        private volatile String status;
        private volatile Instant uploadTime;
        private volatile Instant startTime;
        private volatile Instant endTime;
        private volatile Path csvReportPath;
        private volatile Path excelReportPath;
        private volatile String error;

        private ExecutionRecord(String fileId, String originalFileName, Path uploadFilePath) {
            this.fileId = fileId;
            this.originalFileName = originalFileName;
            this.uploadFilePath = uploadFilePath;
        }

        private Map<String, Object> toResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("fileId", fileId);
            response.put("fileName", originalFileName);
            response.put("status", status);
            response.put("uploadPath", uploadFilePath.toString());
            if (uploadTime != null) {
                response.put("uploadTime", uploadTime.toString());
            }
            if (startTime != null) {
                response.put("startTime", startTime.toString());
            }
            if (endTime != null) {
                response.put("endTime", endTime.toString());
            }
            if (csvReportPath != null) {
                response.put("csvReportPath", csvReportPath.toString());
            }
            if (excelReportPath != null) {
                response.put("resultFilePath", excelReportPath.toString());
            }
            if (error != null) {
                response.put("error", error);
            }
            return response;
        }
    }
}
