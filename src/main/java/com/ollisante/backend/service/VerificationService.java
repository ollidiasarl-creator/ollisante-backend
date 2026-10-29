package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pièces justificatives des médecins et des structures (carte de l'Ordre, pièce d'identité, RCCM, autorisation…),
 * rangées dans un dossier privé du serveur : seule l'équipe d'administration peut les ouvrir. S'y ajoutent les
 * alertes montrées à l'équipe avant de valider un compte (numéros ou coordonnées déjà utilisés, pièces manquantes,
 * signalements).
 */
@Service
public class VerificationService {

    public static final Set<String> KINDS = Set.of("ORDER_CARD", "ID_CARD", "DIPLOMA", "RCCM", "AUTHORIZATION", "OTHER");
    private static final int MAX_PER_OWNER = 10;

    public record Owner(String type, UUID id) {
    }

    public record DocumentInfo(UUID id, String kind, String contentType, long sizeBytes, Instant createdAt) {
    }

    public record Review(List<DocumentInfo> documents, List<String> alerts) {
    }

    private final Path dir;
    private final JdbcTemplate jdbc;
    private final PractitionerRepository practitioners;
    private final OrganizationRepository organizations;

    public VerificationService(@Value("${ollisante.documents.dir:documents}") String dir, JdbcTemplate jdbc,
                               PractitionerRepository practitioners, OrganizationRepository organizations) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.jdbc = jdbc;
        this.practitioners = practitioners;
        this.organizations = organizations;
    }

    /** Le médecin ou la structure du compte connecté. */
    public Owner ownerOf(UUID userId) {
        Optional<Practitioner> p = practitioners.findByUserId(userId);
        if (p.isPresent()) return new Owner("PRACTITIONER", p.get().getId());
        Optional<Organization> o = organizations.findByUserId(userId);
        if (o.isPresent()) return new Owner("ORGANIZATION", o.get().getId());
        throw new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PRO_REQUIS",
                "Réservé aux médecins et aux structures de santé.");
    }

    @Transactional
    public DocumentInfo store(Owner owner, String kind, MultipartFile file) {
        if (kind == null || !KINDS.contains(kind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_PIECE", "Type de pièce inconnu.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FICHIER_VIDE", "Aucun fichier reçu.");
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM verification_documents WHERE owner_type = ? AND owner_id = ?",
                Integer.class, owner.type(), owner.id());
        if (count != null && count >= MAX_PER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TROP_DE_PIECES", "Nombre maximal de pièces atteint (10).");
        }
        String[] type;
        try (InputStream in = file.getInputStream()) {
            type = detect(in.readNBytes(12));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FICHIER_ILLISIBLE", "Fichier illisible.");
        }
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FORMAT_PIECE", "Formats acceptés : photo (JPEG, PNG, WebP) ou PDF.");
        }
        UUID id = UUID.randomUUID();
        String name = id + "." + type[0];
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name));
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STOCKAGE", "Enregistrement impossible.");
        }
        Instant now = Instant.now();
        jdbc.update("INSERT INTO verification_documents (id, owner_type, owner_id, kind, file_name, content_type, size_bytes, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, owner.type(), owner.id(), kind, name, type[1], file.getSize(), Timestamp.from(now));
        return new DocumentInfo(id, kind, type[1], file.getSize(), now);
    }

    public List<DocumentInfo> list(Owner owner) {
        return jdbc.query("SELECT id, kind, content_type, size_bytes, created_at FROM verification_documents"
                        + " WHERE owner_type = ? AND owner_id = ? ORDER BY created_at",
                (rs, n) -> new DocumentInfo(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("content_type"), rs.getLong("size_bytes"), rs.getTimestamp("created_at").toInstant()),
                owner.type(), owner.id());
    }

    /** Suppression par le titulaire (pièce envoyée par erreur). */
    @Transactional
    public void delete(Owner owner, UUID id) {
        List<String> names = jdbc.queryForList("DELETE FROM verification_documents WHERE id = ? AND owner_type = ? AND owner_id = ?"
                + " RETURNING file_name", String.class, id, owner.type(), owner.id());
        if (names.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "PIECE_INTROUVABLE", "Pièce introuvable.");
        try {
            Files.deleteIfExists(dir.resolve(names.get(0)));
        } catch (IOException ignored) {
            // le fichier sera retrouvé orphelin, sans conséquence
        }
    }

    /** Fichier d'une pièce, pour l'équipe d'administration : chemin et type. */
    public Optional<Object[]> file(UUID id) {
        return jdbc.query("SELECT file_name, content_type FROM verification_documents WHERE id = ?",
                (rs, n) -> new Object[] {dir.resolve(rs.getString("file_name")), rs.getString("content_type")}, id).stream().findFirst();
    }

    /** Ce que l'équipe voit avant de valider : les pièces et les points à contrôler. */
    @Transactional(readOnly = true)
    public Review review(Owner owner) {
        List<DocumentInfo> docs = list(owner);
        List<String> alerts = new ArrayList<>();
        if (docs.isEmpty()) alerts.add("Aucune pièce justificative envoyée.");
        if ("PRACTITIONER".equals(owner.type())) {
            practitioners.findById(owner.id()).ifPresent(p -> {
                for (String other : jdbc.queryForList("SELECT first_name || ' ' || last_name FROM practitioners WHERE id <> ?"
                        + " AND upper(regexp_replace(license_number, '[^A-Za-z0-9]', '', 'g')) = upper(regexp_replace(?, '[^A-Za-z0-9]', '', 'g'))",
                        String.class, p.getId(), p.getLicenseNumber())) {
                    alerts.add("Numéro d'ordre déjà utilisé par Dr " + other + ".");
                }
                if (p.getUser() != null) phoneAlerts(alerts, p.getUser().getPhone(), p.getUser().getId());
                if (p.isHiddenByReports()) alerts.add("Profil masqué au public après des signalements.");
            });
        } else {
            organizations.findById(owner.id()).ifPresent(o -> {
                for (String other : jdbc.queryForList("SELECT name FROM organizations WHERE id <> ?"
                        + " AND upper(regexp_replace(registration_number, '[^A-Za-z0-9]', '', 'g')) = upper(regexp_replace(?, '[^A-Za-z0-9]', '', 'g'))",
                        String.class, o.getId(), o.getRegistrationNumber())) {
                    alerts.add("Numéro d'immatriculation déjà utilisé par « " + other + " ».");
                }
                for (String other : jdbc.queryForList("SELECT name FROM organizations WHERE id <> ? AND lower(email) = lower(?)",
                        String.class, o.getId(), o.getEmail())) {
                    alerts.add("E-mail de contact déjà utilisé par « " + other + " ».");
                }
                phoneAlerts(alerts, o.getPhone(), o.getUser() == null ? null : o.getUser().getId());
            });
        }
        if ("PRACTITIONER".equals(owner.type())) {
            Integer reports = jdbc.queryForObject("SELECT count(*) FROM profile_reports WHERE status = 'OPEN'"
                    + " AND target_type = 'PRACTITIONER' AND target_id = ?", Integer.class, owner.id());
            if (reports != null && reports > 0) alerts.add(reports + " signalement(s) en attente.");
        }
        return new Review(docs, alerts);
    }

    /** Même numéro de téléphone (chiffres seuls) sur un autre compte. */
    private void phoneAlerts(List<String> alerts, String phone, UUID ownUserId) {
        if (phone == null || phone.replaceAll("\\D", "").length() < 6) return;
        Integer n = jdbc.queryForObject("SELECT count(*) FROM users WHERE regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = ?"
                + " AND id IS DISTINCT FROM ?", Integer.class, phone.replaceAll("\\D", ""), ownUserId);
        if (n != null && n > 0) alerts.add("Numéro de téléphone déjà utilisé par " + n + " autre(s) compte(s).");
    }

    /** Type réel d'après le contenu : {extension, type MIME}. */
    static String[] detect(byte[] h) {
        if (h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return new String[] {"pdf", "application/pdf"};
        String ext = MediaService.imageExtension(h);
        if (ext == null) return null;
        return new String[] {ext, "jpg".equals(ext) ? "image/jpeg" : "image/" + ext};
    }
}
