package io.github.tomuius.canapilou.ui.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.urlImageBadge

// Affichage des badges (décisions 43, 47 et 80), comme les tuiles du site
// (classes .badge, .badge-pastille et .badge-image de css/style.css).

/** Couleurs d'une pastille : le rond et son contour. */
private data class CouleursPastille(val fond: Color, val contour: Color)

// Couleurs des paliers et des défis, reprises du site (--badge-bronze…) ;
// mêmes couleurs en clair et en sombre, comme des médailles.
private val COULEURS_PALIER = mapOf(
    "bronze" to CouleursPastille(Color(0xFFD9A06F), Color(0xFFA5652F)),
    "argent" to CouleursPastille(Color(0xFFC9CFD8), Color(0xFF7D8592)),
    "or" to CouleursPastille(Color(0xFFECC246), Color(0xFF9A7400)),
    "platine" to CouleursPastille(Color(0xFFA9DBE3), Color(0xFF3C8693)),
)
private val COULEURS_DEFI = CouleursPastille(Color(0xFFF5B8D0), Color(0xFFC2185B))

/**
 * Pastille d'un badge : un rond de 64 dp de la couleur du palier (contour de
 * 2 dp), en bas, et par-dessus l'image de Tilou (64 × 88 dp), dont les
 * oreilles dépassent du rond. L'image, chargée depuis le site (décision 80),
 * est déjà découpée au bord intérieur du rond. Décorative pour les lecteurs
 * d'écran : le nom du badge est juste dessous.
 */
@Composable
fun PastilleBadge(badge: Badge) {
    val couleurs = COULEURS_PALIER[badge.palier] ?: COULEURS_DEFI
    Box(modifier = Modifier.size(width = 64.dp, height = 88.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(64.dp)
                .background(couleurs.fond, CircleShape)
                .border(2.dp, couleurs.contour, CircleShape),
        )
        AsyncImage(
            model = urlImageBadge(badge.icone),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Tuile d'un badge obtenu : pastille, nom, palier (ou « Défi »). Lue d'un
 * seul tenant par les lecteurs d'écran (« Temps passé, Or »).
 */
@Composable
fun TuileBadge(badge: Badge, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .border(1.dp, MaterialTheme.colorScheme.outline, forme)
            .background(MaterialTheme.colorScheme.background, forme)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        PastilleBadge(badge)
        Text(
            badge.nom,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            sousTitreBadge(badge),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Tuiles en grille, [colonnes] de front (3 sur téléphone, comme le site),
 * espacées de 8 dp. Grille simple (lignes de tuiles de même largeur) : il y a
 * au plus 18 badges, inutile de faire défiler la grille à part.
 */
@Composable
fun GrilleBadges(badges: List<Badge>, colonnes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (ligne in badges.chunked(colonnes)) {
            // Toutes les tuiles d'une ligne à la hauteur de la plus haute
            // (un nom sur deux lignes), comme la grille du site.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                for (b in ligne) TuileBadge(b, Modifier.weight(1f).fillMaxHeight())
                // Dernière ligne incomplète : cases vides, pour garder la même largeur.
                repeat(colonnes - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
