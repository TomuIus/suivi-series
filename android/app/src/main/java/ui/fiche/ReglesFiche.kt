package io.github.tomuius.canapilou.ui.fiche

import io.github.tomuius.canapilou.donnees.Episode
import io.github.tomuius.canapilou.donnees.Saison
import io.github.tomuius.canapilou.donnees.SerieCatalogue
import io.github.tomuius.canapilou.donnees.Suivi
import io.github.tomuius.canapilou.ui.liste.episodesVus
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
 * Saison dépliée à l'ouverture. Série suivie : celle du prochain épisode,
 * sinon la dernière saison normale, sinon les spéciaux (0). Aperçu (pas de
 * suivi) : la première saison normale, sinon les spéciaux.
 */
fun saisonInitiale(saisons: List<Saison>, suivi: Suivi?): Int =
    if (suivi != null) {
        suivi.prochaineSaison ?: saisons.lastOrNull { it.numero > 0 }?.numero ?: 0
    } else {
        saisons.firstOrNull { it.numero > 0 }?.numero ?: 0
    }

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
    "${libelleDiffusion(serie.statutDiffusion)} | ${suivi.nbVus} / ${suivi.nbDiffuses} ${episodesVus(suivi.nbVus)}"

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

// --- Réglages : statut, catégorie, retrait (partie B, décisions 67 et 68) ---

// Libellés des statuts (codes de la base), comme LIBELLES_STATUT dans
// js/app.js : « a_voir » (pas encore commencée) s'affiche « En cours »
// depuis la décision 27.
private val LIBELLES_STATUT = mapOf(
    "a_voir" to "En cours",
    "en_cours" to "En cours",
    "plus_tard" to "Plus tard",
    "a_jour" to "À jour",
    "termine" to "Terminé",
    "abandonne" to "Abandonné",
)

/** Statuts proposés dans le menu « Statut », dans cet ordre (STATUTS_MANUELS du site). */
val STATUTS_MANUELS = listOf("en_cours", "plus_tard", "a_jour", "termine", "abandonne")

/** « En cours », « À jour »… ; code inconnu : « — ». */
fun libelleStatut(code: String?): String = LIBELLES_STATUT[code] ?: "—"

/**
 * Statut que la base calculerait toute seule (même règle que la vue
 * « ma_liste »), affiché à côté de « Automatique » dans le menu.
 */
fun statutCalcule(serie: SerieCatalogue, suivi: Suivi): String = when {
    suivi.nbVus == 0 -> "a_voir"
    suivi.nbVus < suivi.nbDiffuses -> "en_cours"
    serie.statutDiffusion in SERIES_FINIES -> "termine"
    else -> "a_jour"
}

/**
 * Choix affiché dans le menu « Statut » : null = « Automatique ». Un ancien
 * statut manuel « À voir » apparaît comme « En cours » (même section depuis
 * la décision 27).
 */
fun valeurStatutManuel(suivi: Suivi): String? = when (val valeur = suivi.statutManuel) {
    null, "" -> null
    "a_voir" -> "en_cours"
    else -> valeur
}

/** Classement automatique de TMDB, affiché à côté de « Automatique » dans le menu « Catégorie ». */
fun categorieCalculee(serie: SerieCatalogue): String = if (serie.estAnime == true) "anime" else "serie"

/** « Série » ou « Animé ». */
fun libelleCategorie(code: String): String = if (code == "anime") "Animé" else "Série"

/** Message après un statut qui a coché des épisodes (avec « Annuler »). */
fun texteEpisodesCoches(titre: String, nombre: Int): String =
    "« $titre » : ${if (nombre == 1) "1 épisode coché" else "$nombre épisodes cochés"}."

/** Message après « Annuler ». */
fun texteRemisCommeAvant(titre: String): String = "« $titre » : épisodes décochés et statut remis comme avant."

// --- Aperçu (décision 77) ---

/** « 3 saisons », « 1 saison », « Aucune saison annoncée » (sans compter les spéciaux), comme nombreSaisons sur le site. */
fun nombreSaisons(saisons: List<Saison>): String {
    val n = saisons.count { it.numero > 0 }
    return when (n) {
        0 -> "Aucune saison annoncée"
        1 -> "1 saison"
        else -> "$n saisons"
    }
}

/** Ligne de l'aperçu sous le titre : « En diffusion | 3 saisons ». */
fun ligneApercu(serie: SerieCatalogue, saisons: List<Saison>): String =
    "${libelleDiffusion(serie.statutDiffusion)} | ${nombreSaisons(saisons)}"

/**
 * Étiquette « Animé » de la fiche (estAnimeFiche du site) : la correction de
 * l'utilisateur pour une série suivie, sinon le classement automatique.
 */
fun estAnimeFiche(serie: SerieCatalogue, suivi: Suivi?): Boolean =
    suivi?.estAnime ?: (serie.estAnime == true)

/**
 * Compte d'une saison dans l'aperçu : « 10 épisodes », « 1 épisode »,
 * « 0 épisode » (le site écrit encore « 1 épisodes » : à corriger de son côté).
 */
fun nombreEpisodes(n: Int): String = if (n <= 1) "$n épisode" else "$n épisodes"

/** Phrase de la confirmation de retrait, selon le nombre d'épisodes cochés (perteEpisodes du site). */
fun perteEpisodes(nombre: Int): String = when (nombre) {
    0 -> "Aucun épisode n'est coché."
    1 -> "L'épisode coché sera effacé."
    else -> "Les $nombre épisodes cochés seront effacés."
}

/** Message affiché dans « Ma liste » après le retrait. */
fun texteRetire(titre: String): String = "« $titre » ne fait plus partie de votre liste."
