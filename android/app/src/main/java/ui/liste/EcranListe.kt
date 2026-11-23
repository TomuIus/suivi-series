package io.github.tomuius.canapilou.ui.liste

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.commun.AfficheSerie
import io.github.tomuius.canapilou.ui.commun.BarreProgression
import io.github.tomuius.canapilou.ui.commun.DUREE_ANNULATION
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.Etiquette
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import kotlinx.coroutines.flow.collectLatest

// Écran « Ma liste » : vos séries, rangées en sections par statut, comme sur
// le site : affiches, titre du prochain épisode, bouton « Vu » avec
// « Annuler », menu (Actualiser, À propos, Se déconnecter) ; un appui sur une
// série ouvre sa fiche (décision 66).

@Composable
fun EcranListe(
    onOuvrirSerie: (Long) -> Unit,
    onAPropos: () -> Unit,
    // Message à afficher en arrivant (série retirée depuis sa fiche), puis oublié.
    messageEnAttente: String?,
    onMessagePris: () -> Unit,
    // Un ViewModel propre à cet écran (Navigation 3 le garde tant que l'écran
    // est dans l'historique), créé avec les dépôts du Conteneur.
    vm: ListeViewModel = viewModel {
        ListeViewModel(Conteneur.depotListe, Conteneur.depotCompte, Conteneur.depotTmdb)
    },
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val titres by vm.titres.collectAsStateWithLifecycle()
    val vusEnCours by vm.vusEnCours.collectAsStateWithLifecycle()

    // Messages en bas de l'écran (« snackbar »). « collectLatest » : un
    // nouveau message remplace aussitôt le précédent, comme sur le site.
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    LaunchedEffect(vm) {
        vm.messages.collectLatest { m ->
            val resultat = if (m.annulation != null) {
                etatMessages.afficherPendant(m.texte, DUREE_ANNULATION, accessibilite, action = "Annuler")
            } else {
                etatMessages.afficherPendant(m.texte, DUREE_MESSAGE, accessibilite)
            }
            if (resultat == SnackbarResult.ActionPerformed && m.annulation != null) vm.annuler(m.annulation)
        }
    }

    // De retour d'une fiche où l'on a coché des épisodes : la liste se relit
    // (sans l'indicateur d'attente), comme le site au retour sur « Ma liste ».
    LaunchedEffect(vm) { vm.relireSiPerimee() }
    LaunchedEffect(messageEnAttente) {
        if (messageEnAttente != null) {
            vm.annoncer(messageEnAttente)
            onMessagePris()
        }
    }

    Scaffold(
        topBar = {
            EnTete(
                onActualiser = { vm.charger(oublierTitres = true) },
                onAPropos = onAPropos,
                onDeconnexion = { vm.seDeconnecter() },
            )
        },
        snackbarHost = { SnackbarHost(etatMessages) },
    ) { marges ->
        Box(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize(),
        ) {
            when (val e = etat) {
                EtatListe.Chargement ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is EtatListe.Erreur -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(e.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { vm.charger() }) { Text("Réessayer") }
                }

                is EtatListe.Pret ->
                    if (e.total == 0) {
                        Text(
                            "Votre liste est vide pour l'instant.",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ListeEnSections(e.sections, titres, vusEnCours, onVu = vm::cocher, onOuvrir = onOuvrirSerie)
                    }
            }
        }
    }
}

// Haut de l'écran : le nom de l'appli et un menu (trois points).
@Composable
private fun EnTete(onActualiser: () -> Unit, onAPropos: () -> Unit, onDeconnexion: () -> Unit) {
    var menuOuvert by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // sous la barre d'état du téléphone
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canapilou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            // Le menu s'ouvre sous le bouton (Box : point d'ancrage du menu).
            Box {
                IconButton(onClick = { menuOuvert = true }) {
                    Icon(painterResource(R.drawable.menu), contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                    DropdownMenuItem(
                        text = { Text("Actualiser") },
                        onClick = { menuOuvert = false; onActualiser() },
                    )
                    DropdownMenuItem(
                        text = { Text("À propos") },
                        onClick = { menuOuvert = false; onAPropos() },
                    )
                    DropdownMenuItem(
                        text = { Text("Se déconnecter") },
                        onClick = { menuOuvert = false; onDeconnexion() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeEnSections(
    sections: Map<Section, List<SerieSuivie>>,
    titres: Map<CleSaison, Map<Int, String?>>,
    vusEnCours: Set<Long>,
    onVu: (SerieSuivie) -> Unit,
    onOuvrir: (Long) -> Unit,
) {
    // Section dépliée ou repliée : mémorisé même si Android recrée l'écran.
    // « key » donne à chaque section sa propre case de mémoire.
    val ouvertes: Map<Section, MutableState<Boolean>> = Section.entries.associateWith { section ->
        key(section.name) { rememberSaveable { mutableStateOf(!section.replieeParDefaut) } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (section in Section.entries) {
            val series = sections[section].orEmpty()
            if (series.isEmpty()) continue // section vide : pas affichée
            val ouverte = ouvertes.getValue(section)
            item(key = "section-${section.name}") {
                TitreSection(section.titre, series.size, ouverte.value) { ouverte.value = !ouverte.value }
            }
            if (ouverte.value) {
                items(series, key = { it.serieId }) { serie ->
                    CarteSerie(
                        s = serie,
                        titreProchain = titreProchain(serie, titres),
                        vuEnCours = serie.serieId in vusEnCours,
                        onVu = { onVu(serie) },
                        onOuvrir = { onOuvrir(serie.serieId) },
                    )
                }
            }
        }
    }
}

// Titre d'une section, « Terminé (3) », avec une flèche ; un appui la replie
// ou la déplie.
@Composable
private fun TitreSection(titre: String, nombre: Int, ouverte: Boolean, onBasculer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBasculer)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$titre ($nombre)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.chevron),
            contentDescription = if (ouverte) "Replier la section" else "Déplier la section",
            modifier = Modifier.rotate(if (ouverte) 180f else 0f),
        )
    }
}

// Une série de la liste : affiche, titre, progression, et bouton « Vu »
// dans la section « En cours ». Un appui sur la carte ouvre la fiche (le
// bouton « Vu » garde son propre appui).
@Composable
private fun CarteSerie(
    s: SerieSuivie,
    titreProchain: String?,
    vuEnCours: Boolean,
    onVu: () -> Unit,
    onOuvrir: () -> Unit,
) {
    OutlinedCard(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 46 × 69 dp, comme les 46 × 69 pixels du site ; taille TMDB w154
            // pour rester nette sur un téléphone (choix de Claude, à revoir).
            AfficheSerie(s.afficheChemin, 46.dp, 69.dp, "w154")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        s.titre,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.estAnime) Etiquette("Animé")
                }
                pourcentage(s)?.let { p -> BarreProgression(p) }
                Text(
                    ligneProgression(s, titreProchain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peutCocher(s)) BoutonVu(s, vuEnCours, onVu)
        }
    }
}

// Bouton « Vu » : coche dessinée + « Vu » ; « … » pendant l'envoi. Les
// lecteurs d'écran annoncent « Marquer S1·E6 de … comme vu », comme sur le site.
@Composable
private fun BoutonVu(s: SerieSuivie, enCours: Boolean, onVu: () -> Unit) {
    val saison = s.prochaineSaison ?: return
    val episode = s.prochainEpisode ?: return
    val description = "Marquer ${libelleEpisode(saison, episode)} de ${s.titre} comme vu"
    Button(
        onClick = onVu,
        enabled = !enCours,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        if (enCours) {
            Text("…", modifier = Modifier.clearAndSetSemantics {})
        } else {
            Icon(painterResource(R.drawable.coche), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Vu", modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
