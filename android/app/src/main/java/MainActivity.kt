package io.github.tomuius.canapilou

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomuius.canapilou.ui.parametres.ChoixTheme
import io.github.tomuius.canapilou.ui.parametres.themeSombre
import io.github.tomuius.canapilou.ui.theme.CanapilouTheme
import kotlinx.coroutines.flow.map

// Point d'entrée de l'appli : Android lance cette « activité », qui affiche
// l'appli Compose avec le thème Canapilou. Tout le reste est dans
// AppCanapilou.kt et les dossiers donnees/ et ui/.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Conteneur.demarrer(applicationContext) // avant tout écran
        enableEdgeToEdge() // l'appli dessine aussi sous les barres du téléphone
        setContent {
            // Thème choisi dans les Paramètres (décision 48), lu dans la
            // mémoire du téléphone ; null le temps de la lecture (quelques
            // millisecondes) : rien n'est dessiné, pour ne pas montrer un
            // instant les mauvaises couleurs.
            val flux = remember { Conteneur.depotReglages.theme.map { ChoixTheme.depuisValeur(it) } }
            val choix by flux.collectAsStateWithLifecycle(initialValue = null)
            val telephoneSombre = isSystemInDarkTheme()
            val c = choix
            if (c != null) {
                val sombre = themeSombre(c, telephoneSombre)
                // Icônes des barres du téléphone (heure, batterie…) lisibles sur
                // le thème choisi, même s'il diffère de celui du téléphone.
                DisposableEffect(sombre) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { sombre },
                        navigationBarStyle = SystemBarStyle.auto(VOILE_CLAIR, VOILE_SOMBRE) { sombre },
                    )
                    onDispose {}
                }
                CanapilouTheme(sombre = sombre) {
                    AppCanapilou()
                }
            }
        }
    }
}

// Voile derrière la barre de navigation, utilisé seulement jusqu'à Android 9
// (API 28) d'après la documentation de SystemBarStyle. Valeurs reprises de
// mémoire des valeurs par défaut d'enableEdgeToEdge (« DefaultLightScrim »,
// « DefaultDarkScrim », non accessibles depuis l'appli) : non vérifiées.
private val VOILE_CLAIR = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val VOILE_SOMBRE = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
