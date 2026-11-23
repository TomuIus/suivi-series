package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.liste.FiltreCategorie
import io.github.tomuius.canapilou.ui.profil.Statistiques
import io.github.tomuius.canapilou.ui.profil.dureeApprox
import io.github.tomuius.canapilou.ui.profil.statistiques
import io.github.tomuius.canapilou.ui.profil.textePlusTard
import io.github.tomuius.canapilou.ui.profil.texteSansDuree
import io.github.tomuius.canapilou.ui.profil.uniteTotal
import org.junit.Assert.assertEquals
import org.junit.Test

// Vérifie que les statistiques du Profil de l'appli sont celles du site.
// Résultats attendus calculés le 23/11/2026 avec le code du site extrait tel
// quel de js/app.js (SECTIONS, filtrerTitres, RAYON_ANNEAU, ECART_ANNEAU, la
// propriété « statistiques », nombre et dureeApprox), lancé sous Node.
class StatistiquesTest {

    private var numero = 0L

    private fun s(statut: String, anime: Boolean, nbVus: Int, speciaux: Int?, minutes: Long?, sansDuree: Int?) =
        SerieSuivie(
            serieId = ++numero, tmdbId = numero.toInt(), titre = "Série $numero", statut = statut,
            nbDiffuses = 0, nbVus = nbVus, estAnime = anime,
            minutesVues = minutes, nbVusSansDuree = sansDuree, nbSpeciauxVus = speciaux,
        )

    // Résumé comme dans le script de calcul : chiffres, nombres par statut,
    // total, « Plus tard », sans durée, puis chaque part (nombre, %, début, longueur tracée).
    private fun resume(st: Statistiques): String {
        val cles = mapOf("En cours" to "en_cours", "À jour" to "a_jour", "Terminé" to "termine", "Abandonné" to "abandonne")
        return st.chiffres.joinToString(",") { it.libelle + "=" + it.texte } + " | " +
            st.parStatut.joinToString(" ") { cles[it.section.titre] + "=" + it.nombre } +
            " | total ${st.total} | plusTard ${st.plusTard} | sansDuree ${st.sansDuree} | " +
            st.parts.joinToString(" ") {
                "${cles[it.section.titre]}:${it.nombre}:${it.pourcentage}%:debut=${"%.6f".format(java.util.Locale.ROOT, it.debut)}" +
                    ":tracee=${"%.6f".format(java.util.Locale.ROOT, it.tracee)}"
            }
    }

    private val listeA = listOf(
        s("en_cours", false, 10, 1, 420, 0), s("a_voir", true, 0, 0, 0, 0), s("a_jour", false, 30, 0, 1300, 2),
        s("termine", true, 24, 2, 600, 0), s("termine", false, 62, 0, 2790, 0), s("abandonne", false, 3, 0, 130, 1),
        s("plus_tard", false, 0, 0, 0, 0), s("plus_tard", true, 0, 0, 0, 0), s("en_cours", true, 120, 0, 2880, 0),
    )

    @Test
    fun statistiquesCommeSurLeSite() {
        assertEquals(
            "Épisodes vus=252,Temps passé=≈ 135 h,Titres=9,Terminés=2 | en_cours=3 a_jour=1 termine=2 abandonne=1 | " +
                "total 7 | plusTard 2 | sansDuree 3 | " +
                "en_cours:3:43%:debut=0.000000:tracee=116.482923 a_jour:1:14%:debut=118.482923:tracee=37.494308 " +
                "termine:2:29%:debut=157.977231:tracee=76.988615 abandonne:1:14%:debut=236.965846:tracee=37.494308",
            resume(statistiques(listeA, FiltreCategorie.TOUT)),
        )
        assertEquals(
            "Épisodes vus=106,Temps passé=≈ 77 h,Titres=5,Terminés=1 | en_cours=1 a_jour=1 termine=1 abandonne=1 | " +
                "total 4 | plusTard 1 | sansDuree 3 | " +
                "en_cours:1:25%:debut=0.000000:tracee=67.115038 a_jour:1:25%:debut=69.115038:tracee=67.115038 " +
                "termine:1:25%:debut=138.230077:tracee=67.115038 abandonne:1:25%:debut=207.345115:tracee=67.115038",
            resume(statistiques(listeA, FiltreCategorie.SERIES)),
        )
        assertEquals(
            "Épisodes vus=146,Temps passé=≈ 58 h,Titres=4,Terminés=1 | en_cours=2 a_jour=0 termine=1 abandonne=0 | " +
                "total 3 | plusTard 1 | sansDuree 0 | " +
                "en_cours:2:67%:debut=0.000000:tracee=182.306769 termine:1:33%:debut=184.306769:tracee=90.153385",
            resume(statistiques(listeA, FiltreCategorie.ANIMES)),
        )
    }

    @Test
    fun casLimitesCommeSurLeSite() {
        // Un seul statut : pas d'écart, anneau complet.
        val un = listOf(s("termine", false, 1, 0, 45, 1))
        assertEquals(
            "Épisodes vus=1,Temps passé=≈ 45 min,Titres=1,Terminés=1 | en_cours=0 a_jour=0 termine=1 abandonne=0 | " +
                "total 1 | plusTard 0 | sansDuree 1 | termine:1:100%:debut=0.000000:tracee=276.460154",
            resume(statistiques(un, FiltreCategorie.TOUT)),
        )
        val zero = "Épisodes vus=0,Temps passé=0 h,Titres=0,Terminés=0 | en_cours=0 a_jour=0 termine=0 abandonne=0 | " +
            "total 0 | plusTard 0 | sansDuree 0 | "
        assertEquals(zero, resume(statistiques(un, FiltreCategorie.ANIMES)))
        assertEquals(zero, resume(statistiques(emptyList(), FiltreCategorie.TOUT)))
        // Seulement « Plus tard » : anneau vide, mais un titre.
        assertEquals(
            "Épisodes vus=0,Temps passé=0 h,Titres=1,Terminés=0 | en_cours=0 a_jour=0 termine=0 abandonne=0 | " +
                "total 0 | plusTard 1 | sansDuree 0 | ",
            resume(statistiques(listOf(s("plus_tard", false, 0, 0, 0, 0)), FiltreCategorie.TOUT)),
        )
        // Grands nombres et colonnes vides (comptées 0).
        val grand = listOf(s("en_cours", false, 5000, 10, 180000, 0), s("en_cours", false, 1, null, null, null))
        assertEquals(
            "Épisodes vus=5 011,Temps passé=≈ 3 000 h,Titres=2,Terminés=0 | en_cours=2 a_jour=0 termine=0 abandonne=0 | " +
                "total 2 | plusTard 0 | sansDuree 0 | en_cours:2:100%:debut=0.000000:tracee=276.460154",
            resume(statistiques(grand, FiltreCategorie.TOUT)),
        )
    }

    @Test
    fun dureesEtTextesCommeSurLeSite() {
        val attendu = mapOf(
            0L to "0 h", 1L to "≈ 1 min", 59L to "≈ 59 min", 60L to "≈ 1 h", 89L to "≈ 1 h", 90L to "≈ 2 h",
            149L to "≈ 2 h", 150L to "≈ 3 h", 31000L to "≈ 517 h", 180000L to "≈ 3 000 h",
        )
        for ((minutes, texte) in attendu) assertEquals("$minutes minutes", texte, dureeApprox(minutes))
        assertEquals("0 h", dureeApprox(null))
        // Textes du gabarit de index.html (espaces de mise en page réunies, comme dans la page).
        assertEquals("titre", uniteTotal(0))
        assertEquals("titre", uniteTotal(1))
        assertEquals("titres", uniteTotal(7))
        assertEquals("+ 1 titre en « Plus tard », hors diagramme.", textePlusTard(1))
        assertEquals("+ 2 titres en « Plus tard », hors diagramme.", textePlusTard(2))
        assertEquals("1 épisode vu sans durée connue n'est pas compté dans le temps passé.", texteSansDuree(1))
        assertEquals("3 épisodes vus sans durée connue ne sont pas comptés dans le temps passé.", texteSansDuree(3))
    }
}
