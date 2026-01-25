package com.quantor.cli.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Enterprise+ audit & integrity utilities.
 *
 * - Writes append-only audit.log entries
 * - Maintains rolling SHA-256 checksums for config files
 */
final class AuditUtil {

    private AuditUtil() {}

    static void appendAudit(Path profileDir, String action, String key, Path affectedFile) {
        try {
            Path auditDir = profileDir.resolve("audit");
            Files.createDirectories(auditDir);

            String fileHash = sha256Hex(affectedFile);
            String line = Instant.now().toString()
                    + " | " + action
                    + " | key=" + key
                    + " | file=" + affectedFile.getFileName()
                    + " | sha256=" + fileHash
                    + System.lineSeparator();

            Files.writeString(auditDir.resolve("audit.log"), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            writeRollingChecksums(profileDir);

        } catch (Exception ignored) {
            // audit must never break primary command flow
        }
    }

    static void writeRollingChecksums(Path profileDir) throws IOException {
        Path auditDir = profileDir.resolve("audit");
        Files.createDirectories(auditDir);

        List<Path> files = new ArrayList<>();
        // Include base config files
        addIfExists(files, profileDir.resolve("config.properties"));
        addIfExists(files, profileDir.resolve("secrets.properties"));
        addIfExists(files, profileDir.resolve(".env"));

        // Include account files
        Path accounts = profileDir.resolve("accounts");
        if (Files.exists(accounts) && Files.isDirectory(accounts)) {
            try (var stream = Files.walk(accounts)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".properties") || p.getFileName().toString().equals(".env"))
                        .forEach(files::add);
            }
        }

        files.sort(Comparator.comparing(Path::toString));

        StringBuilder sb = new StringBuilder();
        for (Path f : files) {
            sb.append(sha256Hex(f)).append("  ").append(profileDir.relativize(f)).append(System.lineSeparator());
        }

        Files.writeString(auditDir.resolve("config.sha256"), sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void addIfExists(List<Path> files, Path p) {
        if (Files.exists(p) && Files.isRegularFile(p)) files.add(p);
    }

    private static String sha256Hex(Path file) throws IOException {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed on all standard Java runtimes; if it isn't available,
            // treat it as a fatal platform issue.
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }

        byte[] data = Files.readAllBytes(file);
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
