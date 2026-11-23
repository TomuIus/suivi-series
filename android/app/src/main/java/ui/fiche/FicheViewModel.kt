package io.github.tomuius.canapilou.ui.fiche

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.DepotTmdb
import io.github.tomuius.canapilou.donnees.DonneesFiche
import io.github.tomuius.canapilou.donnees.Episode
import io.github.tomuius.canapilou.donnees.EpisodeTmdb
import io.github.tomuius.canapilou.donnees.Saison
import io.github.tomuius.canapilou.donnees.SerieCatalogue
import io.github.tomuius.canapilou.donnees.Suivi
import io.github.tomuius.canapilou.donnees.messageErreur
import io.github.tomuius.canapilou.ui.liste.messageAjout
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que l'écran de la fiche doit afficher. */
sealed interface EtatFiche {
    data object Chargement : EtatFiche
    data class Erreur(val message: String) : EtatFiche
    /** Série inexistante, ou plus dans votre liste. */
    data object Introuvable : EtatFiche
    data class Prete(val fiche: Fiche) : EtatFiche
}

/** Quelle fiche ouvrir : une série du catalogue (suivie ou non), ou l'aperçu d'une série TMDB. */
sealed interface CibleFiche {
    data class Catalogue(val serieId: Long) : CibleFiche
    data class Tmdb(val tmdbId: Int) : CibleFiche
}

/**
 * Une fiche prête à afficher. [suivi] vaut null quand la série n'est pas dans
 * votre liste : c'est alors un aperçu (épisodes non cochables, boutons
 * « Ajouter » et « Plus tard »).
 */
data class Fiche(
    val serie: SerieCatalogue,
    val saisons: List<Saison>, // dans l'ordre d'affichage (spéciaux en dernier)
    val suivi: Suivi?,
    val vus: Set<Episode>,     // épisodes cochés
    val saisonInitiale: Int,   // saison dépliée à l'ouverture
    val resume: String?,       // demandé à TMDB, arrive un instant après
)

/** Épisodes cochés par un statut (« Terminé », « À jour ») et statut d'avant, pour « Annuler ». */
data class AnnulationStatut(
    val serieId: Long,
    val titre: String,
    val episodes: List<Episode>,
    val ancienStatut: String?,
)

/** Série ajoutée depuis l'aperçu : son identifiant interne et le message à afficher. */
data class SerieAjoutee(val serieId: Long, val message: String)

/** Message affiché un moment en bas de l'écran, avec « Annuler » si [annulation] est fourni. */
data class MessageFiche(val texte: String, val annulation: AnnulationStatut? = null)

/** Titres et dates des épisodes d'une saison, demandés à TMDB au dépliage. */
sealed interface TitresSaison {
    data object Chargement : TitresSaison
    data object Erreur : TitresSaison
    data class Recus(val episodes: Map<Int, EpisodeTmdb>) : TitresSaison
}

// ViewModel de la fiche d'une série (décision 58), comme utiliserFiche dans
// js/fiche.js : partie A (décision 66), lire la fiche et cocher les
// épisodes ; partie B (décisions 67 et 68), statut, catégorie et retrait ;
// aperçu d'une série non suivie, avec « Ajouter » et « Plus tard »
// (décision 77).
class FicheViewModel(
    private val cible: CibleFiche,
    private val depotListe: DepotListe,
    private val depotTmdb: DepotTmdb,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatFiche>(EtatFiche.Chargement)
    val etat: StateFlow<EtatFiche> = _etat.asStateFlow()

    // Titres des épisodes, par numéro de saison.
    private val _titres = MutableStateFlow<Map<Int, TitresSaison>>(emptyMap())
    val titres: StateFlow<Map<Int, TitresSaison>> = _titres.asStateFlow()

    // Proposition « cocher aussi les épisodes précédents » : leur liste, ou null.
    private val _invite = MutableStateFlow<List<Episode>?>(null)
    val invite: StateFlow<List<Episode>?> = _invite.asStateFlow()

    // Messages à afficher une fois en bas de l'écran (erreurs, épisodes cochés
    // par un statut avec « Annuler »).
    private val _messages = Channel<MessageFiche>(Channel.BUFFERED)
    val messages: Flow<MessageFiche> = _messages.receiveAsFlow()

    // Envoi en cours d'un statut ou d'une catégorie (menu désactivé).
    private val _statutEnCours = MutableStateFlow(false)
    val statutEnCours: StateFlow<Boolean> = _statutEnCours.asStateFlow()
    private val _categorieEnCours = MutableStateFlow(false)
    val categorieEnCours: StateFlow<Boolean> = _categorieEnCours.asStateFlow()

    // Fenêtre de confirmation du retrait ouverte, et retrait en cours.
    private val _confirmationRetrait = MutableStateFlow(false)
    val confirmationRetrait: StateFlow<Boolean> = _confirmationRetrait.asStateFlow()
    private val _retraitEnCours = MutableStateFlow(false)
    val retraitEnCours: StateFlow<Boolean> = _retraitEnCours.asStateFlow()

    // Série retirée : message à afficher dans « Ma liste », où l'écran revient.
    private val _retiree = Channel<String>(Channel.BUFFERED)
    val retiree: Flow<String> = _retiree.receiveAsFlow()

    // Ajout depuis l'aperçu en cours : "liste" ou "plus_tard" (bouton appuyé), sinon null.
    private val _ajoutEnCours = MutableStateFlow<String?>(null)
    val ajoutEnCours: StateFlow<String?> = _ajoutEnCours.asStateFlow()

    // Aperçu d'une série TMDB ajouté : l'écran est alors remplacé par la vraie fiche.
    private val _ajoutee = Channel<SerieAjoutee>(Channel.BUFFERED)
    val ajoutee: Flow<SerieAjoutee> = _ajoutee.receiveAsFlow()

    // Épisodes dont l'envoi est en cours : un nouvel appui est ignoré, pour
    // que « coché » puis « décoché » ne partent pas en même temps (ils
    // pourraient arriver dans le désordre). Choix de Claude, à revoir.
    private val basculesEnCours = mutableSetOf<Episode>()

    // Numéro de la dernière relecture des compteurs (seule la plus récente compte).
    private var numeroSuivi = 0

    init {
        ouvrir()
    }

    /** Charge la fiche (à l'ouverture, et bouton « Réessayer »). */
    fun ouvrir() {
        _etat.value = EtatFiche.Chargement
        viewModelScope.launch {
            try {
                when (cible) {
                    // Série du catalogue, suivie ou non : lue dans la base,
                    // puis complétée avec TMDB en arrière-plan.
                    is CibleFiche.Catalogue -> {
                        val fiche = preparer(depotListe.chargerFiche(cible.serieId))
                        if (fiche == null) {
                            _etat.value = EtatFiche.Introuvable
                            return@launch
                        }
                        _etat.value = EtatFiche.Prete(fiche)
                        chargerTitres(fiche.saisonInitiale)
                        completerDepuisTmdb(fiche.serie)
                    }
                    // Aperçu d'une série TMDB : lu directement chez TMDB (action
                    // « fiche »), rien n'est enregistré (décision 21).
                    is CibleFiche.Tmdb -> {
                        val apercu = depotTmdb.apercu(cible.tmdbId)
                        val fiche = preparer(DonneesFiche(apercu.serie, apercu.saisons, null, emptyList()))!!
                            .copy(resume = apercu.resume)
                        _etat.value = EtatFiche.Prete(fiche)
                        chargerTitres(fiche.saisonInitiale)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.value = EtatFiche.Erreur(messageErreur(e))
            }
        }
    }

    // Met les données reçues en forme ; null si la série n'existe pas. Sans
    // suivi (série pas dans votre liste) : aperçu.
    private fun preparer(d: DonneesFiche): Fiche? {
        val serie = d.serie ?: return null
        return Fiche(
            serie = serie,
            saisons = ordonnerSaisons(d.saisons),
            suivi = d.suivi,
            vus = d.vus.toSet(),
            saisonInitiale = saisonInitiale(d.saisons, d.suivi),
            resume = null,
        )
    }

    private fun ficheActuelle(): Fiche? = (_etat.value as? EtatFiche.Prete)?.fiche

    // Modifie la fiche affichée (si elle l'est toujours).
    private fun modifierFiche(transformation: (Fiche) -> Fiche) {
        _etat.update { if (it is EtatFiche.Prete) EtatFiche.Prete(transformation(it.fiche)) else it }
    }

    // Complète la fiche avec TMDB, en arrière-plan (elle s'affiche tout de
    // suite), comme completerDepuisTmdb sur le site : si elle date de plus de
    // 24 h ou n'a pas de classement série / animé, on la remet à jour dans le
    // catalogue (ce qui donne aussi le résumé) ; sinon on demande seulement le
    // résumé, sans rien enregistrer. Un échec n'empêche rien.
    private fun completerDepuisTmdb(serie: SerieCatalogue) {
        viewModelScope.launch {
            try {
                if (!ficheAncienne(serie, Instant.now())) {
                    val resume = depotTmdb.resume(serie.tmdbId)
                    modifierFiche { it.copy(resume = resume) }
                    return@launch
                }
                val id = serie.id ?: return@launch // série du catalogue : toujours un identifiant
                val importee = depotTmdb.importerSerie(serie.tmdbId)
                depotListe.noterChangement() // nouveaux épisodes possibles : « Ma liste » se relira
                val d = depotListe.chargerFiche(id)
                val nouvelleSerie = d.serie ?: return@launch
                // On garde la saison ouverte et les cases cochées entre-temps.
                modifierFiche {
                    it.copy(
                        serie = nouvelleSerie,
                        saisons = ordonnerSaisons(d.saisons),
                        suivi = d.suivi,
                        resume = importee.resume,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Canapilou", "Complément de la fiche depuis TMDB impossible : ${e.message}")
            }
        }
    }

    /**
     * Titres et dates des épisodes d'une saison (à l'ouverture et au
     * dépliage d'une saison), sauf s'ils sont déjà là ou en cours. Après un
     * échec, un nouveau dépliage réessaie.
     */
    fun chargerTitres(saison: Int) {
        val tmdbId = ficheActuelle()?.serie?.tmdbId ?: return
        val connus = _titres.value[saison]
        if (connus != null && connus !is TitresSaison.Erreur) return
        _titres.update { it + (saison to TitresSaison.Chargement) }
        viewModelScope.launch {
            try {
                val episodes = depotTmdb.episodesSaison(tmdbId, saison)
                _titres.update { it + (saison to TitresSaison.Recus(episodes.associateBy { e -> e.numero })) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _titres.update { it + (saison to TitresSaison.Erreur) }
                Log.w("Canapilou", "Titres de la saison $saison indisponibles : ${e.message}")
            }
        }
    }

    /**
     * Appui sur un épisode : on le coche ou on le décoche. L'affichage change
     * tout de suite ; si la base refuse, on revient en arrière avec un message.
     */
    fun basculer(saison: Int, episode: Int) {
        val fiche = ficheActuelle() ?: return
        val serieId = fiche.suivi?.serieId ?: return // aperçu : rien à cocher
        val ep = Episode(saison, episode)
        if (ep in basculesEnCours) return
        val etat = etatEpisode(fiche.serie, fiche.vus, saison, episode)
        if (etat == EtatEpisode.A_VENIR) return // pas encore sorti : pas cochable
        basculesEnCours += ep
        if (etat == EtatEpisode.VU) {
            modifierFiche { it.copy(vus = it.vus - ep) }
            _invite.value = null
            viewModelScope.launch {
                try {
                    depotListe.annulerVu(serieId, saison, episode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    modifierFiche { it.copy(vus = it.vus + ep) }
                    _messages.send(MessageFiche(messageErreur(e)))
                } finally {
                    basculesEnCours -= ep
                }
                actualiserSuivi()
            }
        } else {
            modifierFiche { it.copy(vus = it.vus + ep) }
            viewModelScope.launch {
                try {
                    depotListe.marquerVus(serieId, listOf(ep))
                    proposerPrecedents(saison, episode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    modifierFiche { it.copy(vus = it.vus - ep) }
                    _messages.send(MessageFiche(messageErreur(e)))
                } finally {
                    basculesEnCours -= ep
                }
                actualiserSuivi()
            }
        }
    }

    // Après avoir coché un épisode : s'il reste des épisodes sortis avant lui
    // qui ne sont pas cochés, on propose de les cocher aussi.
    private fun proposerPrecedents(saison: Int, episode: Int) {
        val fiche = ficheActuelle() ?: return
        _invite.value = episodesPrecedentsNonVus(fiche.serie, fiche.saisons, fiche.vus, saison, episode)
            .ifEmpty { null }
    }

    /** « Oui, les cocher » : coche d'un coup les épisodes proposés. */
    fun cocherPrecedents() {
        val serieId = ficheActuelle()?.suivi?.serieId ?: return
        val episodes = _invite.value ?: return
        _invite.value = null
        modifierFiche { it.copy(vus = it.vus + episodes) }
        viewModelScope.launch {
            try {
                depotListe.marquerVus(serieId, episodes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modifierFiche { it.copy(vus = it.vus - episodes.toSet()) }
                _messages.send(MessageFiche(messageErreur(e)))
            }
            actualiserSuivi()
        }
    }

    /** « Non » : on ne coche rien de plus. */
    fun refuserInvite() {
        _invite.value = null
    }

    // --- Statut choisi à la main ---

    /**
     * Menu « Statut » : null = automatique. Le choix est confié à la base
     * (fonction SQL « choisir_statut », décision 51) : pour « Terminé » ou
     * « À jour », elle coche aussi les épisodes déjà sortis qui ne l'étaient
     * pas (sauf les spéciaux), et renvoie leur liste ; un message propose
     * alors « Annuler » pendant 8 s. Le menu montre tout de suite le nouveau
     * choix, et revient à l'ancien si la base refuse.
     */
    fun modifierStatut(valeur: String?) {
        val fiche = ficheActuelle() ?: return
        val suivi = fiche.suivi ?: return
        val serieId = suivi.serieId
        if (_statutEnCours.value) return
        val nouveau = valeur?.ifEmpty { null }
        val ancien = suivi.statutManuel
        _statutEnCours.value = true
        modifierFiche { it.copy(suivi = it.suivi?.copy(statutManuel = nouveau)) }
        viewModelScope.launch {
            try {
                val coches = depotListe.choisirStatut(serieId, nouveau)
                if (coches.isNotEmpty()) {
                    // Les épisodes cochés par la base apparaissent cochés ; la
                    // proposition « cocher les précédents » n'a plus lieu d'être.
                    _invite.value = null
                    modifierFiche { it.copy(vus = it.vus + coches) }
                    _messages.send(
                        MessageFiche(
                            texteEpisodesCoches(fiche.serie.titre, coches.size),
                            AnnulationStatut(serieId, fiche.serie.titre, coches, ancien),
                        )
                    )
                }
                actualiserSuivi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modifierFiche { it.copy(suivi = it.suivi?.copy(statutManuel = ancien)) }
                _messages.send(MessageFiche(messageErreur(e)))
            } finally {
                _statutEnCours.value = false
            }
        }
    }

    /**
     * « Annuler » : décoche les épisodes que le statut venait de cocher (et
     * seulement eux), puis remet le statut d'avant par une simple mise à jour
     * (pas par « choisir_statut », qui recocherait des épisodes si c'était
     * « À jour » ou « Terminé »). Comme annulerToutCocher sur le site.
     */
    fun annulerStatut(a: AnnulationStatut) {
        viewModelScope.launch {
            try {
                depotListe.annulerVus(a.serieId, a.episodes)
                depotListe.changerStatut(a.serieId, a.ancienStatut)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(MessageFiche(messageErreur(e)))
                actualiserSuivi()
                return@launch
            }
            modifierFiche { it.copy(vus = it.vus - a.episodes.toSet()) }
            actualiserSuivi()
            _messages.send(MessageFiche(texteRemisCommeAvant(a.titre)))
        }
    }

    // --- Catégorie corrigée à la main (série ou animé) ---

    /** Menu « Catégorie » : "serie", "anime", ou null = classement automatique. */
    fun modifierCategorie(valeur: String?) {
        val suivi = ficheActuelle()?.suivi ?: return
        val serieId = suivi.serieId
        if (_categorieEnCours.value) return
        val nouvelle = valeur?.ifEmpty { null }
        val ancienne = suivi.categorieManuelle
        _categorieEnCours.value = true
        modifierFiche { it.copy(suivi = it.suivi?.copy(categorieManuelle = nouvelle)) }
        viewModelScope.launch {
            try {
                depotListe.changerCategorie(serieId, nouvelle)
                actualiserSuivi() // la vue recalcule « Animé » ou non
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modifierFiche { it.copy(suivi = it.suivi?.copy(categorieManuelle = ancienne)) }
                _messages.send(MessageFiche(messageErreur(e)))
            } finally {
                _categorieEnCours.value = false
            }
        }
    }

    // --- Retrait de la liste (avec confirmation, décision 16) ---

    /** « Retirer de ma liste » : ouvre la fenêtre de confirmation. */
    fun demanderRetrait() {
        _confirmationRetrait.value = true
    }

    /** « Garder » (ou fenêtre fermée) : on ne retire rien. */
    fun annulerRetrait() {
        if (!_retraitEnCours.value) _confirmationRetrait.value = false
    }

    /** « Retirer » : retire la série ; l'écran revient alors à « Ma liste », qui affiche un message. */
    fun confirmerRetrait() {
        val fiche = ficheActuelle() ?: return
        val serieId = fiche.suivi?.serieId ?: return
        if (_retraitEnCours.value) return
        _retraitEnCours.value = true
        viewModelScope.launch {
            try {
                depotListe.retirerSerie(serieId)
                _confirmationRetrait.value = false
                _retiree.send(texteRetire(fiche.serie.titre))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fenêtre fermée, pour que le message d'erreur soit visible.
                _confirmationRetrait.value = false
                _messages.send(MessageFiche(messageErreur(e)))
            } finally {
                _retraitEnCours.value = false
            }
        }
    }

    // --- Ajout à la liste depuis l'aperçu (décision 77) ---

    /**
     * « + Ajouter à ma liste » ou « Plus tard » (plusTard) dans l'aperçu,
     * comme ajouterALaListe sur le site. Série déjà dans le catalogue : on a
     * son identifiant ; sinon, on l'y copie d'abord. Ensuite, l'aperçu devient
     * la vraie fiche : rechargée ici pour une série du catalogue, ou remplacée
     * par l'écran de la fiche pour un aperçu venu de TMDB.
     */
    fun ajouterALaListe(plusTard: Boolean) {
        val fiche = ficheActuelle() ?: return
        if (fiche.suivi != null || _ajoutEnCours.value != null) return
        _ajoutEnCours.value = if (plusTard) "plus_tard" else "liste"
        viewModelScope.launch {
            try {
                val connue = fiche.serie.id
                val (id, titre) = if (connue != null) {
                    connue to fiche.serie.titre
                } else {
                    depotTmdb.importerSerie(fiche.serie.tmdbId).let { it.id to it.titre }
                }
                val nouvelle = depotListe.suivreSerie(id, plusTard)
                // Comme sur le site : « … est maintenant dans votre liste. »
                // (ou « … dans « Plus tard ». » si elle y a vraiment été mise).
                val message = messageAjout(titre, nouvelle = true, plusTard = plusTard && nouvelle)
                if (cible is CibleFiche.Catalogue) {
                    ouvrir()
                    _messages.send(MessageFiche(message))
                } else {
                    _ajoutee.send(SerieAjoutee(id, message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(MessageFiche(messageErreur(e)))
            } finally {
                _ajoutEnCours.value = null
            }
        }
    }

    /** Affiche un message venu d'un autre écran (série ajoutée depuis l'aperçu). */
    fun annoncer(texte: String) {
        viewModelScope.launch { _messages.send(MessageFiche(texte)) }
    }

    // Relit compteurs, prochain épisode et statut après une modification (la
    // vue « ma_liste » les recalcule). Un échec n'affiche rien.
    private suspend fun actualiserSuivi() {
        val serieId = ficheActuelle()?.suivi?.serieId ?: return
        val numero = ++numeroSuivi
        try {
            val suivi = depotListe.lireSuivi(serieId)
            if (numero == numeroSuivi && suivi != null) modifierFiche { it.copy(suivi = suivi) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Canapilou", "Relecture des compteurs impossible : ${e.message}")
        }
    }
}
