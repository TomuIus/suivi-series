package io.github.tomuius.canapilou.ui.liste

import io.github.tomuius.canapilou.donnees.SerieSuivie
import kotlin.math.min

// Règles d'affichage de « Ma liste », sans rien dessiner : ce fichier se teste
// seul (voir app/src/test/java/ListeTest.kt). Mêmes règles que le site
// (constante SECTIONS et fonctions pourcentage, libelleEpisode, progression
// de js/app.js) : à modifier aux deux endroits.

/**
 * Sections de « Ma liste », dans l'ordre d'affichage. « À voir » (pas encore
 * commencée) est réuni avec « En cours » (décision 27). Les trois dernières
 * sont repliées au départ.
 */
enum class Section(val titre: String, val statuts: Set<String>, val replieeParDefaut: Boolean) {
    EN_COURS("En cours", setOf("en_cours", "a_voir"), false),
    A_JOUR("À jour", setOf("a_jour"), false),
    TERMINE("Terminé", setOf("termine"), true),
    ABANDONNE("Abandonné", setOf("abandonne"), true),
    PLUS_TARD("Plus tard", setOf("plus_tard"), true),
}

/** Range les séries par section, en gardant leur ordre (activité la plus récente d'abord). */
fun regrouperParSection(series: List<SerieSuivie>): Map<Section, List<SerieSuivie>> =
    Section.entries.associateWith { section -> series.filter { it.statut in section.statuts } }

/**
 * Pourcentage d'épisodes vus parmi les épisodes déjà sortis (spéciaux exclus,
 * comme dans la vue « ma_liste »). Arrondi vers le bas : 100 % seulement quand
 * tout est vu. null s'il n'y a encore aucun épisode sorti.
 */
fun pourcentage(s: SerieSuivie): Int? {
    if (s.nbDiffuses <= 0) return null
    return 100 * min(s.nbVus, s.nbDiffuses) / s.nbDiffuses // division entière = arrondi vers le bas
}

/** « S2·E5 » (point médian, sans espaces : demande de l'utilisateur, v21). */
fun libelleEpisode(saison: Int, episode: Int): String = "S$saison·E$episode"

/**
 * Ligne d'informations sous le titre, comme sur le site :
 * « Prochain : S1·E6 | 5 / 808 épisodes vus ». Le titre de l'épisode
 * (« Prochain : S1·E6 "Ste Lisa Blues" ») viendra à une étape suivante.
 */
fun ligneProgression(s: SerieSuivie): String {
    val compte = "${s.nbVus} / ${s.nbDiffuses} épisodes vus"
    return when {
        s.statut == "termine" -> "${s.nbVus} épisodes vus"
        s.statut == "a_jour" -> "${s.nbVus} épisodes vus | en attente de la suite"
        s.nbDiffuses == 0 -> "Aucun épisode diffusé pour l'instant"
        s.statut == "plus_tard" -> compte // pas de « Prochain » : série mise de côté
        s.prochaineSaison != null && s.prochainEpisode != null && s.statut != "abandonne" ->
            "Prochain : ${libelleEpisode(s.prochaineSaison, s.prochainEpisode)} | $compte"
        else -> compte
    }
}
