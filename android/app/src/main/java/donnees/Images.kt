package io.github.tomuius.canapilou.donnees

// Adresse des images de TMDB. Tailles proposées pour les affiches : w92, w154,
// w185, w342, w500, w780 (liste reprise de js/tmdb.js).
private const val SERVEUR_IMAGES = "https://image.tmdb.org/t/p/"

/**
 * Adresse complète d'une affiche à partir du chemin donné par TMDB
 * (« /abc.jpg »), ou null s'il n'y en a pas. Par défaut w154 : dans la liste,
 * l'affiche fait 46 × 69 dp, soit environ 130 × 190 pixels sur un téléphone
 * courant (le site prend w92 pour 46 × 69 pixels CSS). Choix à revoir après des
 * tests.
 */
fun urlAffiche(chemin: String?, taille: String = "w154"): String? =
    if (chemin.isNullOrEmpty()) null else SERVEUR_IMAGES + taille + chemin

// Images du site (décision 80) : badges et illustrations de Tilou, chargées
// par Coil depuis GitHub Pages (une seule copie, celle du site). Une image
// modifiée doit changer de nom, sinon l'appli pourrait garder l'ancienne.
private const val IMAGES_SITE = "https://tomuius.github.io/suivi-series/img/"

/** Image de Tilou d'un badge (192 × 264 pixels, pour 64 × 88 dp). */
fun urlImageBadge(icone: String): String = IMAGES_SITE + "badges/" + icone + ".png"

/** Tilou au pop-corn (145 × 288 pixels), à côté du nom dans le Profil (décision 39). */
const val URL_TILOU_POPCORN = IMAGES_SITE + "tilou-popcorn.png"
