package com.example.contract.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        resolveEnvFile(Path.of("").toAbsolutePath()).ifPresent(DotenvLoader::loadFrom);
    }

    static Optional<Path> resolveEnvFile(Path workingDirectory) {
        Path localEnv = workingDirectory.resolve(".env").normalize();
        if (Files.isRegularFile(localEnv)) {
            return Optional.of(localEnv);
        }

        Path backendEnv = workingDirectory.resolve("backend").resolve(".env").normalize();
        if (Files.isRegularFile(backendEnv)) {
            return Optional.of(backendEnv);
        }

        return Optional.empty();
    }

    static void loadFrom(Path path) {
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                applyLine(line);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取 .env 文件失败：" + exception.getMessage(), exception);
        }
    }

    private static void applyLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isBlank() || trimmed.startsWith("#")) {
            return;
        }

        int splitIndex = trimmed.indexOf('=');
        if (splitIndex <= 0) {
            return;
        }

        String key = trimmed.substring(0, splitIndex).trim();
        String value = unquote(trimmed.substring(splitIndex + 1).trim());
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}

