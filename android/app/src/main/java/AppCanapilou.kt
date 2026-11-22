package io.github.tomuius.canapilou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.tomuius.canapilou.donnees.EtatConnexion
import io.github.tomuius.canapilou.ui.apropos.EcranAPropos
import io.github.tomuius.canapilou.ui.connexion.EcranConnexion
import io.github.tomuius.canapilou.ui.liste.EcranListe
import kotlinx.serialization.Serializable

// Les « clés » des écrans pour Navigation 3 (décision 59) : chaque écran de
// l'historique est désigné par une clé. Elles sont @Serializable pour que
// l'historique soit sauvegardé quand Android recrée l'appli. D'autres
// viendront (fiche d'une série, recherche…).
@Serializable
data object CleMaListe : NavKey

@Serializable
data object CleAPropos : NavKey

// Racine de l'appli : selon l'état de la connexion, on affiche une attente
// (lecture de la session enregistrée), l'écran de connexion, ou l'appli.
// L'écran de connexion est hors de l'historique : le bouton retour depuis
// « Ma liste » ne ramène donc pas à la connexion.
@Composable
fun AppCanapilou() {
    val etat by Conteneur.depotCompte.etat.collectAsStateWithLifecycle(initialValue = EtatConnexion.CHARGEMENT)

    when (etat) {
        EtatConnexion.CHARGEMENT -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        EtatConnexion.DECONNECTE -> EcranConnexion()
        EtatConnexion.CONNECTE -> NavigationPrincipale()
    }
}

// Les écrans de l'appli une fois connecté, avec leur historique (retour).
@Composable
private fun NavigationPrincipale() {
    val historique = rememberNavBackStack(CleMaListe)

    NavDisplay(
        backStack = historique,
        // Retour : on enlève le dernier écran (au premier écran, Android ferme l'appli).
        onBack = { if (historique.size > 1) historique.removeLastOrNull() },
        entryDecorators = listOf(
            // Garde l'état de chaque écran (position dans la liste…) dans l'historique.
            rememberSaveableStateHolderNavEntryDecorator(),
            // Donne à chaque écran son propre ViewModel.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { cle ->
            when (cle) {
                is CleMaListe -> NavEntry(cle) {
                    EcranListe(
                        // Pas deux fois « À propos » dans l'historique si l'on appuie vite deux fois.
                        onAPropos = { if (historique.lastOrNull() != CleAPropos) historique.add(CleAPropos) },
                    )
                }
                is CleAPropos -> NavEntry(cle) {
                    EcranAPropos(onRetour = { if (historique.size > 1) historique.removeLastOrNull() })
                }
                else -> NavEntry(cle) { Text("Écran inconnu") }
            }
        },
    )
}
