package com.ollisante.backend.web;

import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AdminDtos.AuditEntry;
import com.ollisante.backend.dto.AdminDtos.CountryAdmin;
import com.ollisante.backend.dto.AdminDtos.CountryCreate;
import com.ollisante.backend.dto.AdminDtos.CountryUpdate;
import com.ollisante.backend.dto.AdminDtos.PatientAppointment;
import com.ollisante.backend.dto.AdminDtos.PatientCreate;
import com.ollisante.backend.dto.AdminDtos.PatientCreated;
import com.ollisante.backend.dto.AdminDtos.PatientUpdate;
import com.ollisante.backend.dto.AdminDtos.SpecialtySave;
import com.ollisante.backend.dto.AdminDtos.Stats;
import com.ollisante.backend.dto.AdminDtos.StatusUpdate;
import com.ollisante.backend.dto.AdminDtos.StructureUpdate;
import com.ollisante.backend.dto.AdminDtos.UserAdmin;
import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.dto.SpecialtyResponse;
import com.ollisante.backend.dto.StructureDtos;
import com.ollisante.backend.dto.StructureDtos.CommissionUpdate;
import com.ollisante.backend.dto.StructureDtos.StructureProfile;
import com.ollisante.backend.dto.VerificationRequest;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.AdminPortalService;
import com.ollisante.backend.service.StatsService.AppointmentStats;
import com.ollisante.backend.service.StatsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Site d'administration. Réservé au rôle ADMIN (règle /api/v1/admin/** dans SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminPortalController {

    private final AdminPortalService service;
    private final StatsService statsService;

    @GetMapping("/stats")
    public Stats stats() {
        return service.stats();
    }

    @GetMapping("/stats/appointments")
    public AppointmentStats appointmentStats(@RequestParam(defaultValue = "12") int months) {
        return statsService.appointments(months);
    }

    @GetMapping("/countries")
    public List<CountryAdmin> countries() {
        return service.countries();
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public CountryAdmin createCountry(Authentication auth, @Valid @RequestBody CountryCreate request) {
        return service.createCountry(CurrentUser.id(auth), request);
    }

    @PatchMapping("/countries/{code}")
    public CountryAdmin updateCountry(Authentication auth, @PathVariable String code,
                                      @Valid @RequestBody CountryUpdate request) {
        return service.updateCountry(CurrentUser.id(auth), code, request);
    }

    @PostMapping("/specialties")
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialtyResponse createSpecialty(Authentication auth, @Valid @RequestBody SpecialtySave request) {
        return service.createSpecialty(CurrentUser.id(auth), request);
    }

    @PatchMapping("/specialties/{code}")
    public SpecialtyResponse renameSpecialty(Authentication auth, @PathVariable String code,
                                             @Valid @RequestBody SpecialtySave request) {
        return service.renameSpecialty(CurrentUser.id(auth), code, request);
    }

    @PatchMapping("/facilities/{id}")
    public FacilityResponse updateFacility(Authentication auth, @PathVariable UUID id,
                                           @Valid @RequestBody FacilityRequest request) {
        return service.updateFacility(CurrentUser.id(auth), id, request);
    }

    @DeleteMapping("/facilities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFacility(Authentication auth, @PathVariable UUID id) {
        service.deleteFacility(CurrentUser.id(auth), id);
    }

    @GetMapping("/patients")
    public PageResponse<UserAdmin> patients(@RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return service.patients(q, page, size);
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientCreated createPatient(Authentication auth, @Valid @RequestBody PatientCreate r) {
        UserAdmin created = service.createPatient(CurrentUser.id(auth), r);
        return r.password() != null ? new PatientCreated(created, false) : service.welcomePatient(created, r.locale());
    }

    @PatchMapping("/patients/{userId}")
    public UserAdmin updatePatient(Authentication auth, @PathVariable UUID userId, @Valid @RequestBody PatientUpdate r) {
        return service.updatePatient(CurrentUser.id(auth), userId, r);
    }

    @GetMapping("/patients/{userId}/appointments")
    public List<PatientAppointment> patientAppointments(@PathVariable UUID userId) {
        return service.patientAppointments(userId);
    }

    @PatchMapping("/users/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserStatus(Authentication auth, @PathVariable UUID id, @Valid @RequestBody StatusUpdate request) {
        service.setUserStatus(CurrentUser.id(auth), id, request.status());
    }

    @GetMapping("/audit")
    public PageResponse<AuditEntry> audit(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return service.audit(page, size);
    }

    @GetMapping("/structures")
    public PageResponse<StructureProfile> structures(@RequestParam(defaultValue = "PENDING") VerificationStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return service.structures(status, page, size);
    }

    @PostMapping("/structures")
    @ResponseStatus(HttpStatus.CREATED)
    public StructureDtos.StructureCreated createStructure(Authentication auth,
                                                          @Valid @RequestBody StructureDtos.AdminStructureCreate r) {
        return service.welcomeStructure(service.createStructure(CurrentUser.id(auth), r), r.locale());
    }

    @PatchMapping("/structures/{id}/verification")
    public StructureProfile verifyStructure(Authentication auth, @PathVariable UUID id,
                                            @Valid @RequestBody VerificationRequest r) {
        return service.verifyStructure(CurrentUser.id(auth), id, r.status());
    }

    @PatchMapping("/structures/{id}")
    public StructureProfile updateStructure(Authentication auth, @PathVariable UUID id,
                                            @Valid @RequestBody StructureUpdate r) {
        return service.updateStructure(CurrentUser.id(auth), id, r);
    }

    @PatchMapping("/structures/{id}/commission")
    public StructureProfile structureCommission(Authentication auth, @PathVariable UUID id,
                                                @Valid @RequestBody CommissionUpdate r) {
        return service.structureCommission(CurrentUser.id(auth), id, r.commissionPercent());
    }
}
