package zasyaSolutions.mySpaCoverSkuRecommendation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import zasyaSolutions.mySpaCoverSkuRecommendation.service.TestExecutionService;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
@CrossOrigin(origins = "*") // Configure this properly in production
public class TestAutomationController {

    @Autowired
    private TestExecutionService testExecutionService;

    /**
     * Upload CSV file - replaces the target spa_cover_dimensions.csv file
     * 
     * Endpoint: POST /api/tests/upload
     * Request: multipart/form-data with "file" parameter
     * Response: { "fileId": "uuid", "fileName": "original-name.csv", "message": "..." }
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();

        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate CSV format
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
                response.put("error", "Only CSV files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            // Replace the target CSV file
            String fileId = testExecutionService.replaceTargetCsvFile(file);
            
            response.put("fileId", fileId);
            response.put("fileName", originalFilename);
            response.put("message", "CSV file uploaded and replaced successfully");
            response.put("status", "success");

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("status", "failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Execute TestNG tests
     * 
     * Endpoint: POST /api/tests/execute/{fileId}
     * Response: { "status": "completed", "resultFilePath": "...", "fileId": "..." }
     * 
     * This endpoint runs the test synchronously and returns the Excel report path
     */
    @PostMapping("/execute/{fileId}")
    public ResponseEntity<Map<String, Object>> executeTests(@PathVariable String fileId) {
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("Received execute request for fileId: " + fileId);
            
            // Execute tests synchronously (this will block until tests complete)
            String resultFilePath = testExecutionService.executeTests(fileId);

            response.put("status", "completed");
            response.put("fileId", fileId);
            response.put("resultFilePath", resultFilePath);
            response.put("message", "Tests executed successfully. Excel report generated.");

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "failed");
            response.put("error", e.getMessage());
            response.put("fileId", fileId);
            
            System.err.println("Error executing tests: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Execute tests asynchronously (optional alternative)
     * 
     * Endpoint: POST /api/tests/execute-async/{fileId}
     * Response: { "status": "running", "fileId": "...", "message": "..." }
     * 
     * Use this if you want to start tests in background and poll for status
     */
    @PostMapping("/execute-async/{fileId}")
    public ResponseEntity<Map<String, Object>> executeTestsAsync(@PathVariable String fileId) {
        Map<String, Object> response = new HashMap<>();

        try {
            System.out.println("Received async execute request for fileId: " + fileId);
            
            // Execute tests asynchronously (returns immediately)
            testExecutionService.executeTestsAsync(fileId);

            response.put("status", "running");
            response.put("fileId", fileId);
            response.put("message", "Tests execution started. Use /status endpoint to check progress.");

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "failed");
            response.put("error", e.getMessage());
            response.put("fileId", fileId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download the generated Excel report
     * 
     * Endpoint: GET /api/tests/download/{fileId}
     * Response: Excel file download
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadResultFile(@PathVariable String fileId) {
        try {
            System.out.println("Download request for fileId: " + fileId);
            
            Path filePath = testExecutionService.getResultFilePath(fileId);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String filename = filePath.getFileName().toString();
                
                System.out.println("Serving file: " + filename + " from path: " + filePath.toAbsolutePath());
                
                // Use proper Excel MIME type
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .body(resource);
            } else {
                System.err.println("File not found or not readable: " + filePath);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get test execution status
     * 
     * Endpoint: GET /api/tests/status/{fileId}
     * Response: { "status": "running|completed|failed", ... }
     */
    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> getTestStatus(@PathVariable String fileId) {
        try {
            Map<String, Object> status = testExecutionService.getExecutionStatus(fileId);
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            response.put("status", "error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Health check endpoint
     * 
     * Endpoint: GET /api/tests/health
     * Response: { "status": "ok", "message": "..." }
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Test automation service is running");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete files for a specific fileId
     * 
     * Endpoint: DELETE /api/tests/cleanup/{fileId}
     * Response: { "status": "success", "deletedFiles": [...], "deletedCount": n }
     */
    @DeleteMapping("/cleanup/{fileId}")
    public ResponseEntity<Map<String, Object>> cleanupFiles(@PathVariable String fileId) {
        try {
            System.out.println("Cleanup request for fileId: " + fileId);
            
            Map<String, Object> result = testExecutionService.deleteFilesForId(fileId);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Delete all generated Excel reports
     * 
     * Endpoint: DELETE /api/tests/cleanup/reports/all
     * Response: { "status": "success", "deletedFiles": [...], "deletedCount": n }
     */
    @DeleteMapping("/cleanup/reports/all")
    public ResponseEntity<Map<String, Object>> cleanupAllReports() {
        try {
            System.out.println("Cleanup all reports request");
            
            Map<String, Object> result = testExecutionService.deleteAllGeneratedReports();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Delete all backup CSV files
     * 
     * Endpoint: DELETE /api/tests/cleanup/backups/all
     * Response: { "status": "success", "deletedFiles": [...], "deletedCount": n }
     */
    @DeleteMapping("/cleanup/backups/all")
    public ResponseEntity<Map<String, Object>> cleanupAllBackups() {
        try {
            System.out.println("Cleanup all backups request");
            
            Map<String, Object> result = testExecutionService.deleteAllBackups();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Complete cleanup - delete all files (reports + backups)
     * 
     * Endpoint: DELETE /api/tests/cleanup/all
     * Response: { "status": "success", "deletedFiles": [...], "deletedCount": n }
     */
    @DeleteMapping("/cleanup/all")
    public ResponseEntity<Map<String, Object>> cleanupAll() {
        try {
            System.out.println("Complete cleanup request - deleting all files");
            
            Map<String, Object> result = testExecutionService.cleanupAll();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}