package com.bigphil.mergehell.assets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Stable sprite IDs mapped to UTF-8 properties resources; resource paths are classpath-rooted. */
public final class AssetCatalog {
    private final Map<String, String> spriteResources;

    public AssetCatalog(Map<String, String> spriteResources) {
        LinkedHashMap<String, String> checked = new LinkedHashMap<>();
        spriteResources.forEach((id, path) -> {
            identifier(id, "sprite ID");
            checked.put(id, resourcePath(path, ".properties"));
        });
        this.spriteResources = Collections.unmodifiableMap(checked);
    }

    public static AssetCatalog read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        requireVersion(properties);
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        for (String id : names(properties, "sprites", true)) {
            entries.put(id, required(properties, "sprite." + id));
        }
        return new AssetCatalog(entries);
    }

    public Map<String, String> spriteResources() { return spriteResources; }

    static void requireVersion(Properties properties) {
        if (!"1".equals(required(properties, "format"))) {
            throw new IllegalArgumentException("Unsupported asset metadata format");
        }
    }

    static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property: " + key);
        return value.trim();
    }

    static List<String> names(Properties properties, String key, boolean allowEmpty) {
        return names(properties, key, allowEmpty, false);
    }

    static List<String> names(Properties properties, String key, boolean allowEmpty, boolean allowDuplicates) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            if (allowEmpty) return List.of();
            throw new IllegalArgumentException("Missing property: " + key);
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String name = part.trim();
            identifier(name, key);
            if (!allowDuplicates && result.contains(name)) {
                throw new IllegalArgumentException("Duplicate name in " + key + ": " + name);
            }
            result.add(name);
        }
        return List.copyOf(result);
    }

    static String identifier(String value, String description) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value);
        }
        return value;
    }

    static String resourcePath(String value, String extension) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Empty resource path");
        String path = value.startsWith("/") ? value.substring(1) : value;
        if (!path.endsWith(extension) || path.contains("\\") || path.contains(":")
                || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("Invalid resource path: " + value);
        }
        for (String component : path.split("/", -1)) {
            if (component.isBlank() || component.equals(".") || component.equals("..")) {
                throw new IllegalArgumentException("Invalid resource path: " + value);
            }
        }
        return path;
    }
}
