package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Une ligne de la vue « ma_liste » de la base : une série suivie, avec ce que
// la base calcule pour elle (statut, épisodes sortis et vus, prochain épisode).
// « @SerialName » relie chaque propriété à sa colonne (noms de la base en
// snake_case, noms Kotlin en camelCase). « @Serializable » laisse le plugin de
// sérialisation écrire la conversion JSON → objet.
@Serializable
data class SerieSuivie(
    @SerialName("serie_id") val serieId: Long,
    @SerialName("tmdb_id") val tmdbId: Int,
    val titre: String,
    @SerialName("affiche_chemin") val afficheChemin: String? = null,
    @SerialName("statut_diffusion") val statutDiffusion: String? = null,
    // Statut choisi à la main (null = automatique) et statut final (manuel,
    // sinon calculé par la vue : a_voir, en_cours, a_jour, termine…).
    @SerialName("statut_manuel") val statutManuel: String? = null,
    val statut: String,
    @SerialName("nb_diffuses") val nbDiffuses: Int,
    @SerialName("nb_vus") val nbVus: Int,
    @SerialName("prochaine_saison") val prochaineSaison: Int? = null,
    @SerialName("prochain_episode") val prochainEpisode: Int? = null,
    @SerialName("derniere_activite") val derniereActivite: String? = null,
    @SerialName("est_anime") val estAnime: Boolean = false,
)

// Colonnes demandées à la base : exactement celles ci-dessus.
internal val COLONNES_SERIE_SUIVIE = listOf(
    "serie_id", "tmdb_id", "titre", "affiche_chemin", "statut_diffusion", "statut_manuel",
    "statut", "nb_diffuses", "nb_vus", "prochaine_saison", "prochain_episode",
    "derniere_activite", "est_anime",
)
