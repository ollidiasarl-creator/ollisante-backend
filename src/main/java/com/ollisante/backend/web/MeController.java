package com.ollisante.backend.web;

import com.ollisante.backend.dto.ProfileDtos.PasswordChange;
import com.ollisante.backend.dto.ProfileDtos.Profile;
import com.ollisante.backend.dto.ProfileDtos.ProfileUpdate;
import com.ollisante.backend.dto.ProfileDtos.RelativeInfo;
import com.ollisante.backend.dto.ProfileDtos.RelativeInput;
import com.ollisante.backend.dto.UserResponse;
import com.ollisante.backend.payment.BillingService;
import com.ollisante.backend.service.AccountDeletionService;
import com.ollisante.backend.service.AuthService;
import com.ollisante.backend.service.ProfileService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;
    private final AccountDeletionService accountDeletionService;
    private final ProfileService profileService;
    private final BillingService billingService;

    @GetMapping
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(UUID.fromString(authentication.getName()));
    }

    /** Rubrique « Compte » : identité, coordonnées et informations selon le profil (patient, médecin, structure). */
    @GetMapping("/profile")
    public Profile profile(Authentication authentication) {
        return profileService.get(UUID.fromString(authentication.getName()));
    }

    @PatchMapping("/profile")
    public Profile updateProfile(Authentication authentication, @Valid @RequestBody ProfileUpdate request) {
        return profileService.update(UUID.fromString(authentication.getName()), request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChange request) {
        profileService.changePassword(UUID.fromString(authentication.getName()), request);
    }

    // ---------- mes proches ----------

    @GetMapping("/relatives")
    public List<RelativeInfo> relatives(Authentication authentication) {
        return profileService.relatives(UUID.fromString(authentication.getName()));
    }

    @PostMapping("/relatives")
    @ResponseStatus(HttpStatus.CREATED)
    public RelativeInfo addRelative(Authentication authentication,
            @Valid @RequestBody RelativeInput request) {
        return profileService.addRelative(UUID.fromString(authentication.getName()), request);
    }

    @PatchMapping("/relatives/{id}")
    public RelativeInfo updateRelative(Authentication authentication,
            @PathVariable UUID id, @Valid @RequestBody RelativeInput request) {
        return profileService.updateRelative(UUID.fromString(authentication.getName()), id, request);
    }

    @DeleteMapping("/relatives/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelative(Authentication authentication, @PathVariable UUID id) {
        profileService.deleteRelative(UUID.fromString(authentication.getName()), id);
    }

    /** Rubrique « Paiements et reçus » : selon le profil (patient, médecin, structure). */
    @GetMapping("/billing")
    public BillingService.Billing billing(Authentication authentication) {
        return billingService.get(UUID.fromString(authentication.getName()));
    }

    @GetMapping("/billing/receipts/{paymentId}")
    public BillingService.Receipt receipt(Authentication authentication, @PathVariable UUID paymentId) {
        return billingService.receipt(UUID.fromString(authentication.getName()), paymentId);
    }

    @PostMapping("/billing/receipts/{paymentId}/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void emailReceipt(Authentication authentication, @PathVariable UUID paymentId) {
        billingService.emailReceipt(UUID.fromString(authentication.getName()), paymentId);
    }

    /** Suppression du compte par son titulaire (anonymisation, voir AccountDeletionService). */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication) {
        accountDeletionService.delete(UUID.fromString(authentication.getName()));
    }
}
