package io.github.tomuius.canapilou.ui.liste

import io.github.tomuius.canapilou.donnees.ResultatTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import java.text.Normalizer
import java.util.IllformedLocaleException
import java.util.Locale

// Recherche commune (décision 44), copiée de js/recherche.js et de quelques
// fonctions de js/app.js (details, messageAjout, mentionDansListe) : à
// modifier aux deux endroits. Calculs seuls, sans affichage (testables sans
// Android).
//
// Règles (choix de Claude du 20/11/2026, à revoir après des tests) :
// - majuscules, accents et ponctuation ignorés : « l’attaque » trouve
//   « L'Attaque des Titans », « one punch » trouve « One-Punch Man » ;
// - chaque mot tapé doit être le DÉBUT d'un mot du titre, dans n'importe quel
//   ordre : « titans att » trouve « L'Attaque des Titans », mais « unch » ne
//   trouve pas « One-Punch Man » ;
// - seul le titre affiché (titre français de TMDB) est regardé ;
// - l'ordre est celui de la liste : activité la plus récente d'abord.

/** Nombre de caractères avant de lancer une recherche (LONGUEUR_MIN du site). */
const val LONGUEUR_MIN = 2

// Lettres liées que la décomposition Unicode ne sépare pas.
private val LIGATURES = mapOf('œ' to "oe", 'æ' to "ae", 'ß' to "ss")
private val ACCENTS = Regex("\\p{M}")
private val NI_LETTRE_NI_CHIFFRE = Regex("[^\\p{L}\\p{N}]+")

/**
 * Texte « simplifié » pour comparer : minuscules, sans accents, et tout ce
 * qui n'est ni lettre ni chiffre remplacé par une espace.
 * Exemple : « L’Œuvre — Saison 2 » → « l oeuvre saison 2 ».
 */
fun normaliser(texte: String?): String {
    val minuscules = (texte ?: "").lowercase()
    val sansLigatures = buildString { for (c in minuscules) append(LIGATURES[c] ?: c) }
    // « é » devient « e » + accent séparé (décomposition NFD)…
    val decompose = Normalizer.normalize(sansLigatures, Normalizer.Form.NFD)
    // … puis l'accent séparé est retiré, et la ponctuation devient une espace.
    return decompose.replace(ACCENTS, "").replace(NI_LETTRE_NI_CHIFFRE, " ").trim()
}

/** Mots d'un texte simplifié : « One-Punch Man » → [one, punch, man]. */
fun mots(texte: String?): List<String> {
    val simplifie = normaliser(texte)
    return if (simplifie.isEmpty()) emptyList() else simplifie.split(' ')
}

/** Vrai si chaque mot cherché est le début d'un mot du titre. */
fun correspond(titre: String, motsCherches: List<String>): Boolean {
    if (motsCherches.isEmpty()) return false
    val motsTitre = mots(titre)
    return motsCherches.all { m -> motsTitre.any { t -> t.startsWith(m) } }
}

/** Séries de la liste dont le titre correspond au texte tapé, dans l'ordre de la liste. */
fun chercherDansListe(liste: List<SerieSuivie>, texte: String): List<SerieSuivie> {
    val cherches = mots(texte)
    if (cherches.isEmpty()) return emptyList()
    return liste.filter { correspond(it.titre, cherches) }
}

/**
 * Résultats TMDB sans les séries déjà montrées dans le bloc « Dans ma liste ».
 * Une série suivie que TMDB trouve mais pas notre recherche (par exemple grâce
 * à son titre original) reste dans les résultats TMDB, avec la mention « Dans
 * ma liste » : elle n'est ainsi jamais cachée.
 */
fun autresResultats(resultats: List<ResultatTmdb>, dejaMontrees: List<SerieSuivie>): List<ResultatTmdb> {
    val ids = dejaMontrees.map { it.tmdbId }.toSet()
    return resultats.filter { it.tmdbId !in ids }
}

/** Nom d'un pays en français à partir de son code TMDB (« JP » → « Japon ») ; code inconnu : tel quel. */
fun nomPays(code: String): String =
    try {
        Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.FRENCH).ifEmpty { code }
    } catch (e: IllformedLocaleException) {
        code
    }

/** Ligne sous le titre d'un résultat : « 2011 · Japon · 進撃の巨人 » (année, pays, titre original s'il diffère). */
fun detailsResultat(r: ResultatTmdb): String {
    val morceaux = mutableListOf<String>()
    r.annee?.let { morceaux += it.toString() }
    if (r.pays.isNotEmpty()) morceaux += r.pays.joinToString(", ") { nomPays(it) }
    if (!r.titreOriginal.isNullOrEmpty() && r.titreOriginal != r.titre) morceaux += r.titreOriginal
    return morceaux.joinToString(" · ")
}

/** Mention d'un résultat déjà suivi : « Dans « Plus tard » » ou « Dans ma liste ». */
fun mentionDansListe(statut: String?): String =
    if (statut == "plus_tard") "Dans « Plus tard »" else "Dans ma liste"

/** Message après « Ajouter » ou « Plus tard ». */
fun messageAjout(titre: String, nouvelle: Boolean, plusTard: Boolean): String = when {
    !nouvelle -> "« $titre » était déjà dans votre liste."
    plusTard -> "« $titre » est maintenant dans « Plus tard »."
    else -> "« $titre » est maintenant dans votre liste."
}
