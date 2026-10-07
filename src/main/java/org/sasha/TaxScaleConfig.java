package org.sasha;

import java.math.BigDecimal;
import org.sasha.exception.ConfigLoadException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Tax scale configuration
 */
public class TaxScaleConfig {
    private final NavigableMap<BigDecimal, BigDecimal> scale;

    // key = band lower bound (inclusive); salaries below the lowest key are rejected
    private static final NavigableMap<BigDecimal, BigDecimal> defaultScale = Collections.unmodifiableNavigableMap(
            new TreeMap<BigDecimal, BigDecimal>(Map.of(
                    BigDecimal.ZERO, new BigDecimal("1.89"),
                    BigDecimal.valueOf(10_000L), new BigDecimal("2.5"),
                    BigDecimal.valueOf(20_000), new BigDecimal("4.75"),
                    BigDecimal.valueOf(50_000), new BigDecimal("8.25"),
                    BigDecimal.valueOf(100_000), new BigDecimal("10.5")
            )));

    /**
     * Constructor with tax bands (floor-based lookups in keys (range) for corresponding tax % amount)
     * @param scale NavigableMap with bands
     */
    public TaxScaleConfig(NavigableMap<BigDecimal, BigDecimal> scale){
        this.scale = scale;
    }

    /**
     * Constructs the instance with default tax bands
     * @return TaxScaleConfig with default bands
     */
    public static TaxScaleConfig getDefaultConfig(){
        return new TaxScaleConfig(defaultScale);
    }

    /**
     * Loads tax bands from a YAML or JSON file (JSON is parsed as YAML, so one parser covers both):
     * {@code bands: [{from: <lower bound>, rate: <percent>}, ...]}
     * @param filePath path to the .yaml or .json file
     * @return TaxScaleConfig with the bands from the file
     * @throws ConfigLoadException if the file is unreadable or has no valid bands
     */
    public static TaxScaleConfig fromFile(Path filePath){
        try (Reader reader = Files.newBufferedReader(filePath)) {
            return parse(reader, filePath.toString());
        } catch (IOException e) {
            throw new ConfigLoadException("cannot load tax bands from " + filePath, e);
        }
    }

    /**
     * Loads tax bands over HTTPS from a YAML or JSON document in the same format as {@link #fromFile(Path)}
     * @param uri https URI of the document
     * @param timeout the timeout for connection
     * @return TaxScaleConfig with the bands from the document
     * @throws ConfigLoadException if the URI is not https, the request fails, or the document has no valid bands
     */
    public static TaxScaleConfig fromURI(URI uri, int timeout){
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ConfigLoadException("only https URIs are supported: " + uri);
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build()) {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (Reader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
                if (response.statusCode() != 200) {
                    throw new ConfigLoadException("HTTP %d from %s".formatted(response.statusCode(), uri));
                }
                return parse(reader, uri.toString());
            }
        } catch (IOException e) {
            throw new ConfigLoadException("cannot load tax bands from " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConfigLoadException("interrupted while loading tax bands from " + uri, e);
        }
    }

    public static TaxScaleConfig fromURI(URI uri){
        return TaxScaleConfig.fromURI(uri, 2);
    }

    private static TaxScaleConfig parse(Reader reader, String source){
        try {
            Map<String, List<Map<String, Object>>> root = new Yaml().load(reader);
            if (root == null || root.get("bands") == null || root.get("bands").isEmpty()) {
                throw new ConfigLoadException("No 'bands' defined in " + source);
            }
            NavigableMap<BigDecimal, BigDecimal> bands = new TreeMap<>();
            for (Map<String, Object> band : root.get("bands")) {
                bands.put(new BigDecimal(String.valueOf(band.get("from"))),
                        new BigDecimal(String.valueOf(band.get("rate"))));
            }
            return new TaxScaleConfig(bands);
        } catch (ConfigLoadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigLoadException("invalid tax bands in " + source, e);
        }
    }

    /**
     * Getter for tax bands config
     * @return NavigableMap with tax bands
     */
    public NavigableMap<BigDecimal, BigDecimal> getScale(){
        return scale;
    }
}
