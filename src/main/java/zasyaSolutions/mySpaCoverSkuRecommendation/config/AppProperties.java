package zasyaSolutions.mySpaCoverSkuRecommendation.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Storage storage = new Storage();
    private final Inventory inventory = new Inventory();
    private final Execution execution = new Execution();

    public Cors getCors() {
        return cors;
    }

    public Storage getStorage() {
        return storage;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Execution getExecution() {
        return execution;
    }

    public Path getStorageRootPath() {
        return Path.of(storage.getRoot()).toAbsolutePath().normalize();
    }

    public Path getUploadsRootPath() {
        return getStorageRootPath().resolve(storage.getUploadsDir()).normalize();
    }

    public Path getResultsRootPath() {
        return getStorageRootPath().resolve(storage.getResultsDir()).normalize();
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Storage {

        private String root = "data";
        private String uploadsDir = "uploads";
        private String resultsDir = "results";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getUploadsDir() {
            return uploadsDir;
        }

        public void setUploadsDir(String uploadsDir) {
            this.uploadsDir = uploadsDir;
        }

        public String getResultsDir() {
            return resultsDir;
        }

        public void setResultsDir(String resultsDir) {
            this.resultsDir = resultsDir;
        }
    }

    public static class Inventory {

        private String baseUrl = "https://inventory-api.myspacover.com";
        private String loginEndpoint = "/auth/login/web";
        private String inventoryEndpoint = "/inventory/inhand-quantity";
        private String loginEmail = "";
        private String loginPassword = "";
        private String webhookKey = "";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(60);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginEndpoint() {
            return loginEndpoint;
        }

        public void setLoginEndpoint(String loginEndpoint) {
            this.loginEndpoint = loginEndpoint;
        }

        public String getInventoryEndpoint() {
            return inventoryEndpoint;
        }

        public void setInventoryEndpoint(String inventoryEndpoint) {
            this.inventoryEndpoint = inventoryEndpoint;
        }

        public String getLoginEmail() {
            return loginEmail;
        }

        public void setLoginEmail(String loginEmail) {
            this.loginEmail = loginEmail;
        }

        public String getLoginPassword() {
            return loginPassword;
        }

        public void setLoginPassword(String loginPassword) {
            this.loginPassword = loginPassword;
        }

        public String getWebhookKey() {
            return webhookKey;
        }

        public void setWebhookKey(String webhookKey) {
            this.webhookKey = webhookKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Execution {

        private int maxParallelJobs = 4;

        public int getMaxParallelJobs() {
            return maxParallelJobs;
        }

        public void setMaxParallelJobs(int maxParallelJobs) {
            this.maxParallelJobs = maxParallelJobs;
        }
    }
}
