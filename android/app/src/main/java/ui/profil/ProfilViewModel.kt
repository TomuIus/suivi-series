package io.github.tomuius.canapilou.ui.profil

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.donnees.SerieSuivie
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

/** Où en est la lecture de la liste, pour les statistiques. */
sealed interface EtatStatistiques {
    data object Chargement : EtatStatistiques
    /** Lecture impossible : « Statistiques indisponibles pour le moment. » */
    data object Indisponible : EtatStatistiques
    data class Pretes(val series: List<SerieSuivie>) : EtatStatistiques
}

// ViewModel du Profil (décisions 81 et 82) : nom, progression et badges, et,
// avec [avecStatistiques], la liste pour les statistiques (partie C). Comme
// sur le site, tout est relu à chaque ouverture du Profil (la base recalcule
// la progression à partir des épisodes cochés). Sert aussi à la page « Tous
// les badges », sans les statistiques.
class ProfilViewModel(
    private val depotListe: DepotListe,
    depotCompte: DepotCompte,
    avecStatistiques: Boolean = false,
) : ViewModel() {

    /** Nom d'utilisateur, sinon e-mail (comme nomAffiche sur le site). */
    val nom: String = depotCompte.nomAffiche()

    private val _progression = MutableStateFlow<EtatProgression>(EtatProgression.Chargement)
    val progression: StateFlow<EtatProgression> = _progression.asStateFlow()

    private val _statistiques = MutableStateFlow<EtatStatistiques>(EtatStatistiques.Chargement)
    val statistiques: StateFlow<EtatStatistiques> = _statistiques.asStateFlow()

    // Filtre « Tout · Séries · Animés » des statistiques : propre au Profil,
    // non retenu (« Tout » à chaque ouverture, comme sur le site).
    private val _filtre = MutableStateFlow(FiltreCategorie.TOUT)
    val filtre: StateFlow<FiltreCategorie> = _filtre.asStateFlow()

    fun choisirFiltre(f: FiltreCategorie) {
        _filtre.value = f
    }

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
        if (avecStatistiques) {
            viewModelScope.launch {
                _statistiques.value = try {
                    EtatStatistiques.Pretes(depotListe.lireMaListe())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Canapilou", "Liste illisible pour les statistiques : ${e.message}")
                    EtatStatistiques.Indisponible
                }
            }
        }
    }
}
