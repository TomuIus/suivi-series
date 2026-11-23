package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Données de la fiche d'une série, lues comme chargerFiche dans js/base.js.

/** Une série du catalogue (table « series », fiche TMDB partagée par tous les comptes). */
@Serializable
data class SerieCatalogue(
    val id: Long,
    @SerialName("tmdb_id") val tmdbId: Int,
    val titre: String,
    @SerialName("titre_original") val titreOriginal: String? = null,
    @SerialName("affiche_chemin") val afficheChemin: String? = null,
    // « Returning Series », « Ended »… (statut TMDB, traduit à l'affichage)
    @SerialName("statut_diffusion") val statutDiffusion: String? = null,
    // Dernier épisode sorti selon TMDB : sert à savoir quels épisodes sont sortis.
    @SerialName("derniere_saison_diffusee") val derniereSaisonDiffusee: Int? = null,
    @SerialName("dernier_episode_diffuse") val dernierEpisodeDiffuse: Int? = null,
    // Date de la dernière mise à jour depuis TMDB (« 2026-11-20T10:00:00+00:00 »).
    @SerialName("fiche_maj_le") val ficheMajLe: String? = null,
    // Classement automatique série / animé ; null = pas encore déterminé.
    @SerialName("est_anime") val estAnime: Boolean? = null,
)

internal val COLONNES_SERIE_CATALOGUE = listOf(
    "id", "tmdb_id", "titre", "titre_original", "affiche_chemin", "statut_diffusion",
    "derniere_saison_diffusee", "dernier_episode_diffuse", "fiche_maj_le", "est_anime",
)

/** Une saison d'une série (table « saisons ») ; la saison 0 regroupe les épisodes spéciaux. */
@Serializable
data class Saison(
    val numero: Int,
    @SerialName("nb_episodes") val nbEpisodes: Int,
)

/** Votre suivi d'une série : une ligne de la vue « ma_liste », colonnes utiles à la fiche. */
@Serializable
data class Suivi(
    @SerialName("serie_id") val serieId: Long,
    val statut: String,
    @SerialName("statut_manuel") val statutManuel: String? = null,
    @SerialName("nb_diffuses") val nbDiffuses: Int,
    @SerialName("nb_vus") val nbVus: Int,
    @SerialName("prochaine_saison") val prochaineSaison: Int? = null,
    @SerialName("prochain_episode") val prochainEpisode: Int? = null,
    @SerialName("categorie_manuelle") val categorieManuelle: String? = null,
    @SerialName("est_anime") val estAnime: Boolean = false,
)

internal val COLONNES_SUIVI = listOf(
    "serie_id", "statut", "statut_manuel", "nb_diffuses", "nb_vus", "prochaine_saison",
    "prochain_episode", "categorie_manuelle", "est_anime",
)

/** Un épisode (saison, numéro) : épisode coché, ou à cocher. */
@Serializable
data class Episode(val saison: Int, val episode: Int)

/** Tout ce qu'il faut pour afficher une fiche ; [suivi] vaut null si la série n'est pas dans votre liste. */
data class DonneesFiche(
    val serie: SerieCatalogue?,
    val saisons: List<Saison>,
    val suivi: Suivi?,
    val vus: List<Episode>,
)
