package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

// Lignes lues par requête (limite par défaut de l'API de Supabase, comme
// LIGNES_PAR_PAGE dans js/base.js).
private const val LIGNES_PAR_PAGE = 1000

// Dépôt « sauvegarde » (décisions 22, 40 et 92) : lecture de toutes vos
// données pour l'export (comme lireDonneesPourExport de js/base.js), et plus
// tard l'import (partie C). Les règles d'accès de la base (RLS) ne laissent
// lire que vos propres suivis et épisodes.
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
