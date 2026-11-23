package io.github.tomuius.canapilou.ui.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.tomuius.canapilou.donnees.urlAffiche
import io.github.tomuius.canapilou.ui.theme.LocalCouleurSucces
import kotlinx.coroutines.withTimeoutOrNull

// Éléments d'affichage communs à plusieurs écrans (liste, fiche…).

// Durée d'affichage des messages en bas de l'écran, comme sur le site
// (DUREE_MESSAGE et DUREE_ANNULATION de js/app.js).
const val DUREE_MESSAGE = 5_000L
const val DUREE_ANNULATION = 8_000L

/**
 * Affiche un message en bas de l'écran (« snackbar ») pendant [dureeMs], avec
 * un bouton [action] facultatif (« Annuler »). Si l'utilisateur a demandé plus
 * de temps pour lire ou agir (réglage d'accessibilité d'Android), Android
 * allonge la durée. Renvoie le résultat (bouton appuyé ou non), ou null si le
 * temps est écoulé.
 */
suspend fun SnackbarHostState.afficherPendant(
    texte: String,
    dureeMs: Long,
    accessibilite: AccessibilityManager?,
    action: String? = null,
): SnackbarResult? {
    val duree = accessibilite?.calculateRecommendedTimeoutMillis(
        dureeMs,
        containsText = true,
        containsControls = action != null,
    ) ?: dureeMs
    return withTimeoutOrNull(duree) {
        showSnackbar(message = texte, actionLabel = action, duration = SnackbarDuration.Indefinite)
    }
}

/** Contour en pointillé (affiche absente, épisode pas encore sorti), comme « dashed » sur le site. */
fun Modifier.bordurePointillee(couleur: Color, rayon: Dp, epaisseur: Dp = 1.dp): Modifier =
    drawBehind {
        drawRoundRect(
            color = couleur,
            cornerRadius = CornerRadius(rayon.toPx()),
            style = Stroke(
                width = epaisseur.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            ),
        )
    }

/**
 * Affiche d'une série, chargée par Coil (décision 62) depuis les serveurs
 * d'images de TMDB, à la taille [tailleTmdb] (« w154 »…) ; cadre en pointillé
 * s'il n'y en a pas. Décorative pour les lecteurs d'écran : le titre est à côté.
 */
@Composable
fun AfficheSerie(chemin: String?, largeur: Dp, hauteur: Dp, tailleTmdb: String) {
    val forme = RoundedCornerShape(6.dp)
    val taille = Modifier.size(width = largeur, height = hauteur)
    val url = urlAffiche(chemin, tailleTmdb)
    if (url == null) {
        Box(taille.bordurePointillee(MaterialTheme.colorScheme.outline, 6.dp))
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

/** Barre de progression et pourcentage, verte à 100 % (comme sur le site). */
@Composable
fun BarreProgression(pourcentage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { pourcentage / 100f },
            modifier = Modifier.weight(1f),
            color = if (pourcentage == 100) LocalCouleurSucces.current else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        // Espace insécable ( ) avant « % », comme en français sur le site.
        Text("$pourcentage %", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Barre simple, sans pourcentage écrit (niveau, badges), comme les barres du
 * site : fond gris, partie remplie de la couleur d'accent (ou [couleur]). Elle
 * prend toute la largeur qu'on lui laisse ; pour les lecteurs d'écran, la
 * décrire avec [modifier] si elle n'est pas décorative.
 */
@Composable
fun BarreSimple(
    pourcentage: Int,
    hauteur: Dp,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(hauteur)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(pourcentage.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(couleur),
        )
    }
}

/**
 * Rangée de pastilles à choix unique (« Tout · Séries · Animés », plus tard
 * le thème), comme les boutons « filtre » du site : la choisie est pleine, de
 * la couleur d'accent ; lue « sélectionné » par les lecteurs d'écran. Passe
 * à la ligne si la place manque (« FlowRow »).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> RangeeChoix(
    choix: List<T>,
    choisi: T,
    libelle: (T) -> String,
    onChoix: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in choix) {
            FilterChip(
                selected = c == choisi,
                onClick = { onChoix(c) },
                label = { Text(libelle(c)) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** Petite étiquette, par exemple « Animé ». */
@Composable
fun Etiquette(texte: String) {
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
