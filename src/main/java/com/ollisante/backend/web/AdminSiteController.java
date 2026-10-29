package com.ollisante.backend.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sites web servis par l'API : site public des patients (static/site, projet ollisante-web) à la racine,
 * site d'administration et Espace Structure (static/admin, projet ollisante-admin).
 */
@Controller
public class AdminSiteController {

    @GetMapping("/admin")
    public String redirect() {
        return "redirect:/admin/";
    }

    @GetMapping("/admin/")
    public String index() {
        return "forward:/admin/index.html";
    }

    /** Espace Structure : même application web, qui affiche l'espace dédié selon l'adresse. */
    @GetMapping("/structure")
    public String structureRedirect() {
        return "redirect:/structure/";
    }

    @GetMapping("/structure/")
    public String structureIndex() {
        return "forward:/admin/index.html";
    }

    /** Site public des patients : recherche, fiches, réservation, espace patient. */
    @GetMapping({"/", "/site", "/site/"})
    public String site() {
        return "forward:/site/index.html";
    }

    /** Fonctionnement hors connexion du site : servi à la racine pour couvrir toutes ses pages. */
    @GetMapping("/sw.js")
    public String serviceWorker() {
        return "forward:/site/sw.js";
    }
}
