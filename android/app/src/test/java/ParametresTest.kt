package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.ui.parametres.ChoixTheme
import io.github.tomuius.canapilou.ui.parametres.LONGUEUR_MAX_NOM
import io.github.tomuius.canapilou.ui.parametres.erreurNom
import io.github.tomuius.canapilou.ui.parametres.messageNom
import io.github.tomuius.canapilou.ui.parametres.themeSombre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vérifie que la page Paramètres de l'appli suit les règles du site.
// Résultats attendus calculés le 23/11/2026 avec le script du thème de
// index.html (choix et changer, stockage imité) et la fonction enregistrerNom
// de js/app.js, lancés sous Node.
class ParametresTest {

    @Test
    fun themeCommeSurLeSite() {
        // Valeur retenue → choix (tout sauf « clair » et « sombre » vaut Automatique).
        assertEquals(ChoixTheme.CLAIR, ChoixTheme.depuisValeur("clair"))
        assertEquals(ChoixTheme.SOMBRE, ChoixTheme.depuisValeur("sombre"))
        assertEquals(ChoixTheme.AUTO, ChoixTheme.depuisValeur("auto"))
        assertEquals(ChoixTheme.AUTO, ChoixTheme.depuisValeur("autre"))
        assertEquals(ChoixTheme.AUTO, ChoixTheme.depuisValeur(null))
        assertEquals(listOf("Automatique", "Clair", "Sombre"), ChoixTheme.entries.map { it.libelle })
        // Automatique suit le téléphone ; Clair et Sombre l'emportent sur lui.
        assertEquals(true, themeSombre(ChoixTheme.AUTO, telephoneSombre = true))
        assertEquals(false, themeSombre(ChoixTheme.AUTO, telephoneSombre = false))
        assertEquals(false, themeSombre(ChoixTheme.CLAIR, telephoneSombre = true))
        assertEquals(true, themeSombre(ChoixTheme.SOMBRE, telephoneSombre = false))
    }

    @Test
    fun nomCommeSurLeSite() {
        // Le nom est d'abord débarrassé de ses espaces (trim), comme sur le site.
        assertEquals("Benjamin", "  Benjamin  ".trim())
        assertEquals(30, LONGUEUR_MAX_NOM)
        assertNull(erreurNom("a".repeat(30)))
        assertEquals("Le nom doit faire au plus 30 caractères.", erreurNom("a".repeat(31)))
        assertNull(erreurNom(""))
        assertEquals("Vous vous appelez maintenant « Benjamin ».", messageNom("Benjamin"))
        assertEquals("Vous vous appelez maintenant « Tilou « le lapin » ».", messageNom("Tilou « le lapin »"))
        assertEquals("Nom retiré : votre e-mail est de nouveau affiché.", messageNom(""))
        assertEquals("Nom retiré : votre e-mail est de nouveau affiché.", messageNom("   ".trim()))
    }
}
