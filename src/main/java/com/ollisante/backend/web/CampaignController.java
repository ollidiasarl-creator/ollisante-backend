package com.ollisante.backend.web;

import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mesure des affiches (test A/B) : le site signale un scan (VISIT) puis, le cas échéant, une inscription (SIGNUP)
 * pour la source lue dans le QR code (ex. « medecin-a »). Rien d'autre n'est enregistré.
 */
@RestController
@RequiredArgsConstructor
public class CampaignController {

    private static final Pattern SOURCE = Pattern.compile("[a-z0-9-]{1,40}");

    private final JdbcTemplate jdbc;

    public record EventRequest(String source, String kind) {
    }

    public record SourceStats(String source, long visits, long signups) {
    }

    @PostMapping("/api/v1/campaign/events")
    public ResponseEntity<Void> record(@RequestBody EventRequest body) {
        if (body == null || body.source() == null || !SOURCE.matcher(body.source()).matches()
                || !List.of("VISIT", "SIGNUP").contains(body.kind())) {
            return ResponseEntity.badRequest().build();
        }
        jdbc.update("insert into campaign_events (source, kind) values (?, ?)", body.source(), body.kind());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/campaign/stats")
    public List<SourceStats> stats() {
        return jdbc.query("""
                select source,
                       count(*) filter (where kind = 'VISIT')  as visits,
                       count(*) filter (where kind = 'SIGNUP') as signups
                from campaign_events group by source order by source""",
                (rs, i) -> new SourceStats(rs.getString(1), rs.getLong(2), rs.getLong(3)));
    }
}
