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
@CrossOrigin(origins = "http://localhost:3000") // Your React app URL
public class TestAutomationController {

    @Autowired
    private TestExecutionService testExecutionService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String fileId = testExecutionService.saveUploadedFile(file);
            response.put("fileId", fileId);
            response.put("fileName", file.getOriginalFilename());
            response.put("message", "File uploaded successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/execute/{fileId}")
    public ResponseEntity<Map<String, Object>> executeTests(@PathVariable String fileId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String resultFileId = testExecutionService.executeTestsWithFile(fileId);
            
            response.put("status", "success");
            response.put("resultFileId", resultFileId);
            response.put("message", "Tests executed successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadResultFile(@PathVariable String fileId) {
        try {
            Path filePath = testExecutionService.getResultFilePath(fileId);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> getTestStatus(@PathVariable String fileId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> status = testExecutionService.getExecutionStatus(fileId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
