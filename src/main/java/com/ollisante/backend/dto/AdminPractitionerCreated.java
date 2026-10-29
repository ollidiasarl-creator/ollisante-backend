package com.ollisante.backend.dto;

/** welcomeEmailSent : false si l'e-mail de bienvenue n'a pas pu partir (le compte est créé quand même). */
public record AdminPractitionerCreated(AdminPractitionerResponse practitioner, boolean welcomeEmailSent) {
}
