package io.github.tomuius.canapilou.ui.liste

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.DepotTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.donnees.messageErreur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Ce que l'écran « Ma liste » doit afficher. */
sealed interface EtatListe {
    data object Chargement : EtatListe
    data class Erreur(val message: String) : EtatListe
    data class Pret(val sections: Map<Section, List<SerieSuivie>>, val total: Int) : EtatListe
}

/** Épisode qu'on vient de cocher avec « Vu », pour pouvoir le décocher (« Annuler »). */
data class Annulation(val serieId: Long, val titre: String, val saison: Int, val episode: Int)

/** Message affiché un moment en bas de l'écran, avec « Annuler » si [annulation] est fourni. */
data class Message(val texte: String, val annulation: Annulation? = null)

// Nombre de saisons demandées à TMDB en même temps pour les titres (même
// valeur que TITRES_SIMULTANES sur le site, choisie par Claude, à revoir).
private const val TITRES_SIMULTANES = 4

// ViewModel de « Ma liste » (décision 58) : il charge les séries par les
// dépôts, coche les épisodes, et garde le tout même quand Android recrée
// l'écran (rotation…).
class ListeViewModel(
    private val depotListe: DepotListe,
    private val depotCompte: DepotCompte,
    private val depotTmdb: DepotTmdb,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatListe>(EtatListe.Chargement)
    val etat: StateFlow<EtatListe> = _etat.asStateFlow()

    // Titres des prochains épisodes, par saison (décision 38). Ils arrivent un
    // instant après la liste ; l'écran les affiche dès qu'ils sont là.
    private val _titres = MutableStateFlow<Map<CleSaison, Map<Int, String?>>>(emptyMap())
    val titres: StateFlow<Map<CleSaison, Map<Int, String?>>> = _titres.asStateFlow()

    // Séries dont le « Vu » est en cours d'envoi (bouton désactivé, « … »).
    private val _vusEnCours = MutableStateFlow<Set<Long>>(emptySet())
    val vusEnCours: StateFlow<Set<Long>> = _vusEnCours.asStateFlow()

    // Messages à afficher une seule fois (« … vu. Annuler »). Un « Channel »
    // plutôt qu'un état : un message ne doit pas réapparaître si Android
    // recrée l'écran.
    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    // Saisons dont les titres sont en cours de demande (pour ne pas les
    // demander deux fois), et limite de demandes simultanées. Utilisés
    // seulement depuis viewModelScope, qui travaille sur le fil principal.
    private val titresEnCours = mutableSetOf<CleSaison>()
    private val limiteTitres = Semaphore(TITRES_SIMULTANES)

    // Numéro de la dernière lecture de la liste : si deux lectures se croisent
    // (deux « Vu » rapprochés), seule la plus récente est affichée (comme
    // numeroChargement sur le site).
    private var numeroLecture = 0

    // Version des données (voir DepotListe.version) au moment de la dernière
    // lecture affichée : si elle a changé depuis (épisodes cochés dans une
    // fiche), la liste affichée n'est plus à jour.
    private var versionLue = -1L

    init {
        charger()
    }

    /**
     * (Re)charge la liste depuis la base, avec l'indicateur d'attente.
     * [oublierTitres] : redemander aussi les titres des épisodes (« Actualiser »).
     */
    fun charger(oublierTitres: Boolean = false) {
        if (oublierTitres) {
            depotTmdb.oublierTitres()
            _titres.value = emptyMap()
        }
        _etat.value = EtatListe.Chargement
        viewModelScope.launch {
            try {
                relire()
            } catch (e: CancellationException) {
                throw e // écran fermé pendant le chargement : rien à afficher
            } catch (e: Exception) {
                // Une autre lecture a pu réussir entre-temps : on ne remplace
                // pas une liste affichée par l'erreur.
                if (_etat.value is EtatListe.Chargement) _etat.value = EtatListe.Erreur(messageErreur(e))
            }
        }
    }

    /**
     * Bouton « Vu » : coche le prochain épisode de la série, puis relit la
     * liste (la base recalcule le prochain épisode et le statut), et propose
     * « Annuler ». Comme cocher sur le site.
     */
    fun cocher(s: SerieSuivie) {
        val saison = s.prochaineSaison ?: return
        val episode = s.prochainEpisode ?: return
        if (s.serieId in _vusEnCours.value) return // évite un double appui
        _vusEnCours.update { it + s.serieId }
        viewModelScope.launch {
            try {
                depotListe.marquerVu(s.serieId, saison, episode)
                val erreurLecture = relireSansAttente()
                _messages.send(
                    Message(
                        avecErreurLecture("« ${s.titre} » : ${libelleEpisode(saison, episode)} vu.", erreurLecture),
                        Annulation(s.serieId, s.titre, saison, episode),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(Message(messageErreur(e)))
            } finally {
                _vusEnCours.update { it - s.serieId }
            }
        }
    }

    /** « Annuler » : décoche l'épisode qu'on vient de cocher, puis relit la liste. */
    fun annuler(a: Annulation) {
        viewModelScope.launch {
            try {
                depotListe.annulerVu(a.serieId, a.saison, a.episode)
                val erreurLecture = relireSansAttente()
                _messages.send(
                    Message(
                        avecErreurLecture(
                            "« ${a.titre} » : ${libelleEpisode(a.saison, a.episode)} n'est plus coché.",
                            erreurLecture,
                        )
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(Message(messageErreur(e)))
            }
        }
    }

    /**
     * Appelée quand l'écran réapparaît (retour d'une fiche) : si des données
     * ont changé entre-temps, relit la liste sans l'indicateur d'attente. Rien
     * à faire si rien n'a changé (par exemple quand Android recrée l'écran
     * après une rotation).
     */
    fun relireSiPerimee() {
        if (_etat.value !is EtatListe.Pret || depotListe.version.value == versionLue) return
        viewModelScope.launch {
            relireSansAttente()?.let { erreur -> _messages.send(Message("Liste non actualisée : $erreur")) }
        }
    }

    /** Affiche un message venu d'un autre écran (par exemple « … ne fait plus partie de votre liste. »). */
    fun annoncer(texte: String) {
        viewModelScope.launch { _messages.send(Message(texte)) }
    }

    /** Déconnexion : l'appli revient d'elle-même à l'écran de connexion. */
    fun seDeconnecter() {
        viewModelScope.launch {
            try {
                depotCompte.seDeconnecter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(Message(messageErreur(e)))
            }
        }
    }

    // Lit la liste et l'affiche (si aucune lecture plus récente n'a été
    // lancée entre-temps), puis demande les titres qui manquent. Une erreur
    // remonte à l'appelant.
    private suspend fun relire() {
        val numero = ++numeroLecture
        val version = depotListe.version.value // relevée AVANT la lecture
        val series = depotListe.lireMaListe()
        if (numero != numeroLecture) return // réponse dépassée
        versionLue = version
        _etat.value = EtatListe.Pret(regrouperParSection(series), series.size)
        chargerTitres(series)
    }

    // Relecture après « Vu » ou « Annuler », sans l'indicateur d'attente : la
    // liste actuelle reste affichée. Renvoie le message d'erreur, ou null.
    private suspend fun relireSansAttente(): String? =
        try {
            relire()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageErreur(e)
        }

    // L'épisode est bien coché (ou décoché), mais la liste n'a pas pu être
    // relue : on le dit dans le même message, pour ne pas faire disparaître
    // « Annuler » (choix de Claude, à revoir après des tests).
    private fun avecErreurLecture(texte: String, erreur: String?): String =
        if (erreur == null) texte else "$texte Liste non actualisée : $erreur"

    // Titres des prochains épisodes (décision 38), comme chargerTitresProchains
    // sur le site : pour chaque série de la section « En cours », on demande
    // la saison de son prochain épisode si on ne l'a pas déjà, 4 à la fois au
    // plus. En cas d'échec, rien n'est affiché et on réessaiera au prochain
    // chargement de la liste. Après « Vu », le prochain épisode est souvent
    // dans la même saison : son titre est alors déjà connu.
    private fun chargerTitres(series: List<SerieSuivie>) {
        for (s in series) {
            if (!peutCocher(s)) continue
            val cle = CleSaison(s.tmdbId, s.prochaineSaison ?: continue)
            if (cle in _titres.value || !titresEnCours.add(cle)) continue // déjà connue ou en cours
            viewModelScope.launch {
                try {
                    val episodes = limiteTitres.withPermit { depotTmdb.episodesSaison(cle.tmdbId, cle.saison) }
                    _titres.update { it + (cle to episodes.associate { e -> e.numero to e.titre }) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Canapilou", "Titres de la saison ${cle.saison} de « ${s.titre} » indisponibles : ${e.message}")
                } finally {
                    titresEnCours.remove(cle)
                }
            }
        }
    }
}
