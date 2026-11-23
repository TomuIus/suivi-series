package io.github.tomuius.canapilou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.tomuius.canapilou.ui.fiche.CibleFiche
import io.github.tomuius.canapilou.ui.fiche.EcranFiche
import io.github.tomuius.canapilou.ui.liste.EcranListe
import kotlinx.serialization.Serializable

// Les « clés » des écrans pour Navigation 3 (décision 59) : chaque écran de
// l'historique est désigné par une clé. Elles sont @Serializable pour que
// l'historique soit sauvegardé quand Android recrée l'appli.
@Serializable
data object CleMaListe : NavKey

/** Fiche d'une série du catalogue (identifiant interne, comme « #/serie/42 » sur le site). */
@Serializable
data class CleFiche(val serieId: Long) : NavKey

/** Aperçu d'une série de TMDB pas encore ajoutée (comme « #/apercu/1399 » sur le site). */
@Serializable
data class CleApercu(val tmdbId: Int) : NavKey

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
    // Message à afficher dans « Ma liste » en y revenant (série retirée depuis sa fiche).
    var messagePourLaListe by rememberSaveable { mutableStateOf<String?>(null) }
    // Message à afficher sur la fiche qui remplace un aperçu (série tout juste ajoutée).
    var messagePourLaFiche by rememberSaveable { mutableStateOf<String?>(null) }

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
                        // Un appui rapide sur deux séries n'ouvre qu'une fiche ; de même,
                        // pas deux fois « À propos » dans l'historique.
                        onOuvrirSerie = { id -> if (historique.lastOrNull() == CleMaListe) historique.add(CleFiche(id)) },
                        onApercu = { tmdbId -> if (historique.lastOrNull() == CleMaListe) historique.add(CleApercu(tmdbId)) },
                        onAPropos = { if (historique.lastOrNull() == CleMaListe) historique.add(CleAPropos) },
                        messageEnAttente = messagePourLaListe,
                        onMessagePris = { messagePourLaListe = null },
                    )
                }
                is CleFiche -> NavEntry(cle) {
                    EcranFiche(
                        cible = CibleFiche.Catalogue(cle.serieId),
                        onRetour = { if (historique.size > 1) historique.removeLastOrNull() },
                        onRetiree = { message ->
                            messagePourLaListe = message
                            if (historique.size > 1) historique.removeLastOrNull()
                        },
                        messageEnAttente = messagePourLaFiche,
                        onMessagePris = { messagePourLaFiche = null },
                    )
                }
                is CleApercu -> NavEntry(cle) {
                    EcranFiche(
                        cible = CibleFiche.Tmdb(cle.tmdbId),
                        onRetour = { if (historique.size > 1) historique.removeLastOrNull() },
                        onRetiree = {},
                        // Série ajoutée : l'aperçu est remplacé par sa fiche (comme
                        // location.replace sur le site) ; le retour ramène donc aux
                        // résultats de la recherche, pas à l'aperçu.
                        onAjoutee = { ajout ->
                            if (historique.lastOrNull() == cle) {
                                messagePourLaFiche = ajout.message
                                historique[historique.lastIndex] = CleFiche(ajout.serieId)
                            }
                        },
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
