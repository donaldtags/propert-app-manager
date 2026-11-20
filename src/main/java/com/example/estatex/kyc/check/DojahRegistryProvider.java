package com.example.estatex.kyc.check;

import com.example.estatex.kyc.IdDocumentType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * National-register lookups through Dojah (https://docs.dojah.io). Zimbabwe national IDs work out
 * of the box; more countries are added without code via DOJAH_EXTRA_ENDPOINTS, e.g.
 * "KE:NATIONAL_ID=/api/v1/ke/kyc/id?id=,NG:NATIONAL_ID=/api/v1/kyc/nin?nin=" (paths from Dojah's docs).
 *
 * Keys: DOJAH_APP_ID and DOJAH_SECRET_KEY. Without them this provider is simply off.
 */
@Component
public class DojahRegistryProvider implements RegistryProvider {
    private static final Logger log = LoggerFactory.getLogger(DojahRegistryProvider.class);

    private final String appId;
    private final String secretKey;
    private final String baseUrl;
    private final Map<String, String> endpoints = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DojahRegistryProvider(
            @Value("${app.idcheck.dojah.app-id:}") String appId,
            @Value("${app.idcheck.dojah.secret-key:}") String secretKey,
            @Value("${app.idcheck.dojah.base-url:https://api.dojah.io}") String baseUrl,
            @Value("${app.idcheck.dojah.extra-endpoints:}") String extraEndpoints,
            ObjectMapper objectMapper) {
        this.appId = appId == null ? "" : appId.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        endpoints.put("ZW:NATIONAL_ID", "/api/v1/zw/kyc/nin?id_number=");
        for (String entry : extraEndpoints.split(",")) {
            String[] kv = entry.trim().split("=", 2);
            if (kv.length == 2 && kv[0].contains(":")) {
                endpoints.put(kv[0].trim().toUpperCase(Locale.ROOT), kv[1].trim() + (kv[1].trim().endsWith("=") ? "" : "="));
            }
        }
        if (!isConfigured()) {
            log.info("Dojah ID register checks are off (set DOJAH_APP_ID and DOJAH_SECRET_KEY to turn them on).");
        }
    }

    @Override
    public String name() {
        return "dojah";
    }

    boolean isConfigured() {
        return !appId.isEmpty() && !secretKey.isEmpty();
    }

    @Override
    public boolean supports(String countryIso2, IdDocumentType type) {
        return isConfigured() && endpoints.containsKey(key(countryIso2, type));
    }

    @Override
    public RegistryLookup lookup(String countryIso2, IdDocumentType type, String idNumber, String legalFullName,
                                 LocalDate dateOfBirth) {
        String path = endpoints.get(key(countryIso2, type));
        if (path == null) return RegistryLookup.error("Dojah has no lookup set up for " + countryIso2 + " " + type);
        // Registers store the number without separators, e.g. 631234567F42.
        String cleaned = idNumber == null ? "" : idNumber.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + URLEncoder.encode(cleaned, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .header("AppId", appId)
                .header("Authorization", secretKey)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 404) {
                return new RegistryLookup(IdentityCheck.NOT_FOUND, null, null, null, "The register has no record of this ID number.");
            }
            if (code == 402) return RegistryLookup.error("Dojah account is out of credit - top up the Dojah wallet.");
            if (code == 401 || code == 403) return RegistryLookup.error("Dojah rejected the API keys.");
            if (code >= 400) return RegistryLookup.error("Dojah answered HTTP " + code + ".");
            JsonNode entity = objectMapper.readTree(response.body()).path("entity");
            if (entity.isMissingNode() || entity.isNull() || entity.isEmpty()) {
                return new RegistryLookup(IdentityCheck.NOT_FOUND, null, null, null, "The register has no record of this ID number.");
            }
            return compare(entity, legalFullName, dateOfBirth);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RegistryLookup.error("The register lookup was interrupted.");
        } catch (Exception e) {
            log.warn("Dojah lookup failed: {}", e.toString());
            return RegistryLookup.error("Couldn't reach the register (" + e.getClass().getSimpleName() + ").");
        }
    }

    /** Compares what the register holds with what the person typed. */
    static RegistryLookup compare(JsonNode entity, String legalFullName, LocalDate dateOfBirth) {
        String first = text(entity, "first_name", "firstname", "given_names");
        String middle = text(entity, "middle_name", "middlename");
        String last = text(entity, "surname", "last_name", "lastname");
        String registryName = String.join(" ", List.of(first, middle, last).stream().filter(s -> !s.isBlank()).toList());
        LocalDate registryDob = date(text(entity, "date_of_birth", "dob", "birth_date"));
        String sex = text(entity, "sex", "gender");
        String death = text(entity, "date_of_death", "death_date");
        if (!death.isBlank()) {
            return new RegistryLookup(IdentityCheck.DECEASED, registryName, registryDob, sex,
                    "The register records this person as deceased (" + death + ").");
        }

        Set<String> typed = parts(legalFullName);
        boolean surnameOk = !parts(last).isEmpty() && typed.containsAll(parts(last));
        boolean givenOk = parts(first).stream().anyMatch(typed::contains);
        boolean nameOk = surnameOk && givenOk;
        boolean dobOk = registryDob != null && registryDob.equals(dateOfBirth);
        String status = nameOk && dobOk ? IdentityCheck.MATCH : nameOk || dobOk ? IdentityCheck.PARTIAL : IdentityCheck.NO_MATCH;
        String detail = "Register: " + (registryName.isBlank() ? "(no name)" : registryName)
                + (registryDob == null ? "" : ", born " + registryDob)
                + " - name " + (nameOk ? "matches" : "differs") + ", date of birth " + (dobOk ? "matches" : "differs") + ".";
        return new RegistryLookup(status, registryName, registryDob, sex, detail);
    }

    private static String key(String country, IdDocumentType type) {
        return (country == null ? "ZW" : country.toUpperCase(Locale.ROOT)) + ":" + type.name();
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.path(n);
            if (!v.isMissingNode() && !v.isNull() && !v.asString("").isBlank()) return v.asString("").trim();
        }
        return "";
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (Exception e) {
            return null;
        }
    }

    static Set<String> parts(String name) {
        if (name == null) return Set.of();
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^a-z]+")).filter(p -> p.length() > 1).collect(Collectors.toSet());
    }
}
