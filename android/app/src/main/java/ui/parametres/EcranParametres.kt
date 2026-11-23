package io.github.tomuius.canapilou.ui.parametres

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.ModeImport
import io.github.tomuius.canapilou.donnees.texteApresErreur
import io.github.tomuius.canapilou.donnees.texteBilan
import io.github.tomuius.canapilou.donnees.texteIntrouvables
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.RangeeChoix
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import kotlinx.coroutines.flow.collectLatest

// Page « Paramètres » (décision 90), comme la page « parametres » du site :
// « Connecté avec … », nom d'utilisateur, thème (partie A), export de la
// sauvegarde (partie B) et import d'une sauvegarde (partie C).

// Types de fichiers proposés dans la fenêtre « Ouvrir » : selon l'endroit où
// il est rangé, un fichier .json peut être annoncé comme texte ou comme
// fichier quelconque (choix de Claude, à revoir après des tests).
private val TYPES_SAUVEGARDE = arrayOf("application/json", "text/*", "application/octet-stream")

@Composable
fun EcranParametres(
    onRetour: () -> Unit,
    vm: ParametresViewModel = viewModel {
        ParametresViewModel(
            Conteneur.depotCompte,
            Conteneur.depotReglages,
            Conteneur.depotSauvegarde,
            Conteneur.depotListe,
            Conteneur.depotTmdb,
        )
    },
) {
    val enregistrement by vm.enregistrement.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val exportEnCours by vm.exportEnCours.collectAsStateWithLifecycle()
    val etatImport by vm.etatImport.collectAsStateWithLifecycle()
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

    // Import (décisions 40, 94 et 95) : fenêtre « Ouvrir » d'Android pour
    // choisir le fichier, puis, pour un remplacement, fenêtre « Enregistrer
    // sous » pour la copie de votre liste actuelle.
    val lanceurImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> vm.lireFichierImport(contexte.contentResolver, uri) }
    val lanceurCopie = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> vm.ecrireCopie(contexte.contentResolver, uri) }
    LaunchedEffect(vm) {
        vm.demandeCopie.collect { nom -> lanceurCopie.launch(nom) }
    }

    // Pendant la copie et l'import, on reste sur la page : quitter couperait
    // l'import en route (le site ne peut que le demander : « restez sur cette page »).
    val importBloquant = etatImport is EtatImport.Copie || etatImport is EtatImport.EnCours
    BackHandler(enabled = importBloquant) { vm.retourPendantImport() }

    // Messages en bas de l'écran (nom enregistré, erreur).
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    LaunchedEffect(vm) {
        vm.messages.collectLatest { texte -> etatMessages.afficherPendant(texte, DUREE_MESSAGE, accessibilite) }
    }

    Scaffold(
        topBar = { BarreRetourProfil(onRetour, actif = !importBloquant) },
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

                    // Import d'une sauvegarde (décisions 40, 94 et 95).
                    TexteDoux(
                        "Pour restaurer une sauvegarde, choisissez un fichier exporté depuis cette page " +
                            "(dans l'appli ou sur le site). Rien ne change avant votre confirmation.",
                    )
                    val etat = etatImport
                    if (etat == null || etat is EtatImport.Fini || etat is EtatImport.Erreur || etat is EtatImport.Refuse) {
                        OutlinedButton(onClick = { lanceurImport.launch(TYPES_SAUVEGARDE) }) {
                            Text("Importer une sauvegarde")
                        }
                    }
                    if (etat != null) PanneauImport(etat, vm)
                }
            }
        }
    }
}

// Panneau de l'import, comme celui du site : une étape à la fois.
@Composable
private fun PanneauImport(etat: EtatImport, vm: ParametresViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, // lu par TalkBack quand il change (non vérifié)
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (etat) {
            is EtatImport.Lecture -> TexteDoux("Lecture de « ${etat.nomFichier} »…")

            // Résumé du fichier, puis choix (seulement si la liste n'est pas vide).
            is EtatImport.Choix -> {
                val textes = etat.preparation.textes
                Text(textes.resume, fontWeight = FontWeight.Bold)
                if (textes.ignorees.isNotEmpty()) TexteDoux(textes.ignorees)
                if (etat.preparation.actuelles.isEmpty()) {
                    Text("Votre liste est vide : tout sera importé.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.choisirModeImport(ModeImport.AJOUT) }) { Text("Importer") }
                        OutlinedButton(onClick = vm::fermerImport) { Text("Annuler") }
                    }
                } else {
                    Text(textes.listeActuelle)
                    Button(onClick = { vm.choisirModeImport(ModeImport.AJOUT) }, enabled = !textes.ajoutVide) {
                        Text("Ajouter à ma liste")
                    }
                    TexteDoux(textes.ajout)
                    BoutonDanger("Remplacer toute ma liste") { vm.choisirModeImport(ModeImport.REMPLACEMENT) }
                    TexteDoux(textes.remplacement)
                    OutlinedButton(onClick = vm::fermerImport) { Text("Annuler") }
                }
            }

            // Remplacement : confirmation de plus.
            is EtatImport.Confirmation -> {
                Text("Remplacer toute votre liste ?", fontWeight = FontWeight.Bold)
                Text(etat.preparation.textes.confirmation)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonDanger("Oui, remplacer", vm::confirmerRemplacement)
                    OutlinedButton(onClick = vm::retourChoixImport) { Text("Retour") }
                }
            }

            is EtatImport.Copie -> TexteDoux("Copie de votre liste actuelle : choisissez où l'enregistrer…")

            is EtatImport.EnCours -> TexteDoux("Import en cours, restez sur cette page : ${etat.progression}")

            is EtatImport.Fini -> {
                Text(texteBilan(etat.bilan), fontWeight = FontWeight.Bold)
                val introuvables = texteIntrouvables(etat.bilan)
                if (introuvables.isNotEmpty()) TexteDoux(introuvables)
                OutlinedButton(onClick = vm::fermerImport) { Text("Fermer") }
            }

            is EtatImport.Refuse -> {
                Text("« ${etat.nomFichier} » : ${etat.message}", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::fermerImport) { Text("Fermer") }
            }

            is EtatImport.Erreur -> {
                Text("L'import s'est arrêté : ${etat.message}", color = MaterialTheme.colorScheme.error)
                TexteDoux(texteApresErreur(etat.listeVidee))
                OutlinedButton(onClick = vm::fermerImport) { Text("Fermer") }
            }
        }
    }
}

// Bouton d'une action qui retire des données (comme « bouton-danger » sur le site).
@Composable
private fun BoutonDanger(texte: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(texte)
    }
}

// Haut de l'écran : « ← Profil », comme le bouton du site ; inactif pendant un import.
@Composable
private fun BarreRetourProfil(onRetour: () -> Unit, actif: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onRetour, enabled = actif) {
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
