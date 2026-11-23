package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Forme de la réponse de l'action « saison » : { "episodes": [ … ] }.
@Serializable
private data class ReponseSaison(val episodes: List<EpisodeTmdb> = emptyList())

// Forme de la réponse de l'action « rechercher » : { "resultats": [ … ] }.
@Serializable
private data class ReponseRecherche(val resultats: List<ResultatTmdb> = emptyList())

// Réponse de l'action « fiche » : { "serie": { …, "resume": … }, "saisons": [ … ] }.
// La série a les mêmes champs que la table « series » (sans identifiant
// interne), plus le résumé.
@Serializable
private data class ReponseApercu(val serie: JsonObject, val saisons: List<Saison> = emptyList())

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

    /** Recherche des séries et animés par titre (action « rechercher », 20 résultats au plus). */
    suspend fun rechercher(texte: String): List<ResultatTmdb> {
        val reponse = client.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "rechercher")
                put("texte", texte)
            },
        )
        return json.decodeFromString(ReponseRecherche.serializer(), reponse.bodyAsText()).resultats
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

    /**
     * Aperçu d'une série demandé à TMDB sans rien enregistrer (action
     * « fiche ») : sa fiche, ses saisons et son résumé.
     */
    suspend fun apercu(tmdbId: Int): ApercuTmdb {
        val reponse = client.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "fiche")
                put("tmdbId", tmdbId)
            },
        )
        val brut = json.decodeFromString(ReponseApercu.serializer(), reponse.bodyAsText())
        return ApercuTmdb(
            serie = json.decodeFromJsonElement(SerieCatalogue.serializer(), brut.serie),
            saisons = brut.saisons,
            resume = brut.serie["resume"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Résumé d'une série, demandé à TMDB sans rien enregistrer (action « fiche »). */
    suspend fun resume(tmdbId: Int): String? = apercu(tmdbId).resume

    /** Oublie les titres reçus (bouton « Actualiser », comme un rechargement de la page du site). */
    fun oublierTitres() {
        saisons.clear()
    }
}
