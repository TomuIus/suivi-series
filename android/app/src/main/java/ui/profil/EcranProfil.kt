package io.github.tomuius.canapilou.ui.profil

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.ui.commun.BarreSimple

// Écran « Profil » (décisions 81 et 82), partie A, comme la page « profil »
// du site : nom et Tilou au pop-corn, « Niveau N · rang », barre vers le
// niveau suivant, points, prochain rang, puis les badges obtenus (pour une
// famille, le palier le plus haut), « Voir tous les badges » (partie B,
// décision 84), puis les statistiques (partie C, décision 86).

@Composable
fun EcranProfil(
    onRetour: () -> Unit,
    onTousLesBadges: () -> Unit,
    onParametres: () -> Unit,
    // Un ViewModel propre à cet écran (Navigation 3 le garde tant qu'il est
    // dans l'historique).
    vm: ProfilViewModel = viewModel {
        ProfilViewModel(Conteneur.depotListe, Conteneur.depotCompte, avecStatistiques = true)
    },
) {
    val etat by vm.progression.collectAsStateWithLifecycle()
    val etatStats by vm.statistiques.collectAsStateWithLifecycle()
    val filtre by vm.filtre.collectAsStateWithLifecycle()
    val nom by vm.nom.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarreRetour(onRetour, onParametres) }) { marges ->
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Nom et niveau à gauche, Tilou au pop-corn à droite (décision 39).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            when (val e = etat) {
                                EtatProgression.Chargement -> TexteAide("Chargement…")
                                EtatProgression.Indisponible -> TexteAide("Niveau et badges indisponibles pour le moment.")
                                is EtatProgression.Prete -> Niveau(e.progression)
                            }
                        }
                        // 56 × 112 dp comme les 56 × 112 pixels du site. Image
                        // intégrée à l'appli, comme celles de l'en-tête, de la
                        // connexion et de la liste vide (décisions 106 et 107 ;
                        // avant : chargée depuis le site). Décorative.
                        Image(
                            painter = painterResource(R.drawable.tilou_popcorn),
                            contentDescription = null,
                            modifier = Modifier.size(width = 56.dp, height = 112.dp),
                        )
                    }
                    (etat as? EtatProgression.Prete)?.let { BadgesObtenus(it.progression, onTousLesBadges) }
                    // Statistiques : indépendantes du niveau (et de sa lecture).
                    Text(
                        "Statistiques",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    when (val s = etatStats) {
                        EtatStatistiques.Chargement -> TexteAide("Chargement…")
                        EtatStatistiques.Indisponible -> TexteAide("Statistiques indisponibles pour le moment.")
                        is EtatStatistiques.Pretes -> BlocStatistiques(s.series, filtre, vm::choisirFiltre)
                    }
                }
            }
        }
    }
}

// Haut de l'écran : « ← Ma liste » (comme sur la fiche) à gauche,
// « Paramètres » à droite (comme le site, sans le caractère « ⚙ », dont le
// dessin dépend de la police du téléphone).
@Composable
private fun BarreRetour(onRetour: () -> Unit, onParametres: () -> Unit) {
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
                Text("Ma liste")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onParametres) { Text("Paramètres") }
        }
    }
}

@Composable
private fun TexteAide(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// « Niveau 2 · Touriste du salon », barre, « 23 points · encore 7 pour le
// niveau 3 », « Prochain rang au niveau 3 » (sans son nom, décision 48), et
// une phrase sans chiffres (le barème est dans la base et peut changer).
@Composable
private fun Niveau(p: Progression) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titreNiveau(p), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        val pourcentage = pourcentageNiveau(p)
        val description = "Progression vers le niveau ${p.niveau + 1}"
        BarreSimple(
            pourcentage,
            10.dp,
            Modifier.semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(pourcentage / 100f, 0f..1f)
            },
        )
        Text(textePoints(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        texteProchainRang(p)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TexteAide("Cochez des épisodes et terminez des séries pour gagner des points.", Modifier.padding(top = 4.dp))
    }
}

// « Badges » : seulement ceux obtenus, en tuiles (3 de front sur téléphone,
// 4 à partir de 640 dp de large, comme les 40 rem du site), ou une phrase
// d'invitation s'il n'y en a aucun ; puis « Voir tous les badges ».
@Composable
private fun BadgesObtenus(p: Progression, onTousLesBadges: () -> Unit) {
    val badges = badgesObtenus(p.badges)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (badges.isEmpty()) {
            TexteAide("Pas encore de badge : cochez un premier épisode pour gagner « Premier épisode ».")
        } else {
            BoxWithConstraints {
                GrilleTuiles(badges, colonnes = if (maxWidth >= 640.dp) 4 else 3) { b, m -> TuileBadge(b, m) }
            }
        }
        OutlinedButton(onClick = onTousLesBadges, modifier = Modifier.padding(top = 4.dp)) {
            Text("Voir tous les badges")
        }
    }
}
