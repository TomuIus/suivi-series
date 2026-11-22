package io.github.tomuius.canapilou.ui.liste

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.donnees.urlAffiche
import io.github.tomuius.canapilou.ui.theme.LocalCouleurSucces
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

// Écran « Ma liste » : vos séries, rangées en sections par statut, comme sur
// le site. Étape 2 (plan sous la décision 60) : affiches, titre du prochain
// épisode, bouton « Vu » avec « Annuler », menu (Actualiser, À propos, Se
// déconnecter). L'ouverture d'une fiche viendra ensuite.

// Durée d'affichage des messages, comme sur le site (DUREE_MESSAGE et
// DUREE_ANNULATION de js/app.js).
private const val DUREE_MESSAGE = 5_000L
private const val DUREE_ANNULATION = 8_000L

@Composable
fun EcranListe(
    onAPropos: () -> Unit,
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
            val base = if (m.annulation != null) DUREE_ANNULATION else DUREE_MESSAGE
            // Si l'utilisateur a demandé plus de temps pour lire ou agir
            // (réglage d'accessibilité d'Android), Android allonge la durée.
            val duree = accessibilite?.calculateRecommendedTimeoutMillis(
                base,
                containsText = true,
                containsControls = m.annulation != null,
            ) ?: base
            val resultat = withTimeoutOrNull(duree) {
                etatMessages.showSnackbar(
                    message = m.texte,
                    actionLabel = if (m.annulation != null) "Annuler" else null,
                    duration = SnackbarDuration.Indefinite, // la durée est gérée ci-dessus
                )
            }
            if (resultat == SnackbarResult.ActionPerformed && m.annulation != null) vm.annuler(m.annulation)
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
                        ListeEnSections(e.sections, titres, vusEnCours, onVu = vm::cocher)
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
// dans la section « En cours ».
@Composable
private fun CarteSerie(s: SerieSuivie, titreProchain: String?, vuEnCours: Boolean, onVu: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Affiche(s.afficheChemin)
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

// Petite affiche de la série (46 × 69 dp, comme les 46 × 69 pixels du site),
// chargée par Coil (décision 62) ; cadre en pointillé s'il n'y en a pas.
// Décorative pour les lecteurs d'écran : le titre est juste à côté.
@Composable
private fun Affiche(chemin: String?) {
    val forme = RoundedCornerShape(6.dp)
    val taille = Modifier.size(width = 46.dp, height = 69.dp)
    val url = urlAffiche(chemin)
    if (url == null) {
        val couleur = MaterialTheme.colorScheme.outline
        Box(
            taille.drawBehind {
                drawRoundRect(
                    color = couleur,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop, // remplit le cadre sans déformer (object-fit: cover)
            modifier = taille
                .clip(forme)
                .background(MaterialTheme.colorScheme.background), // fond pendant le chargement
        )
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

// Barre de progression et pourcentage, verte à 100 % (comme sur le site).
@Composable
private fun BarreProgression(pourcentage: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { pourcentage / 100f },
            modifier = Modifier.weight(1f),
            color = if (pourcentage == 100) LocalCouleurSucces.current else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        // Espace insécable (\u00A0) avant « % », comme en français sur le site.
        Text("$pourcentage\u00A0%", style = MaterialTheme.typography.labelMedium)
    }
}

// Petite étiquette, par exemple « Animé ».
@Composable
private fun Etiquette(texte: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            texte,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
