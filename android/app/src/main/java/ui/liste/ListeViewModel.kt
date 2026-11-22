package io.github.tomuius.canapilou.ui.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.donnees.messageErreur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ce que l'écran « Ma liste » doit afficher. */
sealed interface EtatListe {
    data object Chargement : EtatListe
    data class Erreur(val message: String) : EtatListe
    data class Pret(val sections: Map<Section, List<SerieSuivie>>, val total: Int) : EtatListe
}

// ViewModel de « Ma liste » (décision 58) : il charge les séries par le dépôt
// et garde le résultat, même quand Android recrée l'écran (rotation…).
class ListeViewModel(
    private val depotListe: DepotListe,
    private val depotCompte: DepotCompte,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatListe>(EtatListe.Chargement)
    val etat: StateFlow<EtatListe> = _etat.asStateFlow()

    init {
        charger()
    }

    /** (Re)charge la liste depuis la base. */
    fun charger() {
        _etat.value = EtatListe.Chargement
        viewModelScope.launch {
            _etat.value = try {
                val series = depotListe.lireMaListe()
                EtatListe.Pret(regrouperParSection(series), series.size)
            } catch (e: CancellationException) {
                throw e // écran fermé pendant le chargement : rien à afficher
            } catch (e: Exception) {
                EtatListe.Erreur(messageErreur(e))
            }
        }
    }

    /** Déconnexion : l'appli revient d'elle-même à l'écran de connexion. */
    fun seDeconnecter() {
        viewModelScope.launch {
            try {
                depotCompte.seDeconnecter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.value = EtatListe.Erreur(messageErreur(e))
            }
        }
    }
}
