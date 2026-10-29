package com.ollisante.backend.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Client de l'API PawaPay v2 (bac à sable ou production selon l'adresse configurée). */
@Slf4j
public class HttpPawaPay implements PawaPay {

    private final RestClient http;

    public HttpPawaPay(String baseUrl, String token) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(10_000);
        timeouts.setReadTimeout(30_000);
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Initiation deposit(UUID depositId, String phone, String operator, String amount, String currency,
                              String message) {
        Map<String, Object> body = Map.of(
                "depositId", depositId.toString(),
                "payer", Map.of("type", "MMO", "accountDetails", Map.of("phoneNumber", phone, "provider", operator)),
                "amount", amount,
                "currency", currency,
                "customerMessage", message);
        return initiation(post("/v2/deposits", body));
    }

    @Override
    public Status depositStatus(UUID depositId) {
        return status(get("/v2/deposits/" + depositId));
    }

    @Override
    public Initiation refund(UUID refundId, UUID depositId, String amount, String currency) {
        Map<String, Object> body = Map.of(
                "refundId", refundId.toString(),
                "depositId", depositId.toString(),
                "amount", amount,
                "currency", currency);
        return initiation(post("/v2/refunds", body));
    }

    @Override
    public Status refundStatus(UUID refundId) {
        return status(get("/v2/refunds/" + refundId));
    }

    @Override
    public List<Operator> operators(String countryIso3) {
        JsonNode root = get("/v2/active-conf?operationType=DEPOSIT&country=" + countryIso3);
        List<Operator> list = new ArrayList<>();
        for (JsonNode country : root.path("countries")) {
            for (JsonNode p : country.path("providers")) {
                for (JsonNode c : p.path("currencies")) {
                    JsonNode dep = c.path("operationTypes").path("DEPOSIT");
                    if (dep.isMissingNode() || "CLOSED".equals(dep.path("status").asText())) continue;
                    list.add(new Operator(p.path("provider").asText(), p.path("displayName").asText(),
                            c.path("currency").asText(), dep.path("minAmount").asText(null),
                            dep.path("maxAmount").asText(null), !"NONE".equals(dep.path("decimalsInAmount").asText())));
                }
            }
        }
        return list;
    }

    private JsonNode post(String path, Object body) {
        try {
            return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("PawaPay {} : HTTP {}", path, e.getStatusCode().value());
            return e.getResponseBodyAs(JsonNode.class);
        }
    }

    private JsonNode get(String path) {
        try {
            return http.get().uri(path).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("PawaPay {} : HTTP {}", path, e.getStatusCode().value());
            return e.getResponseBodyAs(JsonNode.class);
        }
    }

    private static Initiation initiation(JsonNode r) {
        if (r == null) return new Initiation("REJECTED", "NO_RESPONSE");
        return new Initiation(r.path("status").asText("REJECTED"),
                r.path("failureReason").path("failureCode").asText(null));
    }

    private static Status status(JsonNode r) {
        if (r == null) return new Status("UNKNOWN", null, null);
        if (!"FOUND".equals(r.path("status").asText())) return new Status("NOT_FOUND", null, null);
        JsonNode d = r.path("data");
        return new Status(d.path("status").asText("UNKNOWN"), d.path("failureReason").path("failureCode").asText(null),
                d.path("providerTransactionId").asText(null));
    }
}
