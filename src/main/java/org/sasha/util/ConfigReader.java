package org.sasha.util;

import org.sasha.entity.TaxScaleConfig;
import org.sasha.exception.CalculationInputException;
import org.sasha.exception.ConfigLoadException;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Loads {@link TaxScaleConfig} from files and URIs.
 */
public final class ConfigReader {
    private ConfigReader(){}

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
     * Parses file in line-delimited format key:value\nkey:value
     * @param path to file
     * @return tax brackets map
     */
    public static TaxScaleConfig fromFlatFile(Path path){
        try (Stream<String> lines = Files.lines(path)){
            return parseFlat(lines);
        } catch (IOException ex){
            throw new CalculationInputException("cannot parse bands from file %s: %s".formatted(path, ex));
        }
    }

    /**
     * Parses a classpath resource in the same line-delimited format as {@link #fromFlatFile(Path)}
     * @param name resource name, e.g. "/default_tax_bands.txt"
     * @return tax brackets map
     * @throws ConfigLoadException if the resource is missing or unreadable
     */
    public static TaxScaleConfig fromFlatResource(String name){
        try (InputStream in = ConfigReader.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new ConfigLoadException("classpath resource not found: " + name);
            }
            return parseFlat(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines());
        } catch (IOException ex) {
            throw new ConfigLoadException("cannot read classpath resource " + name, ex);
        }
    }

    private static TaxScaleConfig parseFlat(Stream<String> lines){
        HashMap<BigDecimal, BigDecimal> result = new HashMap<>();
        lines.filter(l -> !l.isBlank() && !l.stripLeading().startsWith("#")) // blank lines and # comments
                .map(l -> l.replaceAll("[ _]", ""))//tip and remove _ delimiters
                .map(l -> l.split(":", 2))
                .filter(p -> p.length == 2)
                .forEach(p -> result.put(
                        new BigDecimal(p[0]),
                        new BigDecimal(p[1])
                ));
        return new TaxScaleConfig(new TreeMap<>(result));
    }

    public static TaxScaleConfig fromFlatFile(String pathStr){
        return fromFlatFile(Path.of(pathStr));
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
        return fromURI(uri, 2);
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
}
