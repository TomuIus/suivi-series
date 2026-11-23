package io.github.tomuius.canapilou.ui.parametres

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.ActionsImport
import io.github.tomuius.canapilou.donnees.BilanImport
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.DepotReglages
import io.github.tomuius.canapilou.donnees.DepotSauvegarde
import io.github.tomuius.canapilou.donnees.DepotTmdb
import io.github.tomuius.canapilou.donnees.ImportInterrompu
import io.github.tomuius.canapilou.donnees.LigneEpisode
import io.github.tomuius.canapilou.donnees.LigneSuivi
import io.github.tomuius.canapilou.donnees.ModeImport
import io.github.tomuius.canapilou.donnees.PlanImport
import io.github.tomuius.canapilou.donnees.SauvegardeLue
import io.github.tomuius.canapilou.donnees.SauvegardeRefusee
import io.github.tomuius.canapilou.donnees.SerieActuelle
import io.github.tomuius.canapilou.donnees.TAILLE_MAX_SAUVEGARDE
import io.github.tomuius.canapilou.donnees.TextesImport
import io.github.tomuius.canapilou.donnees.compterSauvegarde
import io.github.tomuius.canapilou.donnees.contenuSauvegarde
import io.github.tomuius.canapilou.donnees.dateExport
import io.github.tomuius.canapilou.donnees.executerImport
import io.github.tomuius.canapilou.donnees.lireSauvegarde
import io.github.tomuius.canapilou.donnees.messageCopie
import io.github.tomuius.canapilou.donnees.messageErreur
import io.github.tomuius.canapilou.donnees.messageExport
import io.github.tomuius.canapilou.donnees.nomFichierAvantRemplacement
import io.github.tomuius.canapilou.donnees.nomFichierSauvegarde
import io.github.tomuius.canapilou.donnees.planifierImport
import io.github.tomuius.canapilou.donnees.seriesActuelles
import io.github.tomuius.canapilou.donnees.textesImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce qui a été préparé pour un import, une fois le fichier lu : la
 * sauvegarde, votre liste actuelle (lignes de la base, gardées pour la copie
 * avant un remplacement), les deux plans possibles et leurs textes.
 */
class PreparationImport(
    val nomFichier: String,
    val sauvegarde: SauvegardeLue,
    val suivis: List<JsonObject>,
    val vus: List<JsonObject>,
    val actuelles: List<SerieActuelle>,
    val ajout: PlanImport,
    val remplacement: PlanImport,
    val textes: TextesImport,
)

/**
 * Étapes de l'import (comme importEtat.etape sur le site) : Lecture → Choix
 * → (Confirmation → Copie pour un remplacement) → EnCours → Fini ou Erreur ;
 * Refuse si le fichier n'est pas valable.
 */
sealed interface EtatImport {
    data class Lecture(val nomFichier: String) : EtatImport
    data class Refuse(val nomFichier: String, val message: String) : EtatImport
    class Choix(val preparation: PreparationImport) : EtatImport
    class Confirmation(val preparation: PreparationImport) : EtatImport

    /** Fenêtre « Enregistrer sous » ouverte pour la copie, puis copie en cours d'écriture. */
    class Copie(val preparation: PreparationImport) : EtatImport
    data class EnCours(val progression: String) : EtatImport
    data class Fini(val bilan: BilanImport) : EtatImport
    data class Erreur(val message: String, val listeVidee: Boolean) : EtatImport
}

// ViewModel de la page Paramètres (décision 90) : nom d'utilisateur et thème
// (partie A), export de la sauvegarde (partie B) et import (partie C),
// comme la page « parametres » du site.
class ParametresViewModel(
    private val depotCompte: DepotCompte,
    private val depotReglages: DepotReglages,
    private val depotSauvegarde: DepotSauvegarde,
    private val depotListe: DepotListe,
    private val depotTmdb: DepotTmdb,
) : ViewModel() {

    /** « Connecté avec … ». */
    val email: String = depotCompte.email()

    /** Texte du champ « Nom d'utilisateur », prérempli avec le nom actuel. */
    var nomSaisi by mutableStateOf(depotCompte.nomUtilisateur().orEmpty())
        private set

    fun changerNomSaisi(texte: String) {
        nomSaisi = texte.take(LONGUEUR_MAX_NOM) // comme « maxlength » sur le site
    }

    private val _enregistrement = MutableStateFlow(false)
    val enregistrement: StateFlow<Boolean> = _enregistrement.asStateFlow()

    // Messages en bas de l'écran (réussite ou erreur).
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Thème retenu sur le téléphone ; « Automatique » le temps de la lecture. */
    val theme: StateFlow<ChoixTheme> = depotReglages.theme
        .map { ChoixTheme.depuisValeur(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChoixTheme.AUTO)

    /** Appliqué tout de suite à toute l'appli (MainActivity lit le même réglage), sans « Enregistrer ». */
    fun changerTheme(choix: ChoixTheme) {
        viewModelScope.launch { depotReglages.enregistrerTheme(choix.valeur) }
    }

    // --- Export de la sauvegarde (décision 92) ---
    // 1) « Exporter mes données » : lecture de vos données et préparation du
    //    fichier ; 2) l'écran ouvre la fenêtre « Enregistrer sous » d'Android
    //    avec le nom proposé ; 3) le fichier est écrit à l'endroit choisi.

    private val _exportEnCours = MutableStateFlow(false)
    val exportEnCours: StateFlow<Boolean> = _exportEnCours.asStateFlow()

    // Contenu prêt, en attente de l'endroit choisi.
    private var contenuExport: String? = null

    // Nom de fichier proposé : l'écran ouvre la fenêtre « Enregistrer sous ».
    private val _demandeEnregistrement = Channel<String>(Channel.BUFFERED)
    val demandeEnregistrement: Flow<String> = _demandeEnregistrement.receiveAsFlow()

    fun preparerExport() {
        if (_exportEnCours.value) return
        _exportEnCours.value = true
        viewModelScope.launch {
            try {
                val (suivis, vus) = depotSauvegarde.lireDonneesPourExport()
                contenuExport = contenuSauvegarde(suivis, vus, dateExport(Instant.now()))
                _demandeEnregistrement.send(nomFichierSauvegarde(LocalDate.now()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(messageErreur(e))
                _exportEnCours.value = false
            }
        }
    }

    /**
     * Endroit choisi dans la fenêtre « Enregistrer sous » ([uri] null : fenêtre
     * fermée sans choisir, rien n'est écrit). [resolver] : l'accès d'Android
     * aux fichiers, donné par l'écran.
     */
    fun ecrireExport(resolver: ContentResolver, uri: Uri?) {
        val contenu = contenuExport
        contenuExport = null
        if (uri == null) {
            _exportEnCours.value = false
            return
        }
        if (contenu == null) {
            // Appli fermée par Android pendant que la fenêtre était ouverte.
            _messages.trySend("L'export a été interrompu : recommencez.")
            _exportEnCours.value = false
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val flux = resolver.openOutputStream(uri) ?: throw IOException("fichier inaccessible")
                    flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
                }
                val (nbSeries, nbEpisodes) = compterSauvegarde(contenu)
                _messages.send(messageExport(nbSeries, nbEpisodes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("Le fichier n'a pas pu être enregistré (${e.message}).")
            } finally {
                _exportEnCours.value = false
            }
        }
    }

    // --- Import d'une sauvegarde (décisions 40, 94 et 95) ---
    // 1) un fichier est choisi : il est lu, vérifié, et comparé à votre liste
    //    (rien n'est encore écrit) ; 2) choix : ajouter ou remplacer ; pour un
    //    remplacement, confirmation puis copie obligatoire de votre liste par
    //    « Enregistrer sous » ; 3) l'import (executerImport).

    private val _etatImport = MutableStateFlow<EtatImport?>(null)
    val etatImport: StateFlow<EtatImport?> = _etatImport.asStateFlow()

    // Nom proposé pour la copie : l'écran ouvre la fenêtre « Enregistrer sous ».
    private val _demandeCopie = Channel<String>(Channel.BUFFERED)
    val demandeCopie: Flow<String> = _demandeCopie.receiveAsFlow()

    /** Pendant la copie et l'import, on ne quitte pas la page (l'import serait coupé en route). */
    val importBloquant: Boolean
        get() = _etatImport.value.let { it is EtatImport.Copie || it is EtatImport.EnCours }

    /** Retour demandé pendant l'import : refusé, avec un message. */
    fun retourPendantImport() {
        _messages.trySend("Import en cours : attendez la fin avant de quitter cette page.")
    }

    /** Fichier choisi dans la fenêtre d'Android ([uri] null : fenêtre fermée sans choisir). */
    fun lireFichierImport(resolver: ContentResolver, uri: Uri?) {
        if (uri == null || importBloquant) return
        viewModelScope.launch {
            val nom = withContext(Dispatchers.IO) { nomDuFichier(resolver, uri) }
            _etatImport.value = EtatImport.Lecture(nom)
            _etatImport.value = try {
                val octets = try {
                    withContext(Dispatchers.IO) { lireOctets(resolver, uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw SauvegardeRefusee("Ce fichier n'a pas pu être lu (${e.message}).")
                }
                if (octets.size > TAILLE_MAX_SAUVEGARDE) {
                    throw SauvegardeRefusee("Ce fichier est trop gros pour une sauvegarde du site (plus de 10 Mo).")
                }
                // Comme fichier.text() sur le site : lu en UTF-8, marque « BOM » de début retirée.
                val texte = String(octets, Charsets.UTF_8).removePrefix("\uFEFF")
                val zone = ZoneId.systemDefault()
                val sauvegarde = withContext(Dispatchers.Default) { lireSauvegarde(texte, zone) }
                val (suivis, vus) = depotSauvegarde.lireDonneesPourExport()
                val actuelles = seriesActuelles(suivis, vus)
                val ajout = planifierImport(sauvegarde, actuelles, ModeImport.AJOUT)
                EtatImport.Choix(
                    PreparationImport(
                        nomFichier = nom,
                        sauvegarde = sauvegarde,
                        suivis = suivis,
                        vus = vus,
                        actuelles = actuelles,
                        ajout = ajout,
                        remplacement = planifierImport(sauvegarde, actuelles, ModeImport.REMPLACEMENT),
                        textes = textesImport(sauvegarde, actuelles, ajout, zone),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SauvegardeRefusee) {
                EtatImport.Refuse(nom, e.message.orEmpty())
            } catch (e: Exception) {
                EtatImport.Refuse(nom, messageSansPrefixe(e))
            }
        }
    }

    /** « Importer » ou « Ajouter à ma liste » : part tout de suite ; « Remplacer toute ma liste » : confirmation de plus. */
    fun choisirModeImport(mode: ModeImport) {
        val etat = _etatImport.value as? EtatImport.Choix ?: return
        if (mode == ModeImport.REMPLACEMENT) {
            _etatImport.value = EtatImport.Confirmation(etat.preparation)
        } else {
            lancerImport(etat.preparation, ModeImport.AJOUT)
        }
    }

    fun retourChoixImport() {
        val etat = _etatImport.value as? EtatImport.Confirmation ?: return
        _etatImport.value = EtatImport.Choix(etat.preparation)
    }

    fun fermerImport() {
        if (!importBloquant) _etatImport.value = null
    }

    /** « Oui, remplacer » : d'abord la copie de votre liste actuelle (décision 94). */
    fun confirmerRemplacement() {
        val etat = _etatImport.value as? EtatImport.Confirmation ?: return
        _etatImport.value = EtatImport.Copie(etat.preparation)
        _demandeCopie.trySend(nomFichierAvantRemplacement(LocalDate.now()))
    }

    /**
     * Endroit choisi pour la copie ([uri] null : fenêtre fermée sans
     * enregistrer). Le remplacement ne commence qu'une fois la copie écrite ;
     * sans copie, rien n'est fait et on revient à la confirmation.
     */
    fun ecrireCopie(resolver: ContentResolver, uri: Uri?) {
        val etat = _etatImport.value as? EtatImport.Copie
        if (etat == null) {
            // Appli fermée par Android pendant que la fenêtre était ouverte.
            if (uri != null) _messages.trySend("L'import a été interrompu : recommencez. Votre liste n'a pas été remplacée.")
            return
        }
        val preparation = etat.preparation
        if (uri == null) {
            _etatImport.value = EtatImport.Confirmation(preparation)
            _messages.trySend("Copie non enregistrée : votre liste n'a pas été remplacée.")
            return
        }
        viewModelScope.launch {
            val contenu: String
            try {
                contenu = contenuSauvegarde(preparation.suivis, preparation.vus, dateExport(Instant.now()))
                withContext(Dispatchers.IO) {
                    val flux = resolver.openOutputStream(uri) ?: throw IOException("fichier inaccessible")
                    flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etatImport.value = EtatImport.Confirmation(preparation)
                _messages.send("La copie n'a pas pu être enregistrée (${e.message}) : votre liste n'a pas été remplacée.")
                return@launch
            }
            val (nbSeries, nbEpisodes) = compterSauvegarde(contenu)
            _messages.send(messageCopie(nbSeries, nbEpisodes))
            lancerImport(preparation, ModeImport.REMPLACEMENT)
        }
    }

    // Les lectures et écritures de l'import : la base (dépôts) et TMDB.
    private val actionsImport = object : ActionsImport {
        override suspend fun idsCatalogue(tmdbIds: List<Int>): Map<Int, Long> = depotSauvegarde.idsCatalogue(tmdbIds)
        override suspend fun importerSerie(tmdbId: Int): Long = depotTmdb.importerSerie(tmdbId).id
        override suspend fun viderListe() = depotSauvegarde.viderListe()
        override suspend fun suivreSeriesImportees(lignes: List<LigneSuivi>) = depotSauvegarde.suivreSeriesImportees(lignes)
        override suspend fun cocherEpisodesImportes(lignes: List<LigneEpisode>) = depotSauvegarde.cocherEpisodesImportes(lignes)
        override suspend fun changerStatut(serieId: Long, statut: String) = depotListe.changerStatut(serieId, statut)
    }

    private fun lancerImport(preparation: PreparationImport, mode: ModeImport) {
        val plan = if (mode == ModeImport.AJOUT) preparation.ajout else preparation.remplacement
        _etatImport.value = EtatImport.EnCours("")
        viewModelScope.launch {
            try {
                val bilan = executerImport(plan, mode, actionsImport, { dateExport(Instant.now()) }) { texte ->
                    _etatImport.value = EtatImport.EnCours(texte)
                }
                _etatImport.value = EtatImport.Fini(bilan)
            } catch (e: ImportInterrompu) {
                _etatImport.value = EtatImport.Erreur(messageSansPrefixe(e.cause ?: e), e.listeVidee)
            } finally {
                // « Ma liste » se relira (comme chargerListe à la fin de l'import sur le site).
                depotListe.noterChangement()
            }
        }
    }

    /** « Enregistrer » : comme enregistrerNom sur le site. */
    fun enregistrerNom() {
        if (_enregistrement.value) return
        val nom = nomSaisi.trim()
        erreurNom(nom)?.let { erreur ->
            _messages.trySend(erreur)
            return
        }
        _enregistrement.value = true
        viewModelScope.launch {
            try {
                depotCompte.changerNom(nom)
                nomSaisi = nom
                _messages.send(messageNom(nom))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(messageErreur(e))
            } finally {
                _enregistrement.value = false
            }
        }
    }
}

// Message d'erreur à placer dans une phrase (« L'import s'est arrêté : … ») :
// sans le « Erreur : » que messageErreur met devant les cas qu'il ne connaît pas.
private fun messageSansPrefixe(e: Throwable): String = messageErreur(e).removePrefix("Erreur : ")

// Nom du fichier tel qu'Android l'affiche, sinon la fin de son adresse.
private fun nomDuFichier(resolver: ContentResolver, uri: Uri): String {
    val nom = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { curseur ->
            if (curseur.moveToFirst() && !curseur.isNull(0)) curseur.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
    return nom ?: uri.lastPathSegment ?: "fichier"
}

// Contenu du fichier, lu au plus jusqu'à un peu plus de 10 Mo : au-delà, la
// lecture s'arrête (le fichier sera refusé comme trop gros), même si Android
// ne connaît pas sa taille à l'avance.
private fun lireOctets(resolver: ContentResolver, uri: Uri): ByteArray {
    val flux = resolver.openInputStream(uri) ?: throw IOException("fichier inaccessible")
    return flux.use { entree ->
        val sortie = ByteArrayOutputStream()
        val tampon = ByteArray(64 * 1024)
        while (sortie.size() <= TAILLE_MAX_SAUVEGARDE) {
            val lus = entree.read(tampon)
            if (lus < 0) break
            sortie.write(tampon, 0, lus)
        }
        sortie.toByteArray()
    }
}
