package io.github.tomuius.canapilou.ui.parametres

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotReglages
import io.github.tomuius.canapilou.donnees.DepotSauvegarde
import io.github.tomuius.canapilou.donnees.compterSauvegarde
import io.github.tomuius.canapilou.donnees.contenuSauvegarde
import io.github.tomuius.canapilou.donnees.dateExport
import io.github.tomuius.canapilou.donnees.messageErreur
import io.github.tomuius.canapilou.donnees.messageExport
import io.github.tomuius.canapilou.donnees.nomFichierSauvegarde
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
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

// ViewModel de la page Paramètres (décision 90) : nom d'utilisateur et thème
// (partie A), export de la sauvegarde (partie B), comme la page
// « parametres » du site. À venir : l'import (partie C).
class ParametresViewModel(
    private val depotCompte: DepotCompte,
    private val depotReglages: DepotReglages,
    private val depotSauvegarde: DepotSauvegarde,
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
