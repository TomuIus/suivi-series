package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Une ligne à écrire dans la table « episodes_vus ». L'utilisateur n'est pas
// indiqué : la base le remplit elle-même avec le compte connecté.
@Serializable
internal data class EpisodeVu(
    @SerialName("serie_id") val serieId: Long,
    val saison: Int,
    val episode: Int,
)

// Dépôt « liste » : vos séries et vos épisodes cochés (comme listerSuivis,
// marquerVu et annulerVu dans js/base.js). C'est ici, et seulement ici, qu'on
// ajoutera plus tard une copie locale et une file d'attente pour l'utilisation
// hors connexion (décision 58) : les écrans n'auront pas à changer.
class DepotListe(private val client: SupabaseClient) {

    /**
     * Vos séries, de l'activité la plus récente à la plus ancienne. La vue
     * « ma_liste » calcule tout (statut, compteurs, prochain épisode) ; les
     * règles RLS garantissent qu'on ne reçoit que ses propres séries.
     */
    suspend fun lireMaListe(): List<SerieSuivie> =
        client.from("ma_liste")
            .select(columns = Columns.list(*COLONNES_SERIE_SUIVIE.toTypedArray())) {
                order(column = "derniere_activite", order = Order.DESCENDING)
            }
            .decodeList<SerieSuivie>()

    /**
     * Coche un épisode comme vu. S'il l'était déjà, ce n'est pas une erreur :
     * comme marquerVus sur le site, l'écriture ignore les doublons (« ON
     * CONFLICT DO NOTHING » dans la base). Le déclencheur de la base sort la
     * série de « Plus tard » si besoin (décision 26).
     */
    suspend fun marquerVu(serieId: Long, saison: Int, episode: Int) {
        client.from("episodes_vus").upsert(EpisodeVu(serieId, saison, episode)) {
            onConflict = "utilisateur_id,serie_id,saison,episode"
            ignoreDuplicates = true
        }
    }

    /** Décoche un épisode (les règles RLS limitent la suppression à vos propres épisodes). */
    suspend fun annulerVu(serieId: Long, saison: Int, episode: Int) {
        client.from("episodes_vus").delete {
            filter {
                eq("serie_id", serieId)
                eq("saison", saison)
                eq("episode", episode)
            }
        }
    }
}
