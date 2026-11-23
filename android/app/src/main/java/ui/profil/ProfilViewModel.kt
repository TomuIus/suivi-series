package io.github.tomuius.canapilou.ui.profil

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.Progression
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Où en est la lecture de la progression (points, niveau, rang, badges). */
sealed interface EtatProgression {
    data object Chargement : EtatProgression
    /** Lecture impossible : « Niveau et badges indisponibles pour le moment. » */
    data object Indisponible : EtatProgression
    data class Prete(val progression: Progression) : EtatProgression
}

// ViewModel du Profil (décisions 81 et 82), partie A : nom, progression et
// badges obtenus. Comme sur le site, la progression est relue à chaque
// ouverture du Profil (la base la recalcule à partir des épisodes cochés).
class ProfilViewModel(
    private val depotListe: DepotListe,
    depotCompte: DepotCompte,
) : ViewModel() {

    /** Nom d'utilisateur, sinon e-mail (comme nomAffiche sur le site). */
    val nom: String = depotCompte.nomAffiche()

    private val _progression = MutableStateFlow<EtatProgression>(EtatProgression.Chargement)
    val progression: StateFlow<EtatProgression> = _progression.asStateFlow()

    init {
        viewModelScope.launch {
            _progression.value = try {
                EtatProgression.Prete(depotListe.lireProgression())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Canapilou", "Progression (niveau, badges) illisible : ${e.message}")
                EtatProgression.Indisponible
            }
        }
    }
}
