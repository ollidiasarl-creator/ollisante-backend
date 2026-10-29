package com.ollisante.backend.service;

import com.ollisante.backend.domain.AppSetting;
import com.ollisante.backend.dto.AdminDtos.Settings;
import com.ollisante.backend.repository.AppSettingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numéro WhatsApp de l'assistance et ses horaires : affichés par l'application et le site s'ils sont renseignés. */
@Service
@RequiredArgsConstructor
public class SettingsService {

    static final String WHATSAPP_NUMBER = "whatsapp.number";
    static final String WHATSAPP_HOURS = "whatsapp.hours";

    private final AppSettingRepository settings;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Settings get() {
        return new Settings(value(WHATSAPP_NUMBER), value(WHATSAPP_HOURS));
    }

    @Transactional
    public Settings update(UUID admin, Settings r) {
        put(WHATSAPP_NUMBER, r.whatsappNumber());
        put(WHATSAPP_HOURS, r.whatsappHours());
        audit.record(admin, "PARAMETRES_MODIFIES", "SETTINGS", "whatsapp",
                r.whatsappNumber() == null || r.whatsappNumber().isBlank() ? "WhatsApp désactivé" : "WhatsApp " + r.whatsappNumber());
        return get();
    }

    private String value(String key) {
        return settings.findById(key).map(AppSetting::getValue).orElse(null);
    }

    private void put(String key, String value) {
        if (value == null || value.isBlank()) {
            settings.deleteById(key);
            return;
        }
        AppSetting s = settings.findById(key).orElseGet(() -> {
            AppSetting created = new AppSetting();
            created.setKey(key);
            return created;
        });
        s.setValue(value.trim());
        settings.save(s);
    }
}
