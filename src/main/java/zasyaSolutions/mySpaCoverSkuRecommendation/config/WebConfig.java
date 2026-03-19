package zasyaSolutions.mySpaCoverSkuRecommendation.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> configuredOrigins = appProperties.getCors()
            .getAllowedOrigins()
            .stream()
            .filter(StringUtils::hasText)
            .toList();

        String[] allowedOrigins = configuredOrigins.isEmpty()
            ? new String[] { "http://localhost:3000" }
            : configuredOrigins.toArray(String[]::new);

        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
