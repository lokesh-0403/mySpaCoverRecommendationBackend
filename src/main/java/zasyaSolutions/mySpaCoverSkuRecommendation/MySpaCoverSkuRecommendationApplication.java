package zasyaSolutions.mySpaCoverSkuRecommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MySpaCoverSkuRecommendationApplication {

	private static final Logger log = LoggerFactory.getLogger(MySpaCoverSkuRecommendationApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(MySpaCoverSkuRecommendationApplication.class, args);
		log.info("API server started");
	}

}
