package com.ollisante.backend.service;

import com.ollisante.backend.domain.AdminAuditEntry;
import com.ollisante.backend.repository.AdminAuditRepository;
import com.ollisante.backend.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Journal des actions d'administration, enregistré dans la même transaction que l'action. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AdminAuditRepository audit;
    private final UserRepository users;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID adminUserId, String action, String targetType, Object targetId, String details) {
        AdminAuditEntry e = new AdminAuditEntry();
        e.setAdminUserId(adminUserId);
        e.setAdminEmail(users.findById(adminUserId).map(u -> u.getEmail()).orElse(null));
        e.setAction(action);
        e.setTargetType(targetType);
        e.setTargetId(targetId == null ? null : targetId.toString());
        e.setDetails(details == null ? null : details.length() > 500 ? details.substring(0, 500) : details);
        audit.save(e);
    }
}
