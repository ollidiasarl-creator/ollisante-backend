package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * « Signaler ce profil » : n'importe quel visiteur peut signaler un médecin ou une structure. À partir de
 * {@link #HIDE_AFTER} signalements venant de personnes différentes, le médecin est masqué du public jusqu'à la
 * vérification de l'équipe (qui le rétablit ou refuse le compte).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final int HIDE_AFTER = 3;
    public static final Set<String> TARGETS = Set.of("PRACTITIONER", "FACILITY");
    public static final Set<String> REASONS = Set.of("FAKE_PROFILE", "WRONG_INFO", "BEHAVIOUR", "OTHER");

    public record ReportRequest(String targetType, UUID targetId, String reason, String message) {
    }

    public record ReportSummary(String targetType, UUID targetId, String targetName, long openReports,
                                List<String> reasons, List<String> messages, Instant lastAt, boolean hidden) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    @Transactional
    public void report(ReportRequest r, UUID reporterUserId, String ip) {
        if (r == null || r.targetType() == null || r.reason() == null || r.targetId() == null
                || !TARGETS.contains(r.targetType()) || !REASONS.contains(r.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNALEMENT_INVALIDE", "Signalement incomplet.");
        }
        String table = "PRACTITIONER".equals(r.targetType()) ? "practitioners" : "facilities";
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, r.targetId());
        if (exists == null || exists == 0) throw new ApiException(HttpStatus.NOT_FOUND, "PROFIL_INTROUVABLE", "Profil introuvable.");
        // Une même personne (compte ou adresse) ne compte qu'une fois par profil sur 30 jours.
        Timestamp since = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Integer already = reporterUserId == null
                ? jdbc.queryForObject("SELECT count(*) FROM profile_reports WHERE target_type = ? AND target_id = ?"
                        + " AND created_at > ? AND reporter_ip = ?", Integer.class, r.targetType(), r.targetId(), since, ip)
                : jdbc.queryForObject("SELECT count(*) FROM profile_reports WHERE target_type = ? AND target_id = ?"
                        + " AND created_at > ? AND (reporter_ip = ? OR reporter_user_id = ?)",
                        Integer.class, r.targetType(), r.targetId(), since, ip, reporterUserId);
        if (already != null && already > 0) return;
        String message = r.message() == null ? null : r.message().strip();
        if (message != null && message.length() > 1000) message = message.substring(0, 1000);
        jdbc.update("INSERT INTO profile_reports (id, target_type, target_id, reason, message, reporter_user_id, reporter_ip)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), r.targetType(), r.targetId(), r.reason(), message, reporterUserId, ip);
        if ("PRACTITIONER".equals(r.targetType())) {
            Integer open = jdbc.queryForObject("SELECT count(*) FROM profile_reports WHERE target_type = 'PRACTITIONER'"
                    + " AND target_id = ? AND status = 'OPEN'", Integer.class, r.targetId());
            if (open != null && open >= HIDE_AFTER) {
                jdbc.update("UPDATE practitioners SET hidden_by_reports = true WHERE id = ?", r.targetId());
            }
        }
    }

    public List<ReportSummary> open() {
        return jdbc.query("""
                SELECT r.target_type, r.target_id, count(*) AS n, max(r.created_at) AS last_at,
                       string_agg(DISTINCT r.reason, ',') AS reasons,
                       string_agg(r.message, '\u001f' ORDER BY r.created_at DESC) FILTER (WHERE r.message IS NOT NULL) AS messages,
                       coalesce(p.first_name || ' ' || p.last_name, f.name) AS name,
                       coalesce(p.hidden_by_reports, false) AS hidden
                  FROM profile_reports r
                  LEFT JOIN practitioners p ON r.target_type = 'PRACTITIONER' AND p.id = r.target_id
                  LEFT JOIN facilities f ON r.target_type = 'FACILITY' AND f.id = r.target_id
                 WHERE r.status = 'OPEN'
                 GROUP BY r.target_type, r.target_id, p.first_name, p.last_name, f.name, p.hidden_by_reports
                 ORDER BY max(r.created_at) DESC""",
                (rs, i) -> new ReportSummary(rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                        rs.getString("name"), rs.getLong("n"), List.of(rs.getString("reasons").split(",")),
                        rs.getString("messages") == null ? List.of() : List.of(rs.getString("messages").split("\u001f")),
                        rs.getTimestamp("last_at").toInstant(), rs.getBoolean("hidden")));
    }

    /** L'équipe a vérifié : on classe les signalements, et on rétablit (ou non) le profil. */
    @Transactional
    public void close(UUID adminId, String targetType, UUID targetId, boolean reinstate) {
        int n = jdbc.update("UPDATE profile_reports SET status = 'CLOSED', closed_at = now() WHERE target_type = ? AND target_id = ?"
                + " AND status = 'OPEN'", targetType, targetId);
        if ("PRACTITIONER".equals(targetType) && reinstate) {
            jdbc.update("UPDATE practitioners SET hidden_by_reports = false WHERE id = ?", targetId);
        }
        audit.record(adminId, reinstate ? "SIGNALEMENTS_CLASSES_PROFIL_RETABLI" : "SIGNALEMENTS_CLASSES",
                targetType, targetId, n + " signalement(s)");
    }
}
