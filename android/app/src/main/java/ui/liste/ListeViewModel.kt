package io.github.tomuius.canapilou.ui.liste

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.DepotReglages
import io.github.tomuius.canapilou.donnees.DepotTmdb
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.donnees.ResultatTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.donnees.fichesAEntretenir
import io.github.tomuius.canapilou.donnees.messageErreur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Ce que l'écran « Ma liste » doit afficher. */
sealed interface EtatListe {
    data object Chargement : EtatListe
    data class Erreur(val message: String) : EtatListe
    /**
     * [series] : toute la liste, activité la plus récente d'abord ; l'écran la
     * filtre et la range en sections (décision 88), et la recherche la parcourt.
     */
    data class Pret(
        val total: Int,
        val series: List<SerieSuivie>,
    ) : EtatListe
}

/**
 * Recherche sur TMDB : [enCours] (« Recherche… »), [faite] = texte de la
 * dernière recherche aboutie (vide sinon), [resultats] = ses résultats.
 */
data class RechercheTmdb(
    val enCours: Boolean = false,
    val faite: String = "",
    val resultats: List<ResultatTmdb> = emptyList(),
)

/** Épisode qu'on vient de cocher avec « Vu », pour pouvoir le décocher (« Annuler »). */
data class Annulation(val serieId: Long, val titre: String, val saison: Int, val episode: Int)

/** Message affiché un moment en bas de l'écran, avec « Annuler » si [annulation] est fourni. */
data class Message(val texte: String, val annulation: Annulation? = null)

// Nombre de saisons demandées à TMDB en même temps pour les titres (même
// valeur que TITRES_SIMULTANES sur le site, choisie par Claude, à revoir).
private const val TITRES_SIMULTANES = 4

// Pause dans la frappe avant de lancer la recherche sur TMDB (DELAI_FRAPPE
// du site : 0,5 s, à revoir après des tests).
private const val DELAI_FRAPPE = 500L

// ViewModel de « Ma liste » (décision 58) : il charge les séries par les
// dépôts, coche les épisodes, et garde le tout même quand Android recrée
// l'écran (rotation…).
class ListeViewModel(
    private val depotListe: DepotListe,
    private val depotCompte: DepotCompte,
    private val depotTmdb: DepotTmdb,
    private val depotReglages: DepotReglages,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatListe>(EtatListe.Chargement)
    val etat: StateFlow<EtatListe> = _etat.asStateFlow()

    // Filtre « Tout · Séries · Animés » (décisions 23 et 88), lu dans la
    // mémoire du téléphone et retenu à chaque changement. « Tout » le temps
    // de la lecture (quelques millisecondes, en général avant la liste).
    val filtre: StateFlow<FiltreCategorie> = depotReglages.filtreListe
        .map { FiltreCategorie.depuisValeur(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FiltreCategorie.TOUT)

    fun choisirFiltre(f: FiltreCategorie) {
        viewModelScope.launch { depotReglages.enregistrerFiltreListe(f.valeur) }
    }

    // En-tête (décision 82) : nom (sinon e-mail) et progression (niveau et
    // sa barre), lue avec la liste. null tant qu'elle n'a pas été lue, ou si
    // la lecture échoue : la liste reste utilisable, seul le niveau manque.
    val nom: StateFlow<String> = depotCompte.nom
        .stateIn(viewModelScope, SharingStarted.Eagerly, depotCompte.nomAffiche())
    private val _progression = MutableStateFlow<Progression?>(null)
    val progression: StateFlow<Progression?> = _progression.asStateFlow()

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

    // --- Recherche (décisions 44 et 75) ---

    /**
     * Texte tapé dans la barre de recherche. Gardé comme un état de Compose
     * (et non un StateFlow) : c'est la façon recommandée pour un champ de
     * saisie, sinon le curseur peut sauter pendant la frappe.
     */
    var recherche by mutableStateOf("")
        private set

    private val _rechercheTmdb = MutableStateFlow(RechercheTmdb())
    val rechercheTmdb: StateFlow<RechercheTmdb> = _rechercheTmdb.asStateFlow()

    // Séries en cours d'ajout : identifiant TMDB → "liste" ou "plus_tard"
    // (bouton appuyé, qui affiche « Ajout… »).
    private val _ajoutsEnCours = MutableStateFlow<Map<Int, String>>(emptyMap())
    val ajoutsEnCours: StateFlow<Map<Int, String>> = _ajoutsEnCours.asStateFlow()

    private var minuterieFrappe: Job? = null
    // Numéro de la recherche en cours : une réponse arrivée après un
    // changement du texte est ignorée (numeroRecherche sur le site).
    private var numeroRecherche = 0

    // Version des données (voir DepotListe.version) au moment de la dernière
    // lecture affichée : si elle a changé depuis (épisodes cochés dans une
    // fiche), la liste affichée n'est plus à jour.
    private var versionLue = -1L

    // Entretien du catalogue (décisions 42 et 79), une fois par ouverture de
    // l'appli, comme une fois par visite sur le site : nettoyage lancé, mise
    // à jour des fiches lancée (seulement s'il y en avait à faire), et la
    // mise à jour en cours.
    private var nettoyageLance = false
    private var miseAJourLancee = false
    private var miseAJour: Job? = null

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
            // « Actualiser » vaut un rechargement de la page du site : l'entretien
            // pourra se refaire (sauf si une mise à jour est encore en cours).
            if (miseAJour?.isActive != true) {
                nettoyageLance = false
                miseAJourLancee = false
            }
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

    /**
     * Le texte de la barre a changé. Comme sur le site, on attend une pause
     * de 0,5 s dans la frappe avant de chercher sur TMDB ; « Recherche… »
     * s'affiche tout de suite. Sous 2 caractères, rien n'est cherché.
     */
    fun changerRecherche(texte: String) {
        recherche = texte.take(100) // 100 caractères au plus, comme le champ du site
        minuterieFrappe?.cancel()
        numeroRecherche++ // une réponse encore en route pour l'ancien texte sera ignorée
        val nettoye = recherche.trim()
        if (nettoye.length < LONGUEUR_MIN) {
            _rechercheTmdb.value = RechercheTmdb()
            return
        }
        _rechercheTmdb.update { it.copy(enCours = nettoye != it.faite) }
        minuterieFrappe = viewModelScope.launch {
            delay(DELAI_FRAPPE)
            lancerRecherche()
        }
    }

    /** Lance la recherche sur TMDB tout de suite (touche « Rechercher » du clavier). */
    fun lancerRecherche() {
        minuterieFrappe?.cancel()
        val texte = recherche.trim()
        if (texte.length < LONGUEUR_MIN || texte == _rechercheTmdb.value.faite) return
        val numero = ++numeroRecherche
        _rechercheTmdb.update { it.copy(enCours = true) }
        viewModelScope.launch {
            try {
                val trouves = depotTmdb.rechercher(texte)
                if (numero == numeroRecherche) _rechercheTmdb.value = RechercheTmdb(false, texte, trouves)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (numero == numeroRecherche) {
                    // Les anciens résultats ne correspondent plus au texte : on
                    // les efface (la touche « Rechercher » relance).
                    _rechercheTmdb.value = RechercheTmdb()
                    _messages.send(Message(messageErreur(e)))
                }
            }
        }
    }

    /** Croix, ou retour du téléphone : efface la recherche et ramène à la liste. */
    fun effacerRecherche() {
        changerRecherche("")
    }

    /**
     * Boutons « Ajouter » et « Plus tard » d'un résultat TMDB, comme ajouter
     * sur le site : la fiche TMDB est d'abord copiée dans le catalogue (action
     * « ajouter » de la fonction serveur), puis la série est ajoutée à votre
     * liste ; la liste est relue, puis un message confirme.
     */
    fun ajouter(r: ResultatTmdb, plusTard: Boolean) {
        if (r.tmdbId in _ajoutsEnCours.value) return // évite un double appui
        _ajoutsEnCours.update { it + (r.tmdbId to if (plusTard) "plus_tard" else "liste") }
        viewModelScope.launch {
            try {
                val serie = depotTmdb.importerSerie(r.tmdbId)
                val nouvelle = depotListe.suivreSerie(serie.id, plusTard)
                val erreurLecture = relireSansAttente()
                _messages.send(Message(avecErreurLecture(messageAjout(serie.titre, nouvelle, plusTard), erreurLecture)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(Message(messageErreur(e)))
            } finally {
                _ajoutsEnCours.update { it - r.tmdbId }
            }
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
    // lancée entre-temps), puis demande les titres qui manquent et lance
    // l'entretien du catalogue. La progression (niveau de l'en-tête) est lue
    // en même temps, comme chargerListe sur le site ; si elle échoue, la
    // liste s'affiche quand même et l'ancienne progression reste. Une erreur
    // de la liste remonte à l'appelant.
    private suspend fun relire(): Unit = coroutineScope {
        val numero = ++numeroLecture
        val version = depotListe.version.value // relevée AVANT la lecture
        val progression = async {
            try {
                depotListe.lireProgression()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Canapilou", "Progression (niveau, badges) illisible : ${e.message}")
                null
            }
        }
        val series = depotListe.lireMaListe()
        val prog = progression.await()
        if (numero != numeroLecture) return@coroutineScope // réponse dépassée
        versionLue = version
        _etat.value = EtatListe.Pret(series.size, series)
        if (prog != null) _progression.value = prog
        chargerTitres(series)
        entretenirCatalogue(series)
    }

    // Entretien du catalogue, comme completerFiches sur le site (décisions
    // 24, 31 et 42) : une fois, retirer du catalogue les séries que plus
    // personne ne suit (fiches de plus de 5 mois), sans attendre ; et une fois
    // aussi, remettre à jour depuis TMDB les fiches de la liste qui le
    // demandent, une par une, en arrière-plan, puis relire la liste. Un échec
    // n'affiche rien ; réussites et échecs sont notés dans le journal
    // d'Android (Logcat, étiquette « Canapilou »). Une fiche qui échoue sera
    // réessayée à la prochaine ouverture de l'appli.
    private fun entretenirCatalogue(series: List<SerieSuivie>) {
        if (!nettoyageLance) {
            nettoyageLance = true
            viewModelScope.launch {
                try {
                    depotListe.nettoyerCatalogue()
                    Log.i("Canapilou", "Nettoyage du catalogue fait.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Canapilou", "Nettoyage du catalogue impossible : ${e.message}")
                }
            }
        }
        val aMettreAJour = fichesAEntretenir(series)
        if (miseAJourLancee || aMettreAJour.isEmpty()) return
        miseAJourLancee = true
        miseAJour = viewModelScope.launch {
            for (s in aMettreAJour) {
                try {
                    depotTmdb.importerSerie(s.tmdbId)
                    Log.i("Canapilou", "Fiche de « ${s.titre} » remise à jour depuis TMDB.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Canapilou", "Mise à jour de « ${s.titre} » impossible : ${e.message}")
                }
            }
            relireSansAttente()?.let { erreur -> _messages.send(Message("Liste non actualisée : $erreur")) }
        }
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
