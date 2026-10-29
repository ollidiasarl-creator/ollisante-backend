package com.ollisante.backend.web;

import com.ollisante.backend.domain.NewsAudience;
import com.ollisante.backend.dto.ContentDtos.FaqRequest;
import com.ollisante.backend.dto.ContentDtos.FaqResponse;
import com.ollisante.backend.dto.ContentDtos.TrainingRequest;
import com.ollisante.backend.dto.ContentDtos.TrainingResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.ContentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService service;

    // ---------- formations ----------

    @GetMapping("/trainings")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public List<TrainingResponse> myTrainings(Authentication auth) {
        return service.upcomingForPractitioner(CurrentUser.id(auth));
    }

    @GetMapping("/admin/trainings")
    public PageResponse<TrainingResponse> allTrainings(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return service.allTrainings(page, size);
    }

    @PostMapping("/admin/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingResponse createTraining(Authentication auth, @Valid @RequestBody TrainingRequest r) {
        return service.createTraining(CurrentUser.id(auth), r);
    }

    @PatchMapping("/admin/trainings/{id}")
    public TrainingResponse updateTraining(Authentication auth, @PathVariable UUID id,
                                           @Valid @RequestBody TrainingRequest r) {
        return service.updateTraining(CurrentUser.id(auth), id, r);
    }

    @DeleteMapping("/admin/trainings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTraining(Authentication auth, @PathVariable UUID id) {
        service.deleteTraining(CurrentUser.id(auth), id);
    }

    // ---------- FAQ (lecture publique : utile avant la connexion) ----------

    @GetMapping("/faq")
    public List<FaqResponse> faq(@RequestParam(defaultValue = "PATIENT") NewsAudience audience) {
        return service.publishedFaq(audience);
    }

    @GetMapping("/admin/faq")
    public List<FaqResponse> allFaq() {
        return service.allFaq();
    }

    @PostMapping("/admin/faq")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqResponse createFaq(Authentication auth, @Valid @RequestBody FaqRequest r) {
        return service.createFaq(CurrentUser.id(auth), r);
    }

    @PatchMapping("/admin/faq/{id}")
    public FaqResponse updateFaq(Authentication auth, @PathVariable UUID id, @Valid @RequestBody FaqRequest r) {
        return service.updateFaq(CurrentUser.id(auth), id, r);
    }

    @DeleteMapping("/admin/faq/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFaq(Authentication auth, @PathVariable UUID id) {
        service.deleteFaq(CurrentUser.id(auth), id);
    }
}
