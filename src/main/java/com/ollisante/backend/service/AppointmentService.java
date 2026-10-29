package com.ollisante.backend.service;

import com.ollisante.backend.booking.Slot;
import com.ollisante.backend.booking.SlotService;
import com.ollisante.backend.booking.TimeZones;
import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.BookingProperties;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.OrganizationMember;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Relative;
import com.ollisante.backend.domain.PaymentMode;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AppointmentResponse;
import com.ollisante.backend.dto.BookAppointmentRequest;
import com.ollisante.backend.payment.PawaPay;
import com.ollisante.backend.payment.PaymentService;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.OrganizationMemberRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.RelativeRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final FacilityRepository facilities;
    private final SlotService slotService;
    private final TimeZones timeZones;
    private final BookingProperties booking;
    private final CountryRepository countries;
    private final PaymentService paymentService;
    private final PawaPay pawaPay;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final CountryScope countryScope;
    private final RelativeRepository relatives;

    @Transactional
    public AppointmentResponse book(UUID userId, BookAppointmentRequest r) {
        Patient patient = patients.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PATIENT_REQUIS",
                        "Réservé aux patients."));
        Practitioner practitioner = practitioners.findById(r.practitionerId())
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRATICIEN_INTROUVABLE",
                        "Praticien introuvable."));
        // Un patient ne réserve qu'auprès des professionnels de son pays.
        countryScope.of(userId, Role.PATIENT).ifPresent(mine -> {
            if (!mine.equalsIgnoreCase(practitioner.getCountryCode())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HORS_PAYS",
                        "Vous ne pouvez prendre rendez-vous qu'avec des professionnels de votre pays.");
            }
        });
        if (r.mode() == AppointmentMode.VIDEO && !practitioner.isTeleconsultationEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TELECONSULTATION_DESACTIVEE",
                    "Ce praticien ne propose pas la téléconsultation.");
        }

        // Le créneau doit figurer parmi les créneaux libres calculés à partir des disponibilités.
        ZoneId zone = timeZones.of(practitioner.getCountryCode());
        LocalDate day = r.startsAt().atZone(zone).toLocalDate();
        checkBookingLimits(patient, practitioner, day, zone);
        Slot slot = slotService.freeSlots(practitioner, day, day, r.mode()).stream()
                .filter(s -> s.startsAt().equals(r.startsAt()))
                .findFirst()
                .orElseThrow(() -> slotUnavailable());

        // Tarif fixé par pays (administration). Toute réservation payante passe par un moyen de paiement activé
        // pour le pays et choisi par le patient : Mobile Money tout de suite, ou sur place le jour du rendez-vous.
        Country country = countries.findById(practitioner.getCountryCode()).orElseThrow();
        Long fee = country.getDefaultFeeMinor();
        PaymentMode paymentMode = fee == null || fee <= 0 ? PaymentMode.FREE : paymentMode(country, r.paymentMethod());
        Appointment appointment = new Appointment();
        appointment.setPractitioner(practitioner);
        appointment.setPatient(patient);
        if (slot.facilityId() != null) {
            Facility facility = facilities.findById(slot.facilityId()).orElseThrow();
            appointment.setFacility(facility);
            if (facility.getOrganizationId() != null && members.existsByIdOrganizationIdAndIdPractitionerIdAndStatus(
                    facility.getOrganizationId(), practitioner.getId(), OrganizationMember.Status.ACTIVE)) {
                appointment.setOrganizationId(facility.getOrganizationId());
            }
        }
        appointment.setMode(slot.mode());
        appointment.setStartsAt(slot.startsAt());
        appointment.setEndsAt(slot.endsAt());
        appointment.setReason(r.reason());
        if (r.relativeId() != null) {
            Relative relative = relatives.findByIdAndOwnerUserId(r.relativeId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROCHE_INTROUVABLE", "Proche introuvable."));
            appointment.setRelativeId(relative.getId());
            appointment.setRelativeName((relative.getFirstName() + " " + relative.getLastName()).trim());
        }
        appointment.setPriceMinor(paymentMode == PaymentMode.FREE ? null : fee);
        appointment.setPriceCurrency(paymentMode == PaymentMode.FREE ? null : country.getCurrency());
        appointment.setPaymentMode(paymentMode);
        appointment.setStatus(paymentMode == PaymentMode.ONLINE ? AppointmentStatus.PENDING_PAYMENT : AppointmentStatus.CONFIRMED);
        if (slot.mode() == AppointmentMode.VIDEO) {
            // Identifiant de salle provisoire, en attendant le choix du fournisseur de visio.
            appointment.setVideoRoom("ollisante-" + UUID.randomUUID());
        }

        try {
            // L'index unique en base garantit qu'il n'y a pas de double réservation, même en cas de course.
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw slotUnavailable();
        }
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> mine(UUID userId, Role role) {
        List<Appointment> list;
        if (role == Role.PRACTITIONER) {
            Practitioner p = practitioners.findByUserId(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PRATICIEN_REQUIS",
                            "Réservé aux praticiens."));
            list = appointments.findByPractitionerIdOrderByStartsAtDesc(p.getId());
        } else {
            Patient p = patients.findByUserId(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PATIENT_REQUIS",
                            "Réservé aux patients."));
            list = appointments.findByPatientIdOrderByStartsAtDesc(p.getId());
        }
        return list.stream().map(AppointmentResponse::from).toList();
    }

    @Transactional
    public AppointmentResponse cancel(UUID userId, Role role, UUID appointmentId, String reason) {
        Appointment a = appointments.findById(appointmentId)
                .filter(x -> isParticipant(x, userId, role))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RDV_INTROUVABLE",
                        "Rendez-vous introuvable."));
        boolean cancellable = a.getStatus() == AppointmentStatus.PENDING_PAYMENT
                || a.getStatus() == AppointmentStatus.CONFIRMED;
        if (!cancellable) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUT_INVALIDE",
                    "Ce rendez-vous ne peut plus être annulé.");
        }
        if (!a.getStartsAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "RDV_PASSE", "Ce rendez-vous est déjà passé.");
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelledReason(reason);
        paymentService.refundForCancellation(a, role); // selon le délai et l'auteur de l'annulation
        return AppointmentResponse.from(a);
    }

    /** Ce que le patient (ou le praticien) récupérerait en annulant maintenant. */
    @Transactional(readOnly = true)
    public PaymentService.CancellationPreview cancellationPreview(UUID userId, Role role, UUID appointmentId) {
        Appointment a = appointments.findById(appointmentId)
                .filter(x -> isParticipant(x, userId, role))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RDV_INTROUVABLE",
                        "Rendez-vous introuvable."));
        return paymentService.preview(a, role);
    }

    /** Libère les créneaux dont le paiement n'est pas arrivé à temps. Appelé périodiquement. */
    @Transactional
    public int expirePendingPayments() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(booking.pendingPaymentMinutes(), ChronoUnit.MINUTES);
        return appointments.expirePending(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CANCELLED, cutoff, now);
    }

    private boolean isParticipant(Appointment a, UUID userId, Role role) {
        return switch (role) {
            case ADMIN -> true;
            case PRACTITIONER -> a.getPractitioner().getUser().getId().equals(userId);
            case PATIENT -> a.getPatient().getUser().getId().equals(userId);
            case STRUCTURE -> a.getOrganizationId() != null && organizations.findByUserId(userId)
                    .map(o -> o.getId().equals(a.getOrganizationId())).orElse(false);
        };
    }

    /**
     * Anti-blocage d'agenda : un compte ne peut pas réserver en masse des créneaux (impayés ou non) pour
     * empêcher les vrais patients de prendre rendez-vous.
     */
    private void checkBookingLimits(Patient patient, Practitioner practitioner, LocalDate day, ZoneId zone) {
        if (appointments.countByPatientIdAndStatus(patient.getId(), AppointmentStatus.PENDING_PAYMENT) >= booking.maxPendingPerPatient()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAIEMENTS_EN_ATTENTE",
                    "Vous avez déjà des rendez-vous en attente de paiement. Payez-les ou annulez-les avant d'en prendre un autre.");
        }
        Set<AppointmentStatus> active = Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_PAYMENT);
        if (appointments.countByPatientIdAndStatusInAndStartsAtAfter(patient.getId(), active, Instant.now()) >= booking.maxUpcomingPerPatient()) {
            throw new ApiException(HttpStatus.CONFLICT, "TROP_DE_RDV",
                    "Vous avez atteint le nombre maximal de rendez-vous à venir.");
        }
        Instant from = day.atStartOfDay(zone).toInstant();
        if (appointments.existsByPatientIdAndPractitionerIdAndStatusInAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                patient.getId(), practitioner.getId(), active, from, from.plus(1, ChronoUnit.DAYS))) {
            throw new ApiException(HttpStatus.CONFLICT, "DEJA_RESERVE_CE_JOUR",
                    "Vous avez déjà un rendez-vous avec ce professionnel ce jour-là.");
        }
    }

    /** Moyen de paiement de la réservation : celui choisi par le patient, s'il est activé pour le pays. */
    private PaymentMode paymentMode(Country country, BookAppointmentRequest.PaymentMethod choice) {
        boolean mobileMoney = country.isOnlinePayment() && pawaPay.enabled();
        boolean onSite = country.isOnSitePayment();
        if (!mobileMoney && !onSite) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUCUN_MOYEN_DE_PAIEMENT",
                    "La réservation en ligne n'est pas encore ouverte dans ce pays.");
        }
        if (choice == null) return mobileMoney ? PaymentMode.ONLINE : PaymentMode.ON_SITE;
        if (choice == BookAppointmentRequest.PaymentMethod.MOBILE_MONEY && mobileMoney) return PaymentMode.ONLINE;
        if (choice == BookAppointmentRequest.PaymentMethod.ON_SITE && onSite) return PaymentMode.ON_SITE;
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MOYEN_DE_PAIEMENT_INDISPONIBLE",
                "Ce moyen de paiement n'est pas proposé dans ce pays. Choisissez-en un autre.");
    }

    private static ApiException slotUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "CRENEAU_INDISPONIBLE",
                "Ce créneau n'est plus disponible. Veuillez en choisir un autre.");
    }
}
