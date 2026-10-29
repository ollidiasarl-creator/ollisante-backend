package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AdminPractitionerCreate;
import com.ollisante.backend.dto.AdminPractitionerResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.mail.WelcomeMail;
import com.ollisante.backend.repository.PractitionerRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final PractitionerRepository practitioners;
    private final AuditService audit;
    private final AuthService authService;
    private final WelcomeMail welcomeMail;

    @Transactional(readOnly = true)
    public PageResponse<AdminPractitionerResponse> listByStatus(VerificationStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt"));
        return PageResponse.of(practitioners
                .findByVerificationStatusAndUserStatusNot(status, UserStatus.DELETED, pageRequest)
                .map(AdminPractitionerResponse::from));
    }

    @Transactional
    public AdminPractitionerResponse setVerification(UUID adminUserId, UUID practitionerId, VerificationStatus status) {
        Practitioner p = practitioners.findById(practitionerId)
                .filter(found -> found.getUser().getStatus() != UserStatus.DELETED) // compte supprimé : intouchable
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRATICIEN_INTROUVABLE",
                        "Praticien introuvable."));
        p.setVerificationStatus(status);
        audit.record(adminUserId, "PRATICIEN_" + status.name(), "PRACTITIONER", practitionerId,
                p.getFirstName() + " " + p.getLastName());
        return AdminPractitionerResponse.from(p);
    }

    /** Ajout manuel par l'administrateur : le professionnel est validé d'office. */
    @Transactional
    public AdminPractitionerResponse create(UUID adminUserId, AdminPractitionerCreate r) {
        Practitioner p = authService.createPractitionerByAdmin(r);
        audit.record(adminUserId, "PRATICIEN_AJOUTE", "PRACTITIONER", p.getId(),
                p.getFirstName() + " " + p.getLastName());
        return AdminPractitionerResponse.from(p);
    }

    /** E-mail de bienvenue, envoyé après l'enregistrement du compte. false si l'envoi a échoué. */
    public boolean sendWelcome(AdminPractitionerResponse p, String locale) {
        return welcomeMail.send(p.email(), "Dr " + p.lastName(), "Médecin", "Doctor",
                " Votre profil professionnel est déjà validé.", " Your professional profile is already approved.",
                locale);
    }
}
