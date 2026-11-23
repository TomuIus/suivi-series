package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.donnees.urlAffiche
import io.github.tomuius.canapilou.ui.liste.CleSaison
import io.github.tomuius.canapilou.ui.liste.Section
import io.github.tomuius.canapilou.ui.liste.ligneProgression
import io.github.tomuius.canapilou.ui.liste.peutCocher
import io.github.tomuius.canapilou.ui.liste.pourcentage
import io.github.tomuius.canapilou.ui.liste.regrouperParSection
import io.github.tomuius.canapilou.ui.liste.titreProchain
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
        tmdbId: Int = 1,
    ) = SerieSuivie(
        serieId = id, tmdbId = tmdbId, titre = "Essai", statut = statut,
        nbDiffuses = nbDiffuses, nbVus = nbVus, prochaineSaison = saison, prochainEpisode = episode,
    )

    @Test
    fun lignesCommeSurLeSite() {
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(serie("en_cours", 5, 808, 1, 6)))
        assertEquals("Prochain : S1·E1 | 0 / 10 épisode vu", ligneProgression(serie("a_voir", 0, 10, 1, 1)))
        assertEquals("6 épisodes vus | en attente de la suite", ligneProgression(serie("a_jour", 6, 6)))
        assertEquals("10 épisodes vus", ligneProgression(serie("termine", 10, 10)))
        // Accord avec le nombre d'épisodes vus, même avec un total (décision 70).
        assertEquals("1 épisode vu", ligneProgression(serie("termine", 1, 1)))
        assertEquals("0 épisode vu", ligneProgression(serie("termine", 0, 5)))
        assertEquals("1 épisode vu | en attente de la suite", ligneProgression(serie("a_jour", 1, 1)))
        assertEquals("Prochain : S1·E2 | 1 / 808 épisode vu", ligneProgression(serie("en_cours", 1, 808, 1, 2)))
        assertEquals("1 / 10 épisode vu", ligneProgression(serie("plus_tard", 1, 10, 1, 2)))
        assertEquals("0 / 10 épisode vu", ligneProgression(serie("abandonne", 0, 10, 1, 1)))
        assertEquals("0 / 10 épisode vu", ligneProgression(serie("plus_tard", 0, 10, 1, 1)))
        assertEquals("Aucun épisode diffusé pour l'instant", ligneProgression(serie("en_cours", 0, 0)))
        assertEquals("2 / 10 épisodes vus", ligneProgression(serie("plus_tard", 2, 10, 1, 3)))
        assertEquals("2 / 10 épisodes vus", ligneProgression(serie("abandonne", 2, 10, 1, 3)))
        assertEquals("Prochain : S3·E12 | 807 / 808 épisodes vus", ligneProgression(serie("en_cours", 807, 808, 3, 12)))
        assertEquals("4 épisodes vus | en attente de la suite", ligneProgression(serie("a_jour", 4, 3)))
        assertEquals("Aucun épisode diffusé pour l'instant", ligneProgression(serie("abandonne", 0, 0)))
        assertEquals("3 / 10 épisodes vus", ligneProgression(serie("en_cours", 3, 10)))
    }

    @Test
    fun titresDuProchainEpisodeCommeSurLeSite() {
        // Résultats attendus calculés avec titreProchainListe et progression du site.
        val s = serie("en_cours", 5, 808, 1, 6)
        assertEquals("Prochain : S1·E6 \"Ste Lisa Blues\" | 5 / 808 épisodes vus", ligneProgression(s, "Ste Lisa Blues"))
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, "Épisode 6"))
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, "Episode 6"))
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, "épisode 12"))
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, "  Épisode 6  "))
        assertEquals(
            "Prochain : S1·E6 \"Épisode 6 : le retour\" | 5 / 808 épisodes vus",
            ligneProgression(s, "Épisode 6 : le retour"),
        )
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, ""))
        assertEquals("Prochain : S1·E6 | 5 / 808 épisodes vus", ligneProgression(s, null))
    }

    @Test
    fun titreRetrouveParSaison() {
        val titres = mapOf(
            CleSaison(tmdbId = 42, saison = 1) to mapOf(5 to "Cinq", 6 to "Ste Lisa Blues", 7 to null),
            CleSaison(tmdbId = 42, saison = 2) to mapOf(6 to "Autre saison"),
            CleSaison(tmdbId = 7, saison = 1) to mapOf(6 to "Autre série"),
        )
        assertEquals("Ste Lisa Blues", titreProchain(serie("en_cours", 5, 808, 1, 6, tmdbId = 42), titres))
        assertNull(titreProchain(serie("en_cours", 6, 808, 1, 7, tmdbId = 42), titres))  // TMDB sans titre
        assertNull(titreProchain(serie("en_cours", 7, 808, 1, 8, tmdbId = 42), titres))  // épisode absent
        assertNull(titreProchain(serie("en_cours", 0, 10, 3, 1, tmdbId = 42), titres))   // saison pas encore reçue
        assertNull(titreProchain(serie("a_jour", 6, 6, tmdbId = 42), titres))            // pas de prochain épisode
    }

    @Test
    fun adresseDesAffiches() {
        // Même adresse que urlAffiche de js/tmdb.js.
        assertEquals("https://image.tmdb.org/t/p/w154/abc.jpg", urlAffiche("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w92/abc.jpg", urlAffiche("/abc.jpg", "w92"))
        assertNull(urlAffiche(null))
        assertNull(urlAffiche(""))
    }

    @Test
    fun boutonVuSeulementEnCours() {
        assertEquals(true, peutCocher(serie("en_cours", 5, 808, 1, 6)))
        assertEquals(true, peutCocher(serie("a_voir", 0, 10, 1, 1)))
        assertEquals(false, peutCocher(serie("en_cours", 0, 0)))         // rien de sorti
        assertEquals(false, peutCocher(serie("plus_tard", 2, 10, 1, 3)))
        assertEquals(false, peutCocher(serie("abandonne", 2, 10, 1, 3)))
        assertEquals(false, peutCocher(serie("a_jour", 6, 6)))
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
