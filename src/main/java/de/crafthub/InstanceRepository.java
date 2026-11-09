package de.crafthub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class InstanceRepository {
    private static final TypeReference<List<GameInstance>> INSTANCE_LIST = new TypeReference<>() {
    };

    private final Path root;
    private final Path instancesFile;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public InstanceRepository(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.instancesFile = this.root.resolve("instances.json");
        Files.createDirectories(this.root.resolve("instances"));
    }

    public synchronized List<GameInstance> list() throws IOException {
        if (!Files.exists(instancesFile)) {
            return List.of();
        }
        List<GameInstance> instances = mapper.readValue(instancesFile.toFile(), INSTANCE_LIST);
        return instances.stream().sorted(Comparator.comparingLong(GameInstance::createdAt).reversed()).toList();
    }

    public synchronized GameInstance create(String name, String version, GameInstance.Loader loader) throws IOException {
        String normalizedName = name == null ? "" : name.strip();
        if (normalizedName.isEmpty() || normalizedName.length() > 48) {
            throw new IllegalArgumentException("Der Profilname muss zwischen 1 und 48 Zeichen lang sein.");
        }
        if (version == null || version.isBlank() || loader == null) {
            throw new IllegalArgumentException("Bitte eine Minecraft-Version und einen Loader auswählen.");
        }
        if (list().stream().anyMatch(instance -> instance.name().equalsIgnoreCase(normalizedName))) {
            throw new IllegalArgumentException("Ein Profil mit diesem Namen existiert bereits.");
        }

        GameInstance instance = new GameInstance(UUID.randomUUID().toString(), normalizedName,
                version, loader, System.currentTimeMillis());
        Path directory = instanceDirectory(instance.id());
        Files.createDirectories(directory.resolve("mods"));
        List<GameInstance> instances = new ArrayList<>(list());
        instances.add(instance);
        try {
            save(instances);
        } catch (IOException exception) {
            deleteTree(directory);
            throw exception;
        }
        return instance;
    }

    public synchronized void delete(String id) throws IOException {
        GameInstance existing = list().stream().filter(instance -> instance.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Das Profil wurde nicht gefunden."));
        deleteTree(instanceDirectory(existing.id()));
        save(list().stream().filter(instance -> !instance.id().equals(id)).toList());
    }

    public synchronized GameInstance copy(String id) throws IOException {
        GameInstance source = list().stream().filter(instance -> instance.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Die Instanz wurde nicht gefunden."));
        String suffix = " (Kopie)";
        String base = source.name().substring(0, Math.min(source.name().length(), 48 - suffix.length()));
        String name = base + suffix;
        int copyNumber = 2;
        while (containsName(name)) {
            String numberedSuffix = " (Kopie " + copyNumber++ + ")";
            name = source.name().substring(0, Math.min(source.name().length(), 48 - numberedSuffix.length()))
                    + numberedSuffix;
        }

        GameInstance duplicate = create(name, source.version(), source.loader());
        try (Stream<Path> files = Files.list(modsDirectory(source))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.copy(file, modsDirectory(duplicate).resolve(file.getFileName()),
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            delete(duplicate.id());
            throw exception;
        }
        return duplicate;
    }

    private boolean containsName(String name) throws IOException {
        return list().stream().anyMatch(instance -> instance.name().equalsIgnoreCase(name));
    }

    public Path modsDirectory(GameInstance instance) {
        return instanceDirectory(instance.id()).resolve("mods");
    }

    public Path instanceDirectory(GameInstance instance) {
        return instanceDirectory(instance.id());
    }

    public long modCount(GameInstance instance) throws IOException {
        try (Stream<Path> files = Files.list(modsDirectory(instance))) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .count();
        }
    }

    public Path root() {
        return root;
    }

    private Path instanceDirectory(String id) {
        UUID uuid = UUID.fromString(id);
        return root.resolve("instances").resolve(uuid.toString()).normalize();
    }

    private void save(List<GameInstance> instances) throws IOException {
        Path temporaryFile = Files.createTempFile(root, "instances-", ".json.tmp");
        try {
            mapper.writeValue(temporaryFile.toFile(), instances);
            move(temporaryFile, instancesFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}