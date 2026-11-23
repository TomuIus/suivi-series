package io.github.tomuius.canapilou.ui.fiche

import io.github.tomuius.canapilou.donnees.Episode
import io.github.tomuius.canapilou.donnees.Saison
import io.github.tomuius.canapilou.donnees.SerieCatalogue
import io.github.tomuius.canapilou.donnees.Suivi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Règles de la fiche d'une série, sans affichage (donc testables sans
// Android). Copiées de js/fiche.js et js/app.js (décision 51 : règles courtes
// et stables, gardées en deux copies) : à modifier aux deux endroits.

/** Statuts TMDB d'une série finie : tous ses épisodes sont alors sortis. */
val SERIES_FINIES = setOf("Ended", "Canceled")

// Au-delà de ce délai, la fiche d'une série est remise à jour depuis TMDB
// quand on l'ouvre (même valeur que DELAI_MISE_A_JOUR sur le site : 24 h, à
// revoir après des tests).
val DELAI_MISE_A_JOUR: Duration = Duration.ofHours(24)

/** État d'un épisode pour l'affichage. */
enum class EtatEpisode { VU, NON_VU, A_VENIR }

/**
 * Un épisode est-il déjà sorti ? Même règle que la vue « ma_liste », la
 * fonction SQL « choisir_statut » et estDiffuse dans js/fiche.js : à modifier
 * à tous ces endroits. Les épisodes spéciaux (saison 0) restent toujours
 * cochables (décision 14).
 */
fun estDiffuse(serie: SerieCatalogue, saison: Int, episode: Int): Boolean {
    if (saison == 0) return true
    if (serie.statutDiffusion in SERIES_FINIES) return true
    val derniereSaison = serie.derniereSaisonDiffusee ?: return false
    val dernierEpisode = serie.dernierEpisodeDiffuse
    // Sur le site, un « dernier épisode » vide vaut 0 dans la comparaison,
    // qui est donc fausse (les épisodes commencent à 1) : même résultat ici.
    return saison < derniereSaison || (saison == derniereSaison && dernierEpisode != null && episode <= dernierEpisode)
}

/** « Vu », « non vu » ou « à venir » (pas encore sorti). */
fun etatEpisode(serie: SerieCatalogue, vus: Set<Episode>, saison: Int, episode: Int): EtatEpisode = when {
    Episode(saison, episode) in vus -> EtatEpisode.VU
    estDiffuse(serie, saison, episode) -> EtatEpisode.NON_VU
    else -> EtatEpisode.A_VENIR
}

/** Saisons dans l'ordre d'affichage : saisons normales, puis les épisodes spéciaux en dernier. */
fun ordonnerSaisons(saisons: List<Saison>): List<Saison> =
    saisons.filter { it.numero > 0 } + saisons.filter { it.numero == 0 }

/**
 * Saison dépliée à l'ouverture : celle du prochain épisode, sinon la
 * dernière saison normale, sinon les spéciaux (0).
 */
fun saisonInitiale(saisons: List<Saison>, suivi: Suivi?): Int =
    suivi?.prochaineSaison ?: saisons.lastOrNull { it.numero > 0 }?.numero ?: 0

/** Nombre d'épisodes cochés dans une saison (seulement ceux que la saison compte). */
fun nbVusSaison(saison: Saison, vus: Set<Episode>): Int =
    (1..saison.nbEpisodes).count { Episode(saison.numero, it) in vus }

/**
 * Après avoir coché un épisode : les épisodes déjà sortis AVANT lui (saisons
 * normales seulement) qui ne sont pas cochés, à proposer de cocher aussi
 * (utile pour une série commencée ailleurs). Liste vide pour un spécial.
 */
fun episodesPrecedentsNonVus(
    serie: SerieCatalogue,
    saisons: List<Saison>,
    vus: Set<Episode>,
    saison: Int,
    episode: Int,
): List<Episode> {
    if (saison == 0) return emptyList() // les spéciaux ne comptent pas
    val manquants = mutableListOf<Episode>()
    for (s in saisons) {
        if (s.numero == 0 || s.numero > saison) continue
        for (e in 1..s.nbEpisodes) {
            if (s.numero == saison && e >= episode) break
            if (estDiffuse(serie, s.numero, e) && Episode(s.numero, e) !in vus) manquants += Episode(s.numero, e)
        }
    }
    return manquants
}

/**
 * La fiche doit-elle être remise à jour depuis TMDB à l'ouverture ? Oui si
 * elle date de plus de 24 h ou si le classement série / animé manque. Une
 * date absente ou illisible ne compte pas comme ancienne (comme sur le site,
 * où le calcul donne alors « faux »).
 */
fun ficheAncienne(serie: SerieCatalogue, maintenant: Instant): Boolean {
    if (serie.estAnime == null) return true
    val majLe = try {
        serie.ficheMajLe?.let { OffsetDateTime.parse(it).toInstant() }
    } catch (e: Exception) {
        null
    } ?: return false
    return Duration.between(majLe, maintenant) >= DELAI_MISE_A_JOUR
}

// Statuts de diffusion TMDB en français (LIBELLES_DIFFUSION dans js/app.js).
private val LIBELLES_DIFFUSION = mapOf(
    "Returning Series" to "En diffusion",
    "In Production" to "En production",
    "Planned" to "Prévue",
    "Pilot" to "Pilote",
    "Ended" to "Terminée",
    "Canceled" to "Annulée",
)

/** « En diffusion », « Terminée »… ; statut inconnu de la liste : tel quel. */
fun libelleDiffusion(statut: String?): String =
    LIBELLES_DIFFUSION[statut] ?: statut ?: "Statut de diffusion inconnu"

/** Ligne sous le titre : « En diffusion | 5 / 808 épisodes vus ». */
fun ligneDiffusion(serie: SerieCatalogue, suivi: Suivi): String =
    "${libelleDiffusion(serie.statutDiffusion)} | ${suivi.nbVus} / ${suivi.nbDiffuses} épisodes vus"

/** « Saison 2 » ou « Épisodes spéciaux ». */
fun titreSaison(numero: Int): String = if (numero == 0) "Épisodes spéciaux" else "Saison $numero"

/**
 * Titre affiché pour un épisode : celui de TMDB, sinon « … » pendant le
 * chargement, sinon « Épisode 5 ».
 */
fun titreEpisodeAffiche(titreTmdb: String?, chargement: Boolean, episode: Int): String = when {
    !titreTmdb.isNullOrEmpty() -> titreTmdb
    chargement -> "…"
    else -> "Épisode $episode"
}

// Dates en français : « 29 sept. 2023 » (comme Intl.DateTimeFormat('fr-FR',
// { day: 'numeric', month: 'short', year: 'numeric' }) sur le site).
private val FORMAT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/**
 * Date de sortie affichée (« 29 sept. 2023 », « à venir · 29 sept. 2023 »),
 * ou "" si TMDB ne la donne pas (ou si elle est illisible).
 */
fun texteDate(date: String?, aVenir: Boolean): String {
    if (date.isNullOrEmpty()) return ""
    val texte = try {
        LocalDate.parse(date).format(FORMAT_DATE)
    } catch (e: Exception) {
        return ""
    }
    return if (aVenir) "à venir · $texte" else texte
}

/** Question de la barre du bas après avoir coché un épisode. */
fun texteInvite(nombre: Int): String =
    if (nombre == 1) "Cocher aussi l'épisode précédent non vu ?"
    else "Cocher aussi les $nombre épisodes précédents non vus ?"
