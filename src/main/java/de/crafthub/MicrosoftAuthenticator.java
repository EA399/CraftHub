package de.crafthub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MicrosoftAuthenticator {
    private static final String CLIENT_ID_ENV = "CRAFTHUB_MICROSOFT_CLIENT_ID";
    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String clientId = System.getenv(CLIENT_ID_ENV);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public DeviceCode requestDeviceCode() throws IOException, InterruptedException {
        String form = "client_id=" + encode(requireClientId())
            + "&scope=" + encode("XboxLive.SignIn XboxLive.offline_access");
        HttpRequest request = HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        JsonNode response = sendJson(request);
        return new DeviceCode(
                requiredText(response, "device_code"),
                requiredText(response, "user_code"),
                requiredText(response, "verification_uri"),
                response.path("expires_in").asLong(900),
                response.path("interval").asInt(5));
    }

    public MinecraftAccount completeLogin(DeviceCode deviceCode, AtomicBoolean cancelled)
            throws IOException, InterruptedException {
        String microsoftToken = pollForMicrosoftToken(deviceCode, cancelled);
        JsonNode xboxResponse = authenticateXbox(microsoftToken);
        JsonNode xboxClaims = xboxResponse.path("DisplayClaims").path("xui").path(0);
        String userHash = requiredText(xboxClaims, "uhs");
        String xstsToken = authorizeXsts(requiredText(xboxResponse, "Token"));
        String minecraftToken = authenticateMinecraft(userHash, xstsToken);
        JsonNode profile = fetchMinecraftProfile(minecraftToken);
        return new MinecraftAccount(requiredText(profile, "name"), requiredText(profile, "id"));
    }

    private String pollForMicrosoftToken(DeviceCode deviceCode, AtomicBoolean cancelled)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(deviceCode.expiresIn()).toNanos();
        int interval = Math.max(1, deviceCode.interval());
        while (System.nanoTime() < deadline) {
            if (cancelled.get()) {
                throw new IOException("Anmeldung abgebrochen.");
            }
            Thread.sleep(interval * 1000L);
            if (cancelled.get()) {
                throw new IOException("Anmeldung abgebrochen.");
            }
            String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + encode(requireClientId())
                    + "&device_code=" + encode(deviceCode.deviceCode());
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = MAPPER.readTree(response.body());
            if (response.statusCode() / 100 == 2) {
                return requiredText(body, "access_token");
            }
            String error = body.path("error").asText();
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            if ("authorization_declined".equals(error)) {
                throw new IOException("Die Anmeldung wurde im Browser abgelehnt.");
            }
            if ("expired_token".equals(error)) {
                throw new IOException("Der Gerätecode ist abgelaufen. Bitte starte die Anmeldung erneut.");
            }
            throw oauthError(body, response.statusCode());
        }
        throw new IOException("Der Gerätecode ist abgelaufen. Bitte starte die Anmeldung erneut.");
    }

    private JsonNode authenticateXbox(String microsoftToken) throws IOException, InterruptedException {
        String ticket = microsoftToken.startsWith("d=") ? microsoftToken : "d=" + microsoftToken;
        Map<String, Object> body = Map.of(
                "Properties", Map.of("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", ticket),
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT");
        return postJson("https://user.auth.xboxlive.com/user/authenticate", body,
                Map.of("x-xbl-contract-version", "1"));
    }

    private String authorizeXsts(String xboxToken) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "Properties", Map.of("SandboxId", "RETAIL", "UserTokens", new String[]{xboxToken}),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT");
        try {
            JsonNode response = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body,
                    Map.of("x-xbl-contract-version", "1"));
            return requiredText(response, "Token");
        } catch (HttpFailure failure) {
            JsonNode response = failure.response();
            String errorCode = response.path("XErr").asText();
            String message = switch (errorCode) {
                case "2148916233" -> "Das Microsoft-Konto hat noch kein Xbox-Profil. Lege zuerst eines an.";
                case "2148916235" -> "Xbox Live ist in der Region dieses Kontos nicht verfügbar.";
                case "2148916238" -> "Das Kinderkonto braucht die Zustimmung eines Erziehungsberechtigten.";
                default -> "Xbox konnte das Konto nicht freigeben (" + errorCode + ").";
            };
            throw new IOException(message, failure);
        }
    }

    private String authenticateMinecraft(String userHash, String xstsToken) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonNode response = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body,
                Map.of());
        return requiredText(response, "access_token");
    }

    private JsonNode fetchMinecraftProfile(String minecraftToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + minecraftToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException("Für dieses Microsoft-Konto wurde kein Minecraft-Java-Profil gefunden.");
        }
        return parseResponse(response);
    }

    private JsonNode postJson(String url, Object body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        headers.forEach(request::header);
        request.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        return parseResponse(client.send(request.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode sendJson(HttpRequest request) throws IOException, InterruptedException {
        return parseResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static JsonNode parseResponse(HttpResponse<String> response) throws IOException {
        JsonNode body = MAPPER.readTree(response.body());
        if (response.statusCode() / 100 != 2) {
            throw new HttpFailure(response.statusCode(), body);
        }
        return body;
    }

    private static IOException oauthError(JsonNode response, int statusCode) {
        String description = response.path("error_description").asText();
        if (description.isBlank()) {
            description = response.path("error").asText("HTTP " + statusCode);
        }
        return new IOException("Microsoft-Anmeldung fehlgeschlagen: " + description);
    }

    private static String requiredText(JsonNode json, String key) throws IOException {
        String value = json.path(key).asText();
        if (value.isBlank()) {
            throw new IOException("Die Anmeldung lieferte kein Feld '" + key + "'.");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requireClientId() throws IOException {
        if (clientId == null || clientId.isBlank()) {
            throw new IOException("Microsoft-Anmeldung ist noch nicht konfiguriert. Registriere eine eigene App in "
                    + "Microsoft Entra und setze CRAFTHUB_MICROSOFT_CLIENT_ID auf ihre Anwendungs-ID.");
        }
        try {
            return java.util.UUID.fromString(clientId.strip()).toString();
        } catch (IllegalArgumentException exception) {
            throw new IOException("CRAFTHUB_MICROSOFT_CLIENT_ID muss eine gültige Microsoft-Anwendungs-ID sein.");
        }
    }

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, long expiresIn, int interval) {
    }

    public record MinecraftAccount(String username, String uuid) {
    }

    private static final class HttpFailure extends IOException {
        private final JsonNode response;

        private HttpFailure(int statusCode, JsonNode response) {
            super("HTTP " + statusCode);
            this.response = response;
        }

        private JsonNode response() {
            return response;
        }
    }
}