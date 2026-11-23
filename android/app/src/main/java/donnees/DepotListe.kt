package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

// Lectures longues page par page : l'API de Supabase renvoie 1 000 lignes au
// plus par requête (même valeur que LIGNES_PAR_PAGE dans js/base.js).
private const val LIGNES_PAR_PAGE = 1000

// Dépôt « liste » : vos séries et vos épisodes cochés (comme listerSuivis,
// chargerFiche, marquerVus et annulerVu dans js/base.js). C'est ici, et
// seulement ici, qu'on ajoutera plus tard une copie locale et une file
// d'attente pour l'utilisation hors connexion (décision 58) : les écrans
// n'auront pas à changer.
class DepotListe(private val client: SupabaseClient) {

    // Augmente à chaque écriture (épisode coché ou décoché…). « Ma liste »
    // s'en sert pour savoir, quand on revient d'une fiche, s'il faut se relire.
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /** À appeler quand une donnée de la liste a pu changer ailleurs (fiche remise à jour depuis TMDB…). */
    fun noterChangement() {
        _version.update { it + 1 }
    }

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
     * Tout ce qu'il faut pour afficher la fiche d'une série : la série du
     * catalogue (null si elle n'existe pas), ses saisons, votre suivi (null si
     * elle n'est pas dans votre liste) et vos épisodes cochés. Les quatre
     * lectures partent en même temps (« async »), comme sur le site.
     */
    suspend fun chargerFiche(serieId: Long): DonneesFiche = coroutineScope {
        val serie = async {
            client.from("series")
                .select(columns = Columns.list(*COLONNES_SERIE_CATALOGUE.toTypedArray())) {
                    filter { eq("id", serieId) }
                }
                .decodeList<SerieCatalogue>()
                .firstOrNull()
        }
        val saisons = async {
            client.from("saisons")
                .select(columns = Columns.list("numero", "nb_episodes")) {
                    filter { eq("serie_id", serieId) }
                    order(column = "numero", order = Order.ASCENDING)
                }
                .decodeList<Saison>()
        }
        val suivi = async { lireSuivi(serieId) }
        val vus = async { lireEpisodesVus(serieId) }
        DonneesFiche(serie.await(), saisons.await(), suivi.await(), vus.await())
    }

    /** Relit votre suivi d'une série (compteurs, prochain épisode, statut), ou null. */
    suspend fun lireSuivi(serieId: Long): Suivi? =
        client.from("ma_liste")
            .select(columns = Columns.list(*COLONNES_SUIVI.toTypedArray())) {
                filter { eq("serie_id", serieId) }
            }
            .decodeList<Suivi>()
            .firstOrNull()

    // Épisodes cochés d'une série, page par page (un long animé peut dépasser
    // 1 000). Ordre stable (saison, épisode), sinon une ligne pourrait être lue
    // deux fois ou oubliée d'une page à l'autre.
    private suspend fun lireEpisodesVus(serieId: Long): List<Episode> {
        val tous = mutableListOf<Episode>()
        var debut = 0L
        while (true) {
            val page = client.from("episodes_vus")
                .select(columns = Columns.list("saison", "episode")) {
                    filter { eq("serie_id", serieId) }
                    order(column = "saison", order = Order.ASCENDING)
                    order(column = "episode", order = Order.ASCENDING)
                    range(debut..(debut + LIGNES_PAR_PAGE - 1))
                }
                .decodeList<Episode>()
            tous += page
            if (page.size < LIGNES_PAR_PAGE) return tous
            debut += LIGNES_PAR_PAGE
        }
    }

    /** Coche un épisode comme vu (voir [marquerVus]). */
    suspend fun marquerVu(serieId: Long, saison: Int, episode: Int) {
        marquerVus(serieId, listOf(Episode(saison, episode)))
    }

    /**
     * Coche plusieurs épisodes d'un coup. Ceux qui l'étaient déjà sont
     * simplement ignorés (« ON CONFLICT DO NOTHING » dans la base), comme
     * marquerVus sur le site. Le déclencheur de la base sort la série de
     * « Plus tard » si besoin (décision 26).
     */
    suspend fun marquerVus(serieId: Long, episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        try {
            client.from("episodes_vus").upsert(episodes.map { EpisodeVu(serieId, it.saison, it.episode) }) {
                onConflict = "utilisateur_id,serie_id,saison,episode"
                ignoreDuplicates = true
            }
        } finally {
            // Même en cas d'erreur : l'écriture a pu passer malgré tout
            // (réponse perdue), mieux vaut que « Ma liste » se relise.
            noterChangement()
        }
    }

    /** Décoche un épisode (les règles RLS limitent la suppression à vos propres épisodes). */
    suspend fun annulerVu(serieId: Long, saison: Int, episode: Int) {
        try {
            client.from("episodes_vus").delete {
                filter {
                    eq("serie_id", serieId)
                    eq("saison", saison)
                    eq("episode", episode)
                }
            }
        } finally {
            noterChangement()
        }
    }
}
