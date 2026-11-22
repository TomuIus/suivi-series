package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.liste.Section
import io.github.tomuius.canapilou.ui.liste.ligneProgression
import io.github.tomuius.canapilou.ui.liste.pourcentage
import io.github.tomuius.canapilou.ui.liste.regrouperParSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vérifie que l'appli affiche « Ma liste » comme le site. Les résultats
// attendus ont été calculés le 22/11/2026 avec les fonctions du site
// (progression et pourcentage de js/app.js), copiées telles quelles.
// Pour lancer : clic droit sur ce fichier dans Android Studio › Run 'ListeTest'.
class ListeTest {

    private fun serie(
        statut: String,
        nbVus: Int,
        nbDiffuses: Int,
        saison: Int? = null,
        episode: Int? = null,
        id: Long = 1,
    ) = SerieSuivie(
        serieId = id, tmdbId = 1, titre = "Essai", statut = statut,
        nbDiffuses = nbDiffuses, nbVus = nbVus, prochaineSaison = saison, prochainEpisode = episode,
    )

    @Test
    fun lignesCommeSurLeSite() {
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(serie("en_cours", 5, 808, 1, 6)))
        assertEquals("Prochain : S1·E1 | 0 / 10 épisodes vus", ligneProgression(serie("a_voir", 0, 10, 1, 1)))
        assertEquals("6 épisodes vus | en attente de la suite", ligneProgression(serie("a_jour", 6, 6)))
        assertEquals("10 épisodes vus", ligneProgression(serie("termine", 10, 10)))
        // Comme le site (défaut connu, à corriger des deux côtés) : pas de singulier.
        assertEquals("1 épisodes vus", ligneProgression(serie("termine", 1, 1)))
        assertEquals("Aucun épisode diffusé pour l'instant", ligneProgression(serie("en_cours", 0, 0)))
        assertEquals("2 / 10 épisodes vus", ligneProgression(serie("plus_tard", 2, 10, 1, 3)))
        assertEquals("2 / 10 épisodes vus", ligneProgression(serie("abandonne", 2, 10, 1, 3)))
        assertEquals("Prochain : S3·E12 | 807 / 808 épisodes vus", ligneProgression(serie("en_cours", 807, 808, 3, 12)))
        assertEquals("4 épisodes vus | en attente de la suite", ligneProgression(serie("a_jour", 4, 3)))
        assertEquals("Aucun épisode diffusé pour l'instant", ligneProgression(serie("abandonne", 0, 0)))
        assertEquals("3 / 10 épisodes vus", ligneProgression(serie("en_cours", 3, 10)))
    }

    @Test
    fun pourcentagesCommeSurLeSite() {
        assertEquals(0, pourcentage(serie("en_cours", 5, 808, 1, 6)))
        assertEquals(0, pourcentage(serie("a_voir", 0, 10, 1, 1)))
        assertEquals(100, pourcentage(serie("a_jour", 6, 6)))
        assertEquals(100, pourcentage(serie("termine", 10, 10)))
        assertNull(pourcentage(serie("en_cours", 0, 0)))
        assertEquals(20, pourcentage(serie("plus_tard", 2, 10, 1, 3)))
        assertEquals(99, pourcentage(serie("en_cours", 807, 808, 3, 12))) // arrondi vers le bas
        assertEquals(100, pourcentage(serie("a_jour", 4, 3)))             // jamais plus de 100 %
        assertEquals(30, pourcentage(serie("en_cours", 3, 10)))
    }

    @Test
    fun rangementEnSections() {
        val series = listOf(
            serie("en_cours", 1, 5, 1, 2, id = 1),
            serie("termine", 5, 5, id = 2),
            serie("a_voir", 0, 5, 1, 1, id = 3),
            serie("plus_tard", 0, 5, 1, 1, id = 4),
            serie("a_jour", 3, 3, id = 5),
            serie("abandonne", 1, 5, 1, 2, id = 6),
        )
        val sections = regrouperParSection(series)
        // « À voir » réuni avec « En cours », dans l'ordre de départ.
        assertEquals(listOf(1L, 3L), sections.getValue(Section.EN_COURS).map { it.serieId })
        assertEquals(listOf(5L), sections.getValue(Section.A_JOUR).map { it.serieId })
        assertEquals(listOf(2L), sections.getValue(Section.TERMINE).map { it.serieId })
        assertEquals(listOf(6L), sections.getValue(Section.ABANDONNE).map { it.serieId })
        assertEquals(listOf(4L), sections.getValue(Section.PLUS_TARD).map { it.serieId })
        // Ordre des sections et sections repliées au départ, comme sur le site.
        assertEquals(
            listOf("En cours", "À jour", "Terminé", "Abandonné", "Plus tard"),
            Section.entries.map { it.titre },
        )
        assertEquals(
            listOf(false, false, true, true, true),
            Section.entries.map { it.replieeParDefaut },
        )
    }
}
