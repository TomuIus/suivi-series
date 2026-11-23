package io.github.tomuius.canapilou.ui.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.urlImageBadge
import io.github.tomuius.canapilou.ui.commun.BarreSimple
import io.github.tomuius.canapilou.ui.commun.bordurePointillee

// Affichage des badges (décisions 43, 47, 48 et 80), comme les tuiles du site
// (classes .badge, .badge-pastille, .badge-image, .paliers de css/style.css) :
// tuiles du Profil, et tuiles des familles et des défis de la page « Tous les
// badges ».

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

// Image en gris, comme « filter: grayscale(1) » sur le site.
private val GRIS = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Pastille d'un badge : un rond de 64 dp de la couleur du palier [palier]
 * (rose pour un défi, [palier] vide), contour de 2 dp, en bas ; par-dessus,
 * l'image de Tilou (64 × 88 dp), dont les oreilles dépassent du rond.
 * L'image, chargée depuis le site (décision 80), est déjà découpée au bord
 * intérieur du rond. [manquant] : badge pas encore obtenu (ou famille sans
 * palier) : rond neutre, image en gris et pâlie (opacité 0,45, comme le site).
 * Décorative pour les lecteurs d'écran : le nom du badge est juste dessous.
 */
@Composable
fun PastilleBadge(icone: String, palier: String?, manquant: Boolean = false) {
    val couleurs = if (manquant) {
        CouleursPastille(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
    } else {
        COULEURS_PALIER[palier] ?: COULEURS_DEFI
    }
    Box(modifier = Modifier.size(width = 64.dp, height = 88.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(64.dp)
                .background(couleurs.fond, CircleShape)
                .border(2.dp, couleurs.contour, CircleShape),
        )
        AsyncImage(
            model = urlImageBadge(icone),
            contentDescription = null,
            alpha = if (manquant) 0.45f else 1f,
            colorFilter = if (manquant) GRIS else null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Cadre d'une tuile (bordure, fond, marges), contenu centré, lu d'un seul
 * tenant par les lecteurs d'écran.
 */
@Composable
private fun CadreTuile(modifier: Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    val forme = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .border(1.dp, MaterialTheme.colorScheme.outline, forme)
            .background(MaterialTheme.colorScheme.background, forme)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        content = contenu,
    )
}

@Composable
private fun NomBadge(nom: String, manquant: Boolean = false) {
    Text(
        nom,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        color = if (manquant) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PetitTexte(texte: String) {
    Text(
        texte,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// « ✓ Obtenu », « ✓ Tous les paliers obtenus » : coche dessinée, comme sur le site.
@Composable
private fun TexteObtenu(texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.coche),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        PetitTexte(texte)
    }
}

/** Tuile d'un badge obtenu (Profil) : pastille, nom, palier (ou « Défi »). */
@Composable
fun TuileBadge(badge: Badge, modifier: Modifier = Modifier) {
    CadreTuile(modifier) {
        PastilleBadge(badge.icone, badge.palier)
        NomBadge(badge.nom)
        PetitTexte(sousTitreBadge(badge))
    }
}

/**
 * Tuile d'une famille à paliers (page « Tous les badges ») : pastille du
 * palier le plus haut obtenu (grise sans palier), nom, palier, 4 petits ronds
 * (paliers obtenus en couleur, suivants en pointillé), puis une barre et
 * « 312 / 500 h pour Or », ou « Tous les paliers obtenus ».
 */
@Composable
fun TuileFamille(famille: FamilleBadges, modifier: Modifier = Modifier) {
    CadreTuile(modifier) {
        PastilleBadge(famille.icone, famille.palier, manquant = famille.palier == null)
        NomBadge(famille.nom, manquant = famille.palier == null)
        PetitTexte(sousTitreFamille(famille))
        // Les ronds sont un dessin ; pour les lecteurs d'écran, ils disent
        // « 2 paliers obtenus sur 4. » (texte caché du site).
        val texteRonds = textePaliersObtenus(famille)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .padding(vertical = 3.dp)
                .clearAndSetSemantics { contentDescription = texteRonds },
        ) {
            for (p in famille.paliers) RondPalier(p)
        }
        val prochain = famille.prochain
        if (prochain != null) {
            val description = "Progression vers le palier ${libellePalier(prochain.palier)}"
            BarreSimple(
                famille.pourcentage,
                6.dp,
                Modifier
                    .padding(vertical = 2.dp)
                    .widthIn(max = 112.dp)
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(famille.pourcentage / 100f, 0f..1f)
                    },
            )
            PetitTexte(famille.texte)
        } else {
            TexteObtenu(famille.texte)
        }
    }
}

// Petit rond de 14 dp : plein de la couleur du palier s'il est obtenu, en
// pointillé sinon.
@Composable
private fun RondPalier(p: PalierFamille) {
    val couleurs = COULEURS_PALIER[p.palier] ?: COULEURS_DEFI
    val taille = Modifier.size(14.dp)
    if (p.obtenu) {
        Box(
            taille
                .background(couleurs.fond, CircleShape)
                .border(1.5.dp, couleurs.contour, CircleShape),
        )
    } else {
        Box(taille.bordurePointillee(MaterialTheme.colorScheme.onSurfaceVariant, 7.dp, 1.5.dp))
    }
}

/**
 * Tuile d'un défi (page « Tous les badges ») : pastille (grise s'il n'est pas
 * obtenu), nom, condition, puis « Obtenu » ou l'avancement (« 120 / 500 épisodes »).
 */
@Composable
fun TuileDefi(badge: Badge, modifier: Modifier = Modifier) {
    CadreTuile(modifier) {
        PastilleBadge(badge.icone, null, manquant = !badge.obtenu)
        NomBadge(badge.nom, manquant = !badge.obtenu)
        PetitTexte(badge.condition)
        if (badge.obtenu) TexteObtenu(etatDefi(badge)) else PetitTexte(etatDefi(badge))
    }
}

/**
 * Tuiles en grille, [colonnes] de front, espacées de 8 dp ; dans une ligne,
 * toutes à la hauteur de la plus haute (comme la grille du site). Grille
 * simple (lignes de tuiles de même largeur) : il y a au plus 18 badges,
 * inutile de faire défiler la grille à part.
 */
@Composable
fun <T> GrilleTuiles(elements: List<T>, colonnes: Int, tuile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (ligne in elements.chunked(colonnes)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                for (e in ligne) tuile(e, Modifier.weight(1f).fillMaxHeight())
                // Dernière ligne incomplète : cases vides, pour garder la même largeur.
                repeat(colonnes - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
