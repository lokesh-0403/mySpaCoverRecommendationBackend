package zasyaSolutions.mySpaCoverSkuRecommendation.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import zasyaSolutions.mySpaCoverSkuRecommendation.model.SpaCoverDimension;

public class PayloadGenerator {

    private PayloadGenerator() {
    }

    public static List<List<String>> generatePayloads(Path csvPath) {
        List<List<String>> payloads = new ArrayList<>();
        List<SpaCoverDimension> dimensions = CsvDimensionReader.readDimensions(csvPath);
        List<SpaCoverDimension> rectangleDimensions = new ArrayList<>();
        List<SpaCoverDimension> circleDimensions = new ArrayList<>();

        for (SpaCoverDimension original : dimensions) {
            if (FallbackDimensionGenerator.isCircleDimension(original)) {
                circleDimensions.add(original);
            } else {
                rectangleDimensions.add(original);
            }
        }

        List<SpaCoverDimension> orderedDimensions = new ArrayList<>(rectangleDimensions);
        orderedDimensions.addAll(circleDimensions);

        for (SpaCoverDimension original : orderedDimensions) {
            List<SpaCoverDimension> fallbacks = FallbackDimensionGenerator.isCircleDimension(original)
                ? FallbackDimensionGenerator.generateFallbacksForStaticSecondDimension(original)
                : FallbackDimensionGenerator.generateFallbacks(original);
            List<String> skuPayload = FallbackDimensionGenerator.generateSKUArray(fallbacks);
            payloads.add(skuPayload);
        }

        return payloads;
    }
}
