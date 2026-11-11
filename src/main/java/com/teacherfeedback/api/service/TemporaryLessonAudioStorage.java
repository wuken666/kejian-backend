package com.teacherfeedback.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Component
public class TemporaryLessonAudioStorage {
    private final Path rootDirectory;

    public TemporaryLessonAudioStorage(
        @Value("${services.asr.audio-temp-dir:data/lesson-audio}") String rootDirectory
    ) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
    }

    // 临时保存已上传分片，仅用于本节课结束后的说话人分离。
    public void store(Long sessionId, String clientSegmentId, MultipartFile audio) {
        String suffix = safeSuffix(audio.getOriginalFilename());
        Path sessionDirectory = sessionDirectory(sessionId);
        Path target = sessionDirectory.resolve(fileKey(clientSegmentId) + suffix).normalize();
        ensureInsideRoot(target);
        try {
            Files.createDirectories(sessionDirectory);
            Files.copy(audio.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                INTERNAL_SERVER_ERROR,
                "课堂音频暂存失败，请重试",
                exception
            );
        }
    }

    // 根据客户端分片编号查找临时音频，顺序由数据库中的 sequenceNumber 决定。
    public Optional<Path> find(Long sessionId, String clientSegmentId) {
        Path sessionDirectory = sessionDirectory(sessionId);
        if (!Files.isDirectory(sessionDirectory)) return Optional.empty();
        String prefix = fileKey(clientSegmentId);
        try (var paths = Files.list(sessionDirectory)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix + "."))
                .findFirst();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                INTERNAL_SERVER_ERROR,
                "课堂音频读取失败，请重试",
                exception
            );
        }
    }

    // AI 整理成功并结束课堂后立即删除原始音频，系统只长期保留文字和反馈。
    public void deleteSession(Long sessionId) {
        Path sessionDirectory = sessionDirectory(sessionId);
        if (!Files.isDirectory(sessionDirectory)) return;
        try {
            // Windows requires the directory listing handle to close before deleting the directory.
            try (var paths = Files.list(sessionDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(sessionDirectory);
        } catch (IOException | IllegalStateException exception) {
            throw new ResponseStatusException(
                INTERNAL_SERVER_ERROR,
                "课堂音频清理失败",
                exception
            );
        }
    }

    private Path sessionDirectory(Long sessionId) {
        Path directory = rootDirectory.resolve(String.valueOf(sessionId)).normalize();
        ensureInsideRoot(directory);
        return directory;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(rootDirectory)) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的课堂音频路径");
        }
    }

    private String safeSuffix(String filename) {
        if (filename == null) return ".webm";
        int index = filename.lastIndexOf('.');
        String suffix = index < 0 ? ".webm" : filename.substring(index).toLowerCase();
        return switch (suffix) {
            case ".wav", ".webm", ".m4a", ".mp3", ".ogg", ".pcm" -> suffix;
            default -> ".webm";
        };
    }

    private String fileKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
