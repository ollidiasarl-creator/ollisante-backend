package com.ollisante.backend.booking;

import com.ollisante.backend.repository.CountryRepository;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeZones {

    private final CountryRepository countries;

    public ZoneId of(String countryCode) {
        return countries.findById(countryCode)
                .map(c -> c.getTimezone())
                .map(ZoneId::of)
                .orElse(ZoneOffset.UTC);
    }
}
