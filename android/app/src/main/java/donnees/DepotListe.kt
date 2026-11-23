package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Une ligne à écrire dans la table « episodes_vus ». L'utilisateur n'est pas
// indiqué : la base le remplit elle-même avec le compte connecté.
@Serializable
internal data class EpisodeVu(
    @SerialName("serie_id") val serieId: Long,
    val saison: Int,
    val episode: Int,
)

// Une ligne à écrire dans la table « suivis » (série ajoutée à votre liste) ;
// statut manuel « plus_tard » pour « Plus tard », sinon rien (automatique).
@Serializable
internal data class NouveauSuivi(
    @SerialName("serie_id") val serieId: Long,
    @SerialName("statut_manuel") val statutManuel: String? = null,
)

// Ce que la base renvoie des lignes réellement ajoutées.
@Serializable
internal data class SuiviAjoute(@SerialName("serie_id") val serieId: Long)

// Lectures longues page par page : l'API de Supabase renvoie 1 000 lignes au
// plus par requête (même valeur que LIGNES_PAR_PAGE dans js/base.js).
private const val LIGNES_PAR_PAGE = 1000

// Nombre de requêtes de suppression envoyées en même temps par annulerVus
// (même valeur que sur le site : 10).
private const val SUPPRESSIONS_SIMULTANEES = 10

// Dépôt « liste » : vos séries et vos épisodes cochés (comme listerSuivis,
// chargerFiche, suivreSerie, marquerVus, annulerVu(s), choisirStatut,
// changerStatut, changerCategorie et retirerSerie dans js/base.js). C'est ici, et
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
     * Retire du catalogue les séries que plus personne ne suit et dont la
     * fiche TMDB a plus de 5 mois (décision 42), comme nettoyerCatalogue sur
     * le site. Le travail est fait dans la base par la fonction SQL
     * « nettoyer_catalogue » : les utilisateurs ne peuvent pas supprimer de
     * séries eux-mêmes. Ne touche pas à votre liste.
     */
    suspend fun nettoyerCatalogue() {
        client.postgrest.rpc("nettoyer_catalogue")
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

    /**
     * Décoche plusieurs épisodes (« Annuler » après un statut qui en a coché),
     * comme annulerVus sur le site : une requête par suite d'épisodes qui se
     * suivent dans une saison, 10 à la fois au plus. Les autres épisodes ne
     * sont pas touchés.
     */
    suspend fun annulerVus(serieId: Long, episodes: List<Episode>) {
        try {
            for (paquet in suitesConsecutives(episodes).chunked(SUPPRESSIONS_SIMULTANEES)) {
                coroutineScope {
                    paquet.map { suite ->
                        async {
                            client.from("episodes_vus").delete {
                                filter {
                                    eq("serie_id", serieId)
                                    eq("saison", suite.saison)
                                    gte("episode", suite.debut)
                                    lte("episode", suite.fin)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            noterChangement()
        }
    }

    /**
     * Choisit un statut depuis la fiche ; null = revenir au statut
     * automatique. Passe par la fonction SQL « choisir_statut » (décision 51) :
     * pour « À jour » et « Terminé », la base coche aussi, dans la même
     * opération, les épisodes déjà sortis qui ne l'étaient pas (sauf les
     * spéciaux). Renvoie les épisodes qu'elle vient de cocher (liste vide s'il
     * n'y en a pas).
     */
    suspend fun choisirStatut(serieId: Long, statut: String?): List<Episode> {
        try {
            return client.postgrest.rpc(
                "choisir_statut",
                buildJsonObject {
                    put("id_serie", serieId)
                    // "" plutôt que null pour « automatique » : la fonction
                    // traite les deux pareil, et le paramètre est ainsi
                    // toujours envoyé (il n'a pas de valeur par défaut).
                    put("statut_choisi", statut ?: "")
                },
            ).decodeList<Episode>()
        } finally {
            noterChangement()
        }
    }

    /**
     * Enregistre un statut tel quel, sans rien cocher ; null = statut
     * automatique. Sert à remettre l'ancien statut (« Annuler »).
     */
    suspend fun changerStatut(serieId: Long, statut: String?) {
        try {
            client.from("suivis").update({ set("statut_manuel", statut) }) {
                filter { eq("serie_id", serieId) }
            }
        } finally {
            noterChangement()
        }
    }

    /** Corrige le classement d'une série : "serie" ou "anime" ; null = classement automatique. */
    suspend fun changerCategorie(serieId: Long, categorie: String?) {
        try {
            client.from("suivis").update({ set("categorie_manuelle", categorie) }) {
                filter { eq("serie_id", serieId) }
            }
        } finally {
            noterChangement()
        }
    }

    /**
     * Ajoute à votre liste une série déjà copiée dans le catalogue (voir
     * DepotTmdb.importerSerie) ; « Plus tard » si [plusTard]. Renvoie false si
     * elle y était déjà : rien n'est alors changé (pas même son statut), comme
     * suivreSerie sur le site. Écriture qui ignore les doublons ; la base ne
     * renvoie que les lignes vraiment ajoutées, d'où la réponse.
     */
    suspend fun suivreSerie(serieId: Long, plusTard: Boolean): Boolean {
        try {
            val ajoutees = client.from("suivis")
                .upsert(NouveauSuivi(serieId, if (plusTard) "plus_tard" else null)) {
                    onConflict = "utilisateur_id,serie_id"
                    ignoreDuplicates = true
                    select(columns = Columns.list("serie_id"))
                }
                .decodeList<SuiviAjoute>()
            return ajoutees.isNotEmpty()
        } finally {
            noterChangement()
        }
    }

    /** Retire une série de votre liste ; ses épisodes cochés sont effacés avec elle (décision 16). */
    suspend fun retirerSerie(serieId: Long) {
        try {
            client.from("suivis").delete {
                filter { eq("serie_id", serieId) }
            }
        } finally {
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
