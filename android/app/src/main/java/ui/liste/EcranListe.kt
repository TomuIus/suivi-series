package io.github.tomuius.canapilou.ui.liste

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.theme.LocalCouleurSucces

// Écran « Ma liste » : vos séries, rangées en sections par statut, comme sur
// le site. Première étape (plan sous la décision 60) : titre, barre de
// progression et ligne « Prochain : … » ; le bouton « Vu », les affiches et
// l'ouverture d'une fiche viendront ensuite.

@Composable
fun EcranListe(
    // Un ViewModel propre à cet écran (Navigation 3 le garde tant que l'écran
    // est dans l'historique), créé avec les dépôts du Conteneur.
    vm: ListeViewModel = viewModel { ListeViewModel(Conteneur.depotListe, Conteneur.depotCompte) },
) {
    val etat by vm.etat.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { EnTete(onActualiser = vm::charger, onDeconnexion = vm::seDeconnecter) },
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
                    Button(onClick = vm::charger) { Text("Réessayer") }
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
                        ListeEnSections(e.sections)
                    }
            }
        }
    }
}

// Haut de l'écran : le nom de l'appli et deux boutons.
@Composable
private fun EnTete(onActualiser: () -> Unit, onDeconnexion: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // sous la barre d'état du téléphone
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canapilou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onActualiser) { Text("Actualiser") }
            TextButton(onClick = onDeconnexion) { Text("Se déconnecter") }
        }
    }
}

@Composable
private fun ListeEnSections(sections: Map<Section, List<SerieSuivie>>) {
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
                items(series, key = { it.serieId }) { serie -> CarteSerie(serie) }
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

// Une série de la liste.
@Composable
private fun CarteSerie(s: SerieSuivie) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                ligneProgression(s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        // Espace insécable avant « % », comme en français sur le site.
        Text("$pourcentage %", style = MaterialTheme.typography.labelMedium)
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
