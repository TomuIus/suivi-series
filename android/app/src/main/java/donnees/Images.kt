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
