package io.github.tomuius.canapilou.ui.profil

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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.ui.commun.BarreSimple
import io.github.tomuius.canapilou.ui.theme.LocalCouleurSucces

// Page « Tous les badges » (décisions 48 et 84), comme la page « badges » du
// site : « N badges obtenus sur 18 » et sa barre (verte à 100 %), puis une
// tuile par famille à paliers, puis les défis, chacun avec sa condition et
// son avancement. Tout vient de la base (vue « ma_progression »), relue à
// l'ouverture de la page : décocher un épisode peut faire perdre un badge.

@Composable
fun EcranBadges(
    onRetour: () -> Unit,
    // Même ViewModel que le Profil (nom et progression), une instance propre
    // à cette page : la progression est relue à son ouverture.
    vm: ProfilViewModel = viewModel { ProfilViewModel(Conteneur.depotListe, Conteneur.depotCompte) },
) {
    val etat by vm.progression.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarreRetourProfil(onRetour) }) { marges ->
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
                    Text("Badges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    when (val e = etat) {
                        EtatProgression.Chargement -> TexteInfo("Chargement…")
                        EtatProgression.Indisponible -> TexteInfo("Badges indisponibles pour le moment.")
                        is EtatProgression.Prete -> TousLesBadges(e.progression)
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
private fun TexteInfo(texte: String) {
    Text(texte, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Compteur et barre, puis « Badges à paliers » et « Défis » : 2 tuiles de
// front sur téléphone, 4 à partir de 640 dp (40 rem sur le site).
@Composable
private fun TousLesBadges(p: Progression) {
    val compte = compteBadges(p.badges)
    val page = pageBadges(p.badges)
    Text(
        texteCompteBadges(compte),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val pourcentage = compte.pourcentage
    BarreSimple(
        pourcentage,
        10.dp,
        Modifier.semantics {
            contentDescription = "Badges obtenus"
            progressBarRangeInfo = ProgressBarRangeInfo(pourcentage / 100f, 0f..1f)
        },
        couleur = if (pourcentage == 100) LocalCouleurSucces.current else MaterialTheme.colorScheme.primary,
    )
    BoxWithConstraints {
        val colonnes = if (maxWidth >= 640.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TitreSection("Badges à paliers")
            GrilleTuiles(page.familles, colonnes) { f, m -> TuileFamille(f, m) }
            TitreSection("Défis")
            GrilleTuiles(page.defis, colonnes) { b, m -> TuileDefi(b, m) }
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}
