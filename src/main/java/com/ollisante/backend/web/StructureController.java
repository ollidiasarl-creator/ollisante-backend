package com.ollisante.backend.web;

import com.ollisante.backend.dto.AppointmentResponse;
import com.ollisante.backend.dto.AvailabilityRuleRequest;
import com.ollisante.backend.dto.AvailabilityRuleResponse;
import com.ollisante.backend.dto.CancelAppointmentRequest;
import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.StructureDtos.InviteRequest;
import com.ollisante.backend.dto.StructureDtos.MemberRow;
import com.ollisante.backend.dto.StructureDtos.Membership;
import com.ollisante.backend.dto.StructureDtos.ProfileUpdate;
import com.ollisante.backend.dto.StructureDtos.StructureProfile;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.StructureService;
import com.ollisante.backend.service.StructureService.RuleRow;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StructureController {

    private final StructureService service;

    // ---------- espace Structure ----------

    @GetMapping("/structure/profile")
    @PreAuthorize("hasRole('STRUCTURE')")
    public StructureProfile profile(Authentication auth) {
        return service.profile(CurrentUser.id(auth));
    }

    @PatchMapping("/structure/profile")
    @PreAuthorize("hasRole('STRUCTURE')")
    public StructureProfile update(Authentication auth, @Valid @RequestBody ProfileUpdate r) {
        return service.update(CurrentUser.id(auth), r);
    }

    @GetMapping("/structure/sites")
    @PreAuthorize("hasRole('STRUCTURE')")
    public List<FacilityResponse> sites(Authentication auth) {
        return service.sites(CurrentUser.id(auth));
    }

    @PostMapping("/structure/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STRUCTURE')")
    public FacilityResponse addSite(Authentication auth, @Valid @RequestBody FacilityRequest r) {
        return service.addSite(CurrentUser.id(auth), r);
    }

    @GetMapping("/structure/members")
    @PreAuthorize("hasRole('STRUCTURE')")
    public List<MemberRow> members(Authentication auth) {
        return service.members(CurrentUser.id(auth));
    }

    @PostMapping("/structure/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STRUCTURE')")
    public MemberRow invite(Authentication auth, @Valid @RequestBody InviteRequest r) {
        return service.invite(CurrentUser.id(auth), r.email());
    }

    @DeleteMapping("/structure/members/{practitionerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STRUCTURE')")
    public void removeMember(Authentication auth, @PathVariable UUID practitionerId) {
        service.removeMember(CurrentUser.id(auth), practitionerId);
    }

    @GetMapping("/structure/availability-rules")
    @PreAuthorize("hasRole('STRUCTURE')")
    public List<RuleRow> rules(Authentication auth) {
        return service.rules(CurrentUser.id(auth));
    }

    @PostMapping("/structure/members/{practitionerId}/availability-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STRUCTURE')")
    public AvailabilityRuleResponse addRule(Authentication auth, @PathVariable UUID practitionerId,
                                            @Valid @RequestBody AvailabilityRuleRequest r) {
        return service.addRule(CurrentUser.id(auth), practitionerId, r);
    }

    @DeleteMapping("/structure/availability-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STRUCTURE')")
    public void deleteRule(Authentication auth, @PathVariable UUID id) {
        service.deleteRule(CurrentUser.id(auth), id);
    }

    @GetMapping("/structure/appointments")
    @PreAuthorize("hasRole('STRUCTURE')")
    public List<AppointmentResponse> appointments(Authentication auth) {
        return service.appointments(CurrentUser.id(auth));
    }

    @PostMapping("/structure/appointments/{id}/cancel")
    @PreAuthorize("hasRole('STRUCTURE')")
    public AppointmentResponse cancel(Authentication auth, @PathVariable UUID id,
                                      @Valid @RequestBody(required = false) CancelAppointmentRequest r) {
        return service.cancel(CurrentUser.id(auth), id, r == null ? null : r.reason());
    }

    // ---------- côté médecin : invitations des structures ----------

    @GetMapping("/me/organizations")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public List<Membership> memberships(Authentication auth) {
        return service.memberships(CurrentUser.id(auth));
    }

    @PostMapping("/me/organizations/{organizationId}/accept")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public Membership accept(Authentication auth, @PathVariable UUID organizationId) {
        return service.answer(CurrentUser.id(auth), organizationId, true);
    }

    @PostMapping("/me/organizations/{organizationId}/leave")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public Membership leave(Authentication auth, @PathVariable UUID organizationId) {
        return service.answer(CurrentUser.id(auth), organizationId, false);
    }
}
