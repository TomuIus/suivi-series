package io.github.tomuius.canapilou.ui.apropos

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.tomuius.canapilou.R

// Écran « À propos » (décision 63) : version de l'appli, mention obligatoire
// de TMDB et licence. La FAQ de TMDB demande que la mention soit dans une
// section « À propos » ou « Crédits » de l'appli, avec un logo TMDB moins
// visible que celui de l'appli. Ouvert depuis le bas de la page Paramètres
// (décision 103) ; liens vers le site et vers le code source retirés à la
// demande de l'utilisateur (24/11/2026), à remettre si l'appli était un jour
// proposée au public.

private const val ADRESSE_TMDB = "https://www.themoviedb.org/"

// Phrase exacte demandée par la FAQ de TMDB (à ne pas modifier), puis sa
// traduction (adaptée de celle du pied de page du site).
private const val MENTION_TMDB = "This product uses the TMDB API but is not endorsed or certified by TMDB."
private const val MENTION_TMDB_FR = "Cette application utilise l'API de TMDB, sans être approuvée ni certifiée par TMDB."

// Proportions du logo TMDB (image de 493 × 64 pixels tirée du logo du site).
private const val PROPORTIONS_LOGO_TMDB = 493f / 64f

@Composable
fun EcranAPropos(onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val version = remember { versionDeLAppli(contexte) }
    val liens = LocalUriHandler.current // ouvre une adresse dans le navigateur du téléphone

    Scaffold(topBar = { EnTeteRetour("À propos", onRetour) }) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Canapilou",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Version $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Suivez les séries et les animés que vous regardez, avec le même compte que sur le site.")

            HorizontalDivider()
            Text("Données des séries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Logo TMDB, petit (16 dp de haut) : moins visible que le nom de
            // l'appli au-dessus, comme le demande TMDB. Un appui ouvre le site de TMDB.
            Image(
                painter = painterResource(R.drawable.logo_tmdb),
                contentDescription = "TMDB",
                modifier = Modifier
                    .height(16.dp)
                    .aspectRatio(PROPORTIONS_LOGO_TMDB)
                    .clickable(onClickLabel = "Ouvrir le site de TMDB") { liens.openUri(ADRESSE_TMDB) },
            )
            Text(MENTION_TMDB_FR)
            // Phrase en anglais, signalée comme telle (comme lang="en" sur le
            // site) ; effet sur la lecture par TalkBack non vérifié.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(localeList = LocaleList("en"))) { append(MENTION_TMDB) }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("Licence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Logiciel libre, sous licence GNU AGPL-3.0.")
        }
    }
}

// En-tête avec un bouton de retour (le bouton retour du téléphone fait pareil).
@Composable
private fun EnTeteRetour(titre: String, onRetour: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRetour) {
                Icon(painterResource(R.drawable.retour), contentDescription = "Retour")
            }
            Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// Numéro de version de l'appli (versionName de app/build.gradle.kts), lu
// auprès d'Android. La façon de le demander a changé avec Android 13.
private fun versionDeLAppli(contexte: Context): String =
    try {
        val gestionnaire = contexte.packageManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gestionnaire.getPackageInfo(contexte.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            gestionnaire.getPackageInfo(contexte.packageName, 0)
        }
        infos.versionName ?: "inconnue"
    } catch (e: PackageManager.NameNotFoundException) {
        "inconnue"
    }
