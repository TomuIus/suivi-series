package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

// Dépôt « liste » : lecture de vos séries (comme listerSuivis dans js/base.js).
// C'est ici, et seulement ici, qu'on ajoutera plus tard une copie locale pour
// l'utilisation hors connexion (décision 58) : les écrans n'auront pas à changer.
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
}
