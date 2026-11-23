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
    // Entretien du catalogue (décisions 24, 31 et 42) : classement série /
    // animé pas encore fait (null), durées des épisodes à calculer, fiche
    // TMDB de plus de 5 mois. Les deux dernières ne sont jamais vides dans la
    // base ; acceptées vides quand même, par précaution (vide = non).
    @SerialName("anime_detecte") val animeDetecte: Boolean? = null,
    @SerialName("durees_a_calculer") val dureesACalculer: Boolean? = null,
    @SerialName("fiche_a_rafraichir") val ficheARafraichir: Boolean? = null,
)

// Colonnes demandées à la base : exactement celles ci-dessus.
internal val COLONNES_SERIE_SUIVIE = listOf(
    "serie_id", "tmdb_id", "titre", "affiche_chemin", "statut_diffusion", "statut_manuel",
    "statut", "nb_diffuses", "nb_vus", "prochaine_saison", "prochain_episode",
    "derniere_activite", "est_anime", "anime_detecte", "durees_a_calculer", "fiche_a_rafraichir",
)

/**
 * Séries dont la fiche du catalogue est à remettre à jour depuis TMDB
 * (action « ajouter ») : sans classement série / animé, sans durées des
 * épisodes, ou de plus de 5 mois (les conditions de TMDB interdisent de
 * garder ses informations plus de 6 mois). Même règle que completerFiches
 * dans js/app.js ; ordre de la liste conservé.
 */
fun fichesAEntretenir(series: List<SerieSuivie>): List<SerieSuivie> =
    series.filter { it.animeDetecte == null || it.dureesACalculer == true || it.ficheARafraichir == true }
