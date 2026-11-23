package io.github.tomuius.canapilou.ui.profil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.commun.RangeeChoix
import io.github.tomuius.canapilou.ui.liste.FiltreCategorie
import io.github.tomuius.canapilou.ui.liste.Section

// Statistiques du Profil (décisions 28, 34, 35, 36 et 85), partie C, comme la
// page « profil » du site : filtre « Tout · Séries · Animés », quatre chiffres
// clés en tuiles, puis l'anneau « Par statut » (dessiné à la main, comme le
// SVG du site) et sa légende, qui donne les mêmes nombres en texte.

// Couleurs des statuts dans l'anneau (--statut-… du site), vérifiées sur le
// site pour le daltonisme (décision 35) ; une version pour le mode sombre.
private val COULEURS_STATUT_CLAIR = mapOf(
    Section.EN_COURS to Color(0xFF2A78D6),
    Section.A_JOUR to Color(0xFFEB6834),
    Section.TERMINE to Color(0xFF1BAF7A),
    Section.ABANDONNE to Color(0xFFEDA100),
)
private val COULEURS_STATUT_SOMBRE = mapOf(
    Section.EN_COURS to Color(0xFF3987E5),
    Section.A_JOUR to Color(0xFFD95926),
    Section.TERMINE to Color(0xFF199E70),
    Section.ABANDONNE to Color(0xFFC98500),
)

// Mode sombre déduit de la couleur du fond du thème (le thème suit le téléphone).
@Composable
private fun couleursStatut(): Map<Section, Color> =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) COULEURS_STATUT_SOMBRE else COULEURS_STATUT_CLAIR

/** Bloc « Statistiques » : [series] = votre liste, [filtre] = choix « Tout · Séries · Animés ». */
@Composable
fun BlocStatistiques(series: List<SerieSuivie>, filtre: FiltreCategorie, onFiltre: (FiltreCategorie) -> Unit) {
    val st = remember(series, filtre) { statistiques(series, filtre) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RangeeChoix(FiltreCategorie.entries, filtre, { it.libelle }, onFiltre)
        BoxWithConstraints {
            GrilleTuiles(st.chiffres, colonnes = if (maxWidth >= 640.dp) 4 else 2) { c, m -> TuileChiffre(c, m) }
        }
        Text(
            "Par statut",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Repartition(st)
        if (st.plusTard > 0) TexteNote(textePlusTard(st.plusTard))
        if (st.sansDuree > 0) TexteNote(texteSansDuree(st.sansDuree))
    }
}

@Composable
private fun TexteNote(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Un chiffre clé : le nombre en grand au-dessus de son libellé. Lu « libellé :
// nombre » par les lecteurs d'écran (comme l'ordre de lecture du site).
@Composable
private fun TuileChiffre(c: Chiffre, modifier: Modifier) {
    val forme = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = "${c.libelle} : ${c.texte}" }
            .border(1.dp, MaterialTheme.colorScheme.outline, forme)
            .background(MaterialTheme.colorScheme.background, forme)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(c.texte, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(c.libelle, fontSize = 13.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Anneau à gauche, légende à droite ; la légende passe dessous si la place
// manque (moins de 280 dp : anneau 128 + écart 16 + légende 136, comme le site).
@Composable
private fun Repartition(st: Statistiques) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 280.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Anneau(st)
                Legende(st, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Anneau(st)
                Legende(st, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Anneau de 128 dp (8 rem sur le site), dessiné comme le SVG du site dans un
 * carré de 120 : cercle de rayon 44, trait de 18 ; chaque part commence en
 * haut et tourne dans le sens des aiguilles d'une montre ; aucun titre :
 * anneau gris. Au centre : le total et « titre(s) ». Décoratif pour les
 * lecteurs d'écran : la légende donne les mêmes nombres.
 */
@Composable
private fun Anneau(st: Statistiques) {
    val couleurs = couleursStatut()
    val gris = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(128.dp)
            .clearAndSetSemantics {},
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val echelle = size.minDimension / 120f // unités du dessin du site → pixels
            val rayon = (RAYON_ANNEAU * echelle).toFloat()
            val trait = Stroke(width = (EPAISSEUR_ANNEAU * echelle).toFloat())
            val coin = Offset(center.x - rayon, center.y - rayon)
            val cadre = Size(2 * rayon, 2 * rayon)
            if (st.total == 0) {
                drawCircle(color = gris, radius = rayon, center = center, style = trait)
            } else {
                for (p in st.parts) {
                    // Longueurs le long du cercle → angles en degrés ; 0° = à droite, d'où −90° pour partir du haut.
                    val depart = (-90.0 + 360.0 * p.debut / CIRCONFERENCE_ANNEAU).toFloat()
                    val balayage = (360.0 * p.tracee / CIRCONFERENCE_ANNEAU).toFloat()
                    drawArc(
                        color = couleurs[p.section] ?: gris,
                        startAngle = depart,
                        sweepAngle = balayage,
                        useCenter = false,
                        topLeft = coin,
                        size = cadre,
                        style = trait,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nombre(st.total.toLong()), fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            Text(uniteTotal(st.total), fontSize = 12.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Légende : une ligne par statut (y compris à 0), pastille de couleur, nom,
// nombre ; un trait entre les lignes.
@Composable
private fun Legende(st: Statistiques, modifier: Modifier) {
    val couleurs = couleursStatut()
    Column(modifier = modifier) {
        for (l in st.parStatut) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 5.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(11.dp)
                        .background(couleurs[l.section] ?: MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
                )
                Text(
                    l.section.titre,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(nombre(l.nombre.toLong()), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
