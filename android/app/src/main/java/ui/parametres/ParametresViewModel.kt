package io.github.tomuius.canapilou.ui.parametres

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotReglages
import io.github.tomuius.canapilou.donnees.messageErreur
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

// ViewModel de la page Paramètres (décision 90), partie A : nom d'utilisateur
// et thème, comme la page « parametres » du site. À venir : la sauvegarde
// (export, puis import).
class ParametresViewModel(
    private val depotCompte: DepotCompte,
    private val depotReglages: DepotReglages,
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
