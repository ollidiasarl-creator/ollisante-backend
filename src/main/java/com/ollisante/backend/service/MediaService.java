package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Images des actualités, stockées sur le disque du serveur. Le type est vérifié sur le contenu du
 * fichier (pas sur son nom) et le nom est généré : rien de ce qu'envoie le client ne sert de chemin.
 */
@Service
public class MediaService {

    private static final Pattern SAFE_NAME = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");

    private final Path dir;

    public MediaService(@Value("${ollisante.media.dir}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FICHIER_VIDE", "Aucun fichier reçu.");
        }
        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = imageExtension(in.readNBytes(12));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FICHIER_ILLISIBLE", "Fichier illisible.");
        }
        if (ext == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FORMAT_IMAGE",
                    "Formats acceptés : JPEG, PNG ou WebP.");
        }
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STOCKAGE", "Enregistrement impossible.");
        }
        return "/media/" + name;
    }

    /** Fichier existant au nom sûr, sinon vide. */
    public Optional<Path> find(String name) {
        if (!SAFE_NAME.matcher(name).matches()) return Optional.empty();
        Path path = dir.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    static String imageExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
