package com.ollisante.backend.payment;

import com.ollisante.backend.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;

/** PawaPay réel si une clé est configurée ; simulé en local ; sinon paiement en ligne désactivé. */
@Configuration
public class PawaPayConfig {

    @Bean
    public PawaPay pawaPay(Environment env, @Value("${ollisante.pawapay.base-url}") String baseUrl,
                           @Value("${ollisante.pawapay.api-token:}") String token) {
        if (!token.isBlank()) return new HttpPawaPay(baseUrl, token);
        if (env.acceptsProfiles(Profiles.of("local"))) return new SimulatedPawaPay();
        return new Disabled();
    }

    /** Aucune clé : les pays « paiement en ligne » basculent en paiement sur place. */
    static class Disabled implements PawaPay {
        private static ApiException off() {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAIEMENT_NON_CONFIGURE",
                    "Le paiement en ligne n'est pas encore disponible.");
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Initiation deposit(UUID id, String phone, String operator, String amount, String currency, String m) {
            throw off();
        }

        @Override
        public Status depositStatus(UUID id) {
            throw off();
        }

        @Override
        public Initiation refund(UUID refundId, UUID depositId, String amount, String currency) {
            throw off();
        }

        @Override
        public Status refundStatus(UUID id) {
            throw off();
        }

        @Override
        public List<Operator> operators(String countryIso3) {
            return List.of();
        }
    }
}
