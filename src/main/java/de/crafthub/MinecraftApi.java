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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class MinecraftApi {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<MinecraftVersion> fetchVersions() throws IOException, InterruptedException {
        JsonNode manifest = getJson(VERSION_MANIFEST);
        List<MinecraftVersion> versions = new ArrayList<>();
        for (JsonNode version : manifest.path("versions")) {
            if ("release".equals(version.path("type").asText())) {
                versions.add(new MinecraftVersion(version.path("id").asText(), version.path("type").asText()));
            }
        }
        if (versions.isEmpty()) {
            throw new IOException("Die offizielle Minecraft-Versionsliste ist leer.");
        }
        return List.copyOf(versions);
    }

    public List<ModResult> searchMods(String query, GameInstance instance) throws IOException, InterruptedException {
        if (instance.loader() == GameInstance.Loader.VANILLA) {
            throw new IllegalArgumentException("Modrinth-Mods brauchen Fabric, Forge, NeoForge oder Quilt.");
        }
        String facets = "[[\"project_type:mod\"],[\"versions:" + jsonFacet(instance.version())
                + "\"],[\"categories:" + instance.loader().modrinthName() + "\"]]";
        String url = MODRINTH_API + "/search?query=" + encode(query.strip()) + "&facets=" + encode(facets)
                + "&limit=20&index=relevance";
        JsonNode hits = getJson(url).path("hits");
        List<ModResult> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            results.add(new ModResult(
                    hit.path("project_id").asText(),
                    hit.path("title").asText("Unbenannte Mod"),
                    hit.path("description").asText(),
                    hit.path("downloads").asLong(),
                    hit.path("icon_url").asText("")));
        }
        return List.copyOf(results);
    }

    public Path installMod(ModResult mod, GameInstance instance, Path modsDirectory)
            throws IOException, InterruptedException {
        if (instance.loader() == GameInstance.Loader.VANILLA) {
            throw new IllegalArgumentException("Mods koennen nicht in Vanilla-Profile installiert werden.");
        }
        JsonNode versions = getJson(MODRINTH_API + "/project/" + encodePath(mod.projectId()) + "/version");
        JsonNode selectedVersion = null;
        for (JsonNode version : versions) {
            boolean matchingGame = contains(version.path("game_versions"), instance.version());
            boolean matchingLoader = contains(version.path("loaders"), instance.loader().modrinthName());
            if (matchingGame && matchingLoader) {
                selectedVersion = version;
                break;
            }
        }
        if (selectedVersion == null || !selectedVersion.path("files").isArray()
                || selectedVersion.path("files").isEmpty()) {
            throw new IOException("Für diese Mod wurde keine passende Datei gefunden.");
        }

        JsonNode file = selectedVersion.path("files").path(0);
        for (JsonNode candidate : selectedVersion.path("files")) {
            if (candidate.path("primary").asBoolean()) {
                file = candidate;
                break;
            }
        }
        String filename = Path.of(file.path("filename").asText()).getFileName().toString();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Die ausgewählte Mod-Datei ist keine JAR-Datei.");
        }
        URI downloadUri = URI.create(file.path("url").asText());
        if (!"https".equalsIgnoreCase(downloadUri.getScheme())) {
            throw new IOException("Der Download-Link ist nicht sicher.");
        }

        Files.createDirectories(modsDirectory);
        Path destination = modsDirectory.resolve(filename).normalize();
        if (!destination.getParent().equals(modsDirectory.toAbsolutePath().normalize())) {
            throw new IOException("Ungültiger Dateiname.");
        }
        Path temporaryFile = Files.createTempFile(modsDirectory, ".crafthub-", ".download");
        try {
            HttpResponse<Path> response = client.send(request(downloadUri.toString()).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temporaryFile));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Mod-Download fehlgeschlagen (HTTP " + response.statusCode() + ").");
            }
            String expectedHash = file.path("hashes").path("sha512").asText("");
            if (!expectedHash.isBlank() && !expectedHash.equalsIgnoreCase(sha512(temporaryFile))) {
                throw new IOException("Die Prüfsumme der heruntergeladenen Mod stimmt nicht.");
            }
            move(temporaryFile, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Anfrage fehlgeschlagen (HTTP " + response.statusCode() + ").");
        }
        return MAPPER.readTree(response.body());
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "CraftHub/1.0.0")
                .header("Accept", "application/json");
    }

    private static boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equalsIgnoreCase(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String jsonFacet(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String sha512(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 wird von dieser Java-Version nicht unterstuetzt.", exception);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record MinecraftVersion(String id, String type) {
        @Override
        public String toString() {
            return id;
        }
    }

    public record ModResult(String projectId, String title, String description, long downloads, String iconUrl) {
    }
}