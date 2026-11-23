package io.github.tomuius.canapilou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.tomuius.canapilou.ui.theme.CanapilouTheme

// Point d'entrée de l'appli : Android lance cette « activité », qui affiche
// l'appli Compose avec le thème Canapilou. Tout le reste est dans
// AppCanapilou.kt et les dossiers donnees/ et ui/.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Conteneur.demarrer(applicationContext) // avant tout écran
        enableEdgeToEdge() // l'appli dessine aussi sous les barres du téléphone
        setContent {
            CanapilouTheme {
                AppCanapilou()
            }
        }
    }
}
