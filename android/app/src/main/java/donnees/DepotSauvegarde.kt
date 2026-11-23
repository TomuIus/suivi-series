package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Lignes lues par requête (limite par défaut de l'API de Supabase, comme
// LIGNES_PAR_PAGE dans js/base.js).
private const val LIGNES_PAR_PAGE = 1000

// Import : lignes envoyées par requête (comme LIGNES_PAR_ENVOI dans
// js/base.js, choix de Claude, à revoir) et identifiants TMDB par recherche
// dans le catalogue (ils passent dans l'adresse de la requête, comme sur le site).
private const val LIGNES_PAR_ENVOI = 500
private const val IDS_PAR_REQUETE = 200

// Ce que la base renvoie du catalogue : identifiant interne et identifiant TMDB.
@Serializable
private data class IdCatalogue(val id: Long, @SerialName("tmdb_id") val tmdbId: Int)

// Dépôt « sauvegarde » (décisions 22, 40, 92 et 95) : lecture de toutes vos
// données pour l'export (comme lireDonneesPourExport de js/base.js), et
// écritures de l'import (comme idsCatalogue, suivreSeriesImportees,
// cocherEpisodesImportes et viderListe). Les règles d'accès de la base (RLS)
// ne laissent lire et écrire que vos propres suivis et épisodes.
class DepotSauvegarde(private val client: SupabaseClient) {

    /**
     * Vos séries suivies (avec leur série du catalogue, dans « series ») et
     * vos épisodes cochés, lus page par page, dans l'ordre du site. Lignes
     * gardées telles que la base les donne : le fichier les recopie.
     */
    suspend fun lireDonneesPourExport(): Pair<List<JsonObject>, List<JsonObject>> = coroutineScope {
        val suivis = async {
            lireParPages("suivis", Columns.raw(COLONNES_SUIVIS)) {
                order(column = "ajoute_le", order = Order.ASCENDING)
                order(column = "serie_id", order = Order.ASCENDING)
            }
        }
        val vus = async {
            lireParPages("episodes_vus", Columns.list("serie_id", "saison", "episode", "vu_le")) {
                order(column = "serie_id", order = Order.ASCENDING)
                order(column = "saison", order = Order.ASCENDING)
                order(column = "episode", order = Order.ASCENDING)
            }
        }
        suivis.await() to vus.await()
    }

    /** Identifiants internes des séries déjà dans le catalogue : identifiant TMDB → identifiant interne. */
    suspend fun idsCatalogue(tmdbIds: List<Int>): Map<Int, Long> {
        val ids = HashMap<Int, Long>()
        for (morceau in tmdbIds.chunked(IDS_PAR_REQUETE)) {
            client.from("series")
                .select(columns = Columns.list("id", "tmdb_id")) {
                    filter { isIn("tmdb_id", morceau) }
                }
                .decodeList<IdCatalogue>()
                .forEach { ids[it.tmdbId] = it.id }
        }
        return ids
    }

    /**
     * Ajoute des séries à votre liste avec leur catégorie et leur date d'ajout
     * d'origine. Une série déjà suivie est laissée telle quelle (écriture qui
     * ignore les doublons). L'utilisateur n'est pas indiqué : la base le
     * remplit avec le compte connecté. Lignes écrites à la main pour que
     * « categorie_manuelle » soit toujours envoyée, même vide (null).
     */
    suspend fun suivreSeriesImportees(lignes: List<LigneSuivi>) {
        for (morceau in lignes.chunked(LIGNES_PAR_ENVOI)) {
            val valeurs = morceau.map {
                buildJsonObject {
                    put("serie_id", it.serieId)
                    put("categorie_manuelle", it.categorieManuelle)
                    put("ajoute_le", it.ajouteLe)
                }
            }
            client.from("suivis").upsert(valeurs) {
                onConflict = "utilisateur_id,serie_id"
                ignoreDuplicates = true
            }
        }
    }

    /** Coche des épisodes avec leur date d'origine ; un épisode déjà coché est laissé tel quel (sa date ne change pas). */
    suspend fun cocherEpisodesImportes(lignes: List<LigneEpisode>) {
        for (morceau in lignes.chunked(LIGNES_PAR_ENVOI)) {
            val valeurs = morceau.map {
                buildJsonObject {
                    put("serie_id", it.serieId)
                    put("saison", it.saison)
                    put("episode", it.episode)
                    put("vu_le", it.vuLe)
                }
            }
            client.from("episodes_vus").upsert(valeurs) {
                onConflict = "utilisateur_id,serie_id,saison,episode"
                ignoreDuplicates = true
            }
        }
    }

    /**
     * Retire toutes les séries de votre liste, et avec elles leurs épisodes
     * cochés (« on delete cascade », comme pour un retrait). Les règles RLS
     * limitent la suppression à vos propres suivis. Le filtre « serie_id > 0 »
     * est toujours vrai (les identifiants commencent à 1 : « generated always
     * as identity » dans la base) ; il est là par précaution, comme celui du
     * site (« serie_id non vide »), une suppression sans aucun filtre pouvant
     * être refusée (non vérifié).
     */
    suspend fun viderListe() {
        client.from("suivis").delete {
            filter { gt("serie_id", 0) }
        }
    }

    // Toutes les lignes d'une table, 1 000 à la fois, jusqu'à une page
    // incomplète. Ordre stable, sinon une ligne pourrait être lue deux fois
    // ou oubliée d'une page à l'autre.
    private suspend fun lireParPages(
        table: String,
        colonnes: Columns,
        ordre: PostgrestRequestBuilder.() -> Unit,
    ): List<JsonObject> {
        val tous = mutableListOf<JsonObject>()
        var debut = 0L
        while (true) {
            val page = client.from(table)
                .select(columns = colonnes) {
                    ordre()
                    range(debut..(debut + LIGNES_PAR_PAGE - 1))
                }
                .decodeList<JsonObject>()
            tous += page
            if (page.size < LIGNES_PAR_PAGE) return tous
            debut += LIGNES_PAR_PAGE
        }
    }

    private companion object {
        // Comme sur le site : la série du catalogue est jointe (« series (…) »).
        const val COLONNES_SUIVIS =
            "serie_id, statut_manuel, categorie_manuelle, ajoute_le, series (tmdb_id, imdb_id, tvdb_id, titre, titre_original)"
    }
}
