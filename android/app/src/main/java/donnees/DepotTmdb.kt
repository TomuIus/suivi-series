package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un épisode d'une saison, tel que la fonction serveur « tmdb » le renvoie. */
@Serializable
data class EpisodeTmdb(
    val numero: Int,
    val titre: String? = null, // null quand TMDB n'a pas de titre
    val date: String? = null,  // date de sortie, « 2023-09-29 », ou null
)

// Forme de la réponse de l'action « saison » : { "episodes": [ … ] }.
@Serializable
private data class ReponseSaison(val episodes: List<EpisodeTmdb> = emptyList())

/** Réponse de l'action « ajouter » : la série copiée (ou remise à jour) dans le catalogue. */
@Serializable
data class SerieImportee(val id: Long, val titre: String, val resume: String? = null)

// Réponse de l'action « fiche » : { "serie": { …, "resume": … }, "saisons": [ … ] }.
// Pour l'instant, seul le résumé sert (l'aperçu complet viendra avec la recherche).
@Serializable
private data class ResumeSeul(val resume: String? = null)

@Serializable
private data class ReponseApercu(val serie: ResumeSeul)

// Dépôt « TMDB » : tout ce qui passe par la fonction serveur « tmdb », comme
// js/tmdb.js sur le site. La clé TMDB reste sur le serveur (décision 8) ;
// l'appli envoie seulement son jeton de connexion.
class DepotTmdb(private val client: SupabaseClient) {

    // Lecture du JSON : on ignore les champs que l'appli ne connaît pas encore,
    // pour qu'un ajout côté serveur ne casse pas une appli déjà installée.
    private val json = Json { ignoreUnknownKeys = true }

    // Titres déjà reçus, gardés en mémoire tant que l'appli est ouverte, jamais
    // enregistrés sur le téléphone (décisions 12 et 38). Clé : (TMDB, saison).
    // « Concurrent » : plusieurs demandes peuvent arriver en même temps.
    private val saisons = ConcurrentHashMap<Pair<Int, Int>, List<EpisodeTmdb>>()

    /**
     * Épisodes d'une saison (numéro, titre, date), demandés à TMDB par la
     * fonction serveur (action « saison »), ou repris de la mémoire s'ils ont
     * déjà été reçus. Une erreur (réseau, TMDB…) remonte à l'appelant ; rien
     * n'est alors gardé, la prochaine demande réessaiera.
     */
    suspend fun episodesSaison(tmdbId: Int, saison: Int): List<EpisodeTmdb> {
        val cle = tmdbId to saison
        saisons[cle]?.let { return it }
        val reponse = client.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "saison")
                put("tmdbId", tmdbId)
                put("saison", saison)
            },
        )
        val episodes = json.decodeFromString(ReponseSaison.serializer(), reponse.bodyAsText()).episodes
        saisons[cle] = episodes
        return episodes
    }

    /**
     * Copie (ou remet à jour) la fiche TMDB d'une série dans le catalogue
     * (action « ajouter », comme importerSerie sur le site) : titre, affiche,
     * saisons, dernier épisode sorti, classement animé, durées. Renvoie
     * l'identifiant interne, le titre et le résumé.
     */
    suspend fun importerSerie(tmdbId: Int): SerieImportee {
        val reponse = client.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "ajouter")
                put("tmdbId", tmdbId)
            },
        )
        return json.decodeFromString(SerieImportee.serializer(), reponse.bodyAsText())
    }

    /** Résumé d'une série, demandé à TMDB sans rien enregistrer (action « fiche »). */
    suspend fun resume(tmdbId: Int): String? {
        val reponse = client.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "fiche")
                put("tmdbId", tmdbId)
            },
        )
        return json.decodeFromString(ReponseApercu.serializer(), reponse.bodyAsText()).serie.resume
    }

    /** Oublie les titres reçus (bouton « Actualiser », comme un rechargement de la page du site). */
    fun oublierTitres() {
        saisons.clear()
    }
}
