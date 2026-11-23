package io.github.tomuius.canapilou.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Le thème de l'appli : les couleurs de Canapilou placées dans les « rôles »
// de Material 3 (primary = couleur d'accent, background = fond, surface =
// cartes…). Les couleurs dynamiques d'Android 12+ (tirées du fond d'écran) ne
// sont pas utilisées : l'appli garde l'identité du site (décision 60).

private val CouleursClaires = lightColorScheme(
    primary = AccentClair,
    onPrimary = AccentTexteClair,
    primaryContainer = TeinteClair,
    onPrimaryContainer = LogoDebutClair,
    secondary = LogoDebutClair,
    onSecondary = Color.White,
    secondaryContainer = TeinteClair,
    onSecondaryContainer = LogoDebutClair,
    tertiary = AccentClair,
    onTertiary = AccentTexteClair,
    background = FondClair,
    onBackground = TexteClair,
    surface = CarteClair,
    onSurface = TexteClair,
    surfaceVariant = FondClair,
    onSurfaceVariant = TexteDouxClair,
    surfaceContainerLowest = CarteClair,
    surfaceContainerLow = CarteClair,
    surfaceContainer = CarteClair,
    surfaceContainerHigh = CarteClair,
    surfaceContainerHighest = CarteClair,
    outline = BordureClair,
    outlineVariant = BordureClair,
    error = ErreurClair,
    onError = Color.White,
)

private val CouleursSombres = darkColorScheme(
    primary = AccentSombre,
    onPrimary = AccentTexteSombre,
    primaryContainer = TeinteSombre,
    onPrimaryContainer = LogoDebutSombre,
    secondary = LogoDebutSombre,
    onSecondary = AccentTexteSombre,
    secondaryContainer = TeinteSombre,
    onSecondaryContainer = LogoDebutSombre,
    tertiary = AccentSombre,
    onTertiary = AccentTexteSombre,
    background = FondSombre,
    onBackground = TexteSombre,
    surface = CarteSombre,
    onSurface = TexteSombre,
    surfaceVariant = FondSombre,
    onSurfaceVariant = TexteDouxSombre,
    surfaceContainerLowest = CarteSombre,
    surfaceContainerLow = CarteSombre,
    surfaceContainer = CarteSombre,
    surfaceContainerHigh = CarteSombre,
    surfaceContainerHighest = CarteSombre,
    outline = BordureSombre,
    outlineVariant = BordureSombre,
    error = ErreurSombre,
    onError = AccentTexteSombre,
)

/** Vert « succès » (barre de progression à 100 %), selon le thème clair ou sombre. */
val LocalCouleurSucces = staticCompositionLocalOf { SuccesClair }

@Composable
fun CanapilouTheme(
    // Clair ou sombre : choisi dans les Paramètres (Automatique = réglage du
    // téléphone), voir MainActivity.
    sombre: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCouleurSucces provides if (sombre) SuccesSombre else SuccesClair) {
        MaterialTheme(
            colorScheme = if (sombre) CouleursSombres else CouleursClaires,
            typography = Typography,
            content = content,
        )
    }
}
