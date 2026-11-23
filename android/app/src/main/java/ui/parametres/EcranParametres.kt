package io.github.tomuius.canapilou.ui.parametres

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.RangeeChoix
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import kotlinx.coroutines.flow.collectLatest

// Page « Paramètres » (décision 90), comme la page « parametres » du site :
// « Connecté avec … », nom d'utilisateur, thème (partie A), export de la
// sauvegarde (partie B). À venir : l'import (partie C).

@Composable
fun EcranParametres(
    onRetour: () -> Unit,
    vm: ParametresViewModel = viewModel {
        ParametresViewModel(Conteneur.depotCompte, Conteneur.depotReglages, Conteneur.depotSauvegarde)
    },
) {
    val enregistrement by vm.enregistrement.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val exportEnCours by vm.exportEnCours.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val contexte = LocalContext.current

    // Fenêtre « Enregistrer sous » d'Android (décision 92) : ouverte quand le
    // fichier est prêt, avec le nom proposé ; l'endroit choisi revient ici.
    val lanceurExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> vm.ecrireExport(contexte.contentResolver, uri) }
    LaunchedEffect(vm) {
        vm.demandeEnregistrement.collect { nom -> lanceurExport.launch(nom) }
    }

    // Messages en bas de l'écran (nom enregistré, erreur).
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    LaunchedEffect(vm) {
        vm.messages.collectLatest { texte -> etatMessages.afficherPendant(texte, DUREE_MESSAGE, accessibilite) }
    }

    Scaffold(
        topBar = { BarreRetourProfil(onRetour) },
        snackbarHost = { SnackbarHost(etatMessages) },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Paramètres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (vm.email.isNotEmpty()) TexteDoux("Connecté avec ${vm.email}")

                    // Nom d'utilisateur (décision 25)
                    Titre("Nom d'utilisateur")
                    OutlinedTextField(
                        value = vm.nomSaisi,
                        onValueChange = vm::changerNomSaisi,
                        label = { Text("Nom d'utilisateur") },
                        placeholder = { Text("Votre nom ou pseudo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            vm.enregistrerNom()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TexteDoux("Affiché en haut à la place de l'e-mail. Laissez vide pour réafficher l'e-mail.")
                    Button(
                        onClick = {
                            focus.clearFocus()
                            vm.enregistrerNom()
                        },
                        enabled = !enregistrement,
                    ) {
                        Text(if (enregistrement) "Enregistrement…" else "Enregistrer")
                    }

                    // Thème (décision 48) : appliqué tout de suite, retenu sur ce téléphone.
                    Titre("Thème")
                    RangeeChoix(ChoixTheme.entries, theme, { it.libelle }, vm::changerTheme)
                    TexteDoux("Automatique : suit le réglage de votre appareil. Le choix est retenu sur cet appareil.")

                    // Sauvegarde (décisions 22, 30 et 92) : même fichier que le site.
                    Titre("Sauvegarde")
                    TexteDoux("Enregistrez une copie de votre liste et de vos épisodes cochés. Pensez-y de temps en temps.")
                    OutlinedButton(onClick = vm::preparerExport, enabled = !exportEnCours) {
                        Text(if (exportEnCours) "Préparation…" else "Exporter mes données")
                    }
                }
            }
        }
    }
}

// Haut de l'écran : « ← Profil », comme le bouton du site.
@Composable
private fun BarreRetourProfil(onRetour: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onRetour) {
                Icon(painterResource(R.drawable.retour), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Profil")
            }
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun TexteDoux(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
