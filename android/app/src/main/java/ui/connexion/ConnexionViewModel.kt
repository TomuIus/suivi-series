package io.github.tomuius.canapilou.ui.connexion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.messageErreur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ViewModel de l'écran de connexion : les champs saisis, l'envoi en cours et
// le message d'erreur. Une fois connecté, l'appli passe d'elle-même à
// « Ma liste » (elle suit l'état de la connexion, voir AppCanapilou.kt).
class ConnexionViewModel(private val depotCompte: DepotCompte) : ViewModel() {

    var email by mutableStateOf("")
    var motDePasse by mutableStateOf("")
    var enCours by mutableStateOf(false)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    val peutEnvoyer: Boolean
        get() = !enCours && email.isNotBlank() && motDePasse.isNotEmpty()

    fun seConnecter() {
        if (!peutEnvoyer) return
        enCours = true
        erreur = null
        viewModelScope.launch {
            try {
                depotCompte.seConnecter(email.trim(), motDePasse)
                motDePasse = "" // on n'en garde pas trace une fois connecté
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erreur = messageErreur(e)
            } finally {
                enCours = false
            }
        }
    }
}
