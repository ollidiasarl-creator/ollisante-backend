package com.ollisante.backend.dto;

/** Chemin public d'un fichier envoyé, à préfixer par l'adresse de l'API (ex. /media/abc.jpg). */
public record MediaResponse(String url) {
}
