package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Specialty;

public record SpecialtyResponse(String code, String nameFr, String nameEn) {

    public static SpecialtyResponse from(Specialty s) {
        return new SpecialtyResponse(s.getCode(), s.getNameFr(), s.getNameEn());
    }
}
