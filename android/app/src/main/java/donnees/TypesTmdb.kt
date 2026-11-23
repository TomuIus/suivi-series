package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.Serializable

// Données renvoyées par la fonction serveur « tmdb » (voir DepotTmdb), dans
// un fichier à part, sans Supabase, pour pouvoir les utiliser dans les tests.

/** Un épisode d'une saison, tel que la fonction serveur « tmdb » le renvoie. */
@Serializable
data class EpisodeTmdb(
    val numero: Int,
    val titre: String? = null, // null quand TMDB n'a pas de titre
    val date: String? = null,  // date de sortie, « 2023-09-29 », ou null
)

/** Réponse de l'action « ajouter » : la série copiée (ou remise à jour) dans le catalogue. */
@Serializable
data class SerieImportee(val id: Long, val titre: String, val resume: String? = null)

/**
 * Un résultat de recherche (action « rechercher », 20 au plus), comme sur le
 * site : identifiant TMDB, titres, année de première diffusion, chemin de
 * l'affiche, résumé, pays d'origine (codes « JP »…), classement animé.
 */
@Serializable
data class ResultatTmdb(
    val tmdbId: Int,
    val titre: String,
    val titreOriginal: String? = null,
    val annee: Int? = null,
    val affiche: String? = null,
    val resume: String? = null,
    val pays: List<String> = emptyList(),
    val anime: Boolean = false,
)
