package zasyaSolutions.mySpaCoverSkuRecommendation.controller;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import zasyaSolutions.mySpaCoverSkuRecommendation.service.TestExecutionService;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
public class TestAutomationController {

    private static final Logger log = LoggerFactory.getLogger(TestAutomationController.class);

    private final TestExecutionService testExecutionService;

    @Autowired
    public TestAutomationController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    /**
     * Upload a CSV file for later execution.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileId = testExecutionService.storeUploadedCsv(file);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("fileName", file.getOriginalFilename());
        response.put("message", "CSV file uploaded successfully");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    /**
     * Execute a recommendation job synchronously and return the generated Excel report path.
     */
    @PostMapping("/execute/{fileId}")
    public ResponseEntity<Map<String, Object>> executeTests(@PathVariable String fileId) {
        log.info("Received synchronous execution request for fileId={}", fileId);

        String resultFilePath = testExecutionService.executeTests(fileId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "completed");
        response.put("fileId", fileId);
        response.put("resultFilePath", resultFilePath);
        response.put("message", "Execution completed successfully. Excel report generated.");
        return ResponseEntity.ok(response);
    }

    /**
     * Execute a recommendation job asynchronously and poll its status later.
     */
    @PostMapping("/execute-async/{fileId}")
    public ResponseEntity<Map<String, Object>> executeTestsAsync(@PathVariable String fileId) {
        log.info("Received asynchronous execution request for fileId={}", fileId);

        testExecutionService.executeTestsAsync(fileId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "running");
        response.put("fileId", fileId);
        response.put("message", "Execution started. Use the status endpoint to check progress.");
        return ResponseEntity.ok(response);
    }

    /**
     * Download the generated Excel report.
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadResultFile(@PathVariable String fileId) throws IOException {
        log.info("Received download request for fileId={}", fileId);

        Path filePath = testExecutionService.getResultFilePath(fileId);
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new NoSuchFileException(filePath.toString());
        }

        String filename = filePath.getFileName().toString();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(resource.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .body(resource);
    }

    /**
     * Get recommendation job status.
     */
    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> getTestStatus(@PathVariable String fileId) {
        return ResponseEntity.ok(testExecutionService.getExecutionStatus(fileId));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new LinkedHashMap<>();
        Path storageRoot = testExecutionService.getStorageRoot();
        Path uploadsRoot = testExecutionService.getUploadsRoot();
        Path resultsRoot = testExecutionService.getResultsRoot();
        response.put("status", "ok");
        response.put("message", "Recommendation service is running");
        response.put("workingDirectory", Path.of("").toAbsolutePath().normalize().toString());
        response.put("storageRoot", storageRoot.toString());
        response.put("storageWritable", Boolean.toString(Files.isWritable(storageRoot)));
        response.put("uploadsRoot", uploadsRoot.toString());
        response.put("uploadsWritable", Boolean.toString(Files.isWritable(uploadsRoot)));
        response.put("resultsRoot", resultsRoot.toString());
        response.put("resultsWritable", Boolean.toString(Files.isWritable(resultsRoot)));
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete generated files for a specific fileId.
     */
    @DeleteMapping("/cleanup/{fileId}")
    public ResponseEntity<Map<String, Object>> cleanupFiles(@PathVariable String fileId) {
        log.info("Received cleanup request for fileId={}", fileId);
        return ResponseEntity.ok(testExecutionService.deleteFilesForId(fileId));
    }
    
    /**
     * Delete all generated Excel reports.
     */
    @DeleteMapping("/cleanup/reports/all")
    public ResponseEntity<Map<String, Object>> cleanupAllReports() {
        log.info("Received cleanup request for all reports");
        return ResponseEntity.ok(testExecutionService.deleteAllGeneratedReports());
    }
    
    /**
     * Legacy endpoint retained for compatibility.
     */
    @DeleteMapping("/cleanup/backups/all")
    public ResponseEntity<Map<String, Object>> cleanupAllBackups() {
        log.info("Received cleanup request for legacy backups");
        return ResponseEntity.ok(testExecutionService.deleteAllBackups());
    }
    
    /**
     * Delete all generated uploads and reports.
     */
    @DeleteMapping("/cleanup/all")
    public ResponseEntity<Map<String, Object>> cleanupAll() {
        log.info("Received cleanup request for all generated data");
        return ResponseEntity.ok(testExecutionService.cleanupAll());
    }
}
