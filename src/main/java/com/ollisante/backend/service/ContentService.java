package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.FaqEntry;
import com.ollisante.backend.domain.NewsAudience;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.Training;
import com.ollisante.backend.dto.ContentDtos.FaqRequest;
import com.ollisante.backend.dto.ContentDtos.FaqResponse;
import com.ollisante.backend.dto.ContentDtos.TrainingRequest;
import com.ollisante.backend.dto.ContentDtos.TrainingResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.FaqRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.SpecialtyRepository;
import com.ollisante.backend.repository.TrainingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Formations pour les professionnels et questions fréquentes. */
@Service
@RequiredArgsConstructor
public class ContentService {

    private final TrainingRepository trainings;
    private final FaqRepository faq;
    private final PractitionerRepository practitioners;
    private final CountryRepository countries;
    private final SpecialtyRepository specialties;
    private final AuditService audit;

    // ---------- formations ----------

    /** Formations à venir pour ce praticien : son pays (ou tous) et ses spécialités (ou toutes). */
    @Transactional(readOnly = true)
    public List<TrainingResponse> upcomingForPractitioner(UUID userId) {
        Practitioner p = practitioners.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PRATICIEN_REQUIS",
                        "Réservé aux praticiens."));
        List<String> codes = p.getSpecialties().stream().map(Specialty::getCode).toList();
        return trainings.upcomingFor(Instant.now().minus(2, ChronoUnit.HOURS), p.getCountryCode(),
                codes.isEmpty() ? List.of("-") : codes).stream().map(TrainingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingResponse> allTrainings(int page, int size) {
        return PageResponse.of(trainings.findAllByOrderByStartsAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(TrainingResponse::from));
    }

    @Transactional
    public TrainingResponse createTraining(UUID admin, TrainingRequest r) {
        Training t = new Training();
        apply(t, r);
        trainings.save(t);
        audit.record(admin, "FORMATION_AJOUTEE", "TRAINING", t.getId(), t.getTitle());
        return TrainingResponse.from(t);
    }

    @Transactional
    public TrainingResponse updateTraining(UUID admin, UUID id, TrainingRequest r) {
        Training t = trainings.findById(id).orElseThrow(ContentService::trainingNotFound);
        apply(t, r);
        audit.record(admin, "FORMATION_MODIFIEE", "TRAINING", id, t.getTitle());
        return TrainingResponse.from(t);
    }

    @Transactional
    public void deleteTraining(UUID admin, UUID id) {
        Training t = trainings.findById(id).orElseThrow(ContentService::trainingNotFound);
        trainings.delete(t);
        audit.record(admin, "FORMATION_SUPPRIMEE", "TRAINING", id, t.getTitle());
    }

    private void apply(Training t, TrainingRequest r) {
        if (r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HORAIRE_INVALIDE", "La fin doit être après le début.");
        }
        String cc = blankToNull(r.countryCode());
        if (cc != null) {
            cc = cc.toUpperCase(Locale.ROOT);
            if (!countries.existsById(cc)) throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
        }
        String sc = blankToNull(r.specialtyCode());
        if (sc != null && !specialties.existsByCode(sc)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIALITE_INCONNUE", "Spécialité inconnue.");
        }
        t.setTitle(r.title().trim());
        t.setDescription(blankToNull(r.description()));
        t.setFormat(r.format());
        t.setStartsAt(r.startsAt());
        t.setEndsAt(r.endsAt());
        t.setLocation(blankToNull(r.location()));
        t.setLinkUrl(blankToNull(r.linkUrl()));
        t.setCountryCode(cc);
        t.setSpecialtyCode(sc);
        t.setPublished(r.published() == null || r.published());
    }

    private static ApiException trainingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "FORMATION_INTROUVABLE", "Formation introuvable.");
    }

    // ---------- FAQ ----------

    /** FAQ publiée pour un public (sans connexion : patients et tout le monde). */
    @Transactional(readOnly = true)
    public List<FaqResponse> publishedFaq(NewsAudience audience) {
        NewsAudience own = audience == NewsAudience.PRACTITIONER ? NewsAudience.PRACTITIONER : NewsAudience.PATIENT;
        return faq.findByPublishedTrueAndAudienceInOrderByPositionAscCreatedAtAsc(List.of(NewsAudience.ALL, own))
                .stream().map(FaqResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FaqResponse> allFaq() {
        return faq.findAllByOrderByPositionAscCreatedAtAsc().stream().map(FaqResponse::from).toList();
    }

    @Transactional
    public FaqResponse createFaq(UUID admin, FaqRequest r) {
        FaqEntry f = new FaqEntry();
        apply(f, r);
        faq.save(f);
        audit.record(admin, "FAQ_AJOUTEE", "FAQ", f.getId(), f.getQuestionFr());
        return FaqResponse.from(f);
    }

    @Transactional
    public FaqResponse updateFaq(UUID admin, UUID id, FaqRequest r) {
        FaqEntry f = faq.findById(id).orElseThrow(ContentService::faqNotFound);
        apply(f, r);
        audit.record(admin, "FAQ_MODIFIEE", "FAQ", id, f.getQuestionFr());
        return FaqResponse.from(f);
    }

    @Transactional
    public void deleteFaq(UUID admin, UUID id) {
        FaqEntry f = faq.findById(id).orElseThrow(ContentService::faqNotFound);
        faq.delete(f);
        audit.record(admin, "FAQ_SUPPRIMEE", "FAQ", id, f.getQuestionFr());
    }

    private static void apply(FaqEntry f, FaqRequest r) {
        f.setQuestionFr(r.questionFr().trim());
        f.setAnswerFr(r.answerFr().trim());
        f.setQuestionEn(blankToNull(r.questionEn()));
        f.setAnswerEn(blankToNull(r.answerEn()));
        f.setAudience(r.audience() == null ? NewsAudience.ALL : r.audience());
        f.setPosition(r.position() == null ? 0 : r.position());
        f.setPublished(r.published() == null || r.published());
    }

    private static ApiException faqNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "FAQ_INTROUVABLE", "Question introuvable.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
