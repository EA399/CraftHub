package de.crafthub;

public record GameInstance(String id, String name, String version, Loader loader, long createdAt) {
    public enum Loader {
        VANILLA("Vanilla", ""),
        FABRIC("Fabric", "fabric"),
        FORGE("Forge", "forge"),
        NEOFORGE("NeoForge", "neoforge"),
        QUILT("Quilt", "quilt");

        private final String label;
        private final String modrinthName;

        Loader(String label, String modrinthName) {
            this.label = label;
            this.modrinthName = modrinthName;
        }

        public String label() {
            return label;
        }

        public String modrinthName() {
            return modrinthName;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}