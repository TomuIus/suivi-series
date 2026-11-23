package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.ui.profil.badgesObtenus
import io.github.tomuius.canapilou.ui.profil.nombre
import io.github.tomuius.canapilou.ui.profil.pourcentageNiveau
import io.github.tomuius.canapilou.ui.profil.sousTitreBadge
import io.github.tomuius.canapilou.ui.profil.texteProchainRang
import io.github.tomuius.canapilou.ui.profil.textePoints
import io.github.tomuius.canapilou.ui.profil.titreNiveau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vérifie que le Profil de l'appli s'affiche comme celui du site. Résultats
// attendus calculés le 23/11/2026 avec les fonctions du site (badgesObtenus,
// libellePalier et pourcentageNiveau de js/badges.js, nombre de js/app.js,
// textes de la page « profil » de index.html), sur des badges au format de la
// vue « ma_progression », construits à partir des 18 définitions du fichier
// SQL de la progression.
class ProfilTest {

    // Les 18 badges du fichier SQL : code, famille, palier, nom, image, mesure, seuil.
    private val definitions = listOf(
        listOf("temps_bronze", "temps", "bronze", "Temps passé", "clock", "minutes_vues", "1440"),
        listOf("temps_argent", "temps", "argent", "Temps passé", "clock", "minutes_vues", "6000"),
        listOf("temps_or", "temps", "or", "Temps passé", "clock", "minutes_vues", "30000"),
        listOf("temps_platine", "temps", "platine", "Temps passé", "clock", "minutes_vues", "60000"),
        listOf("series_bronze", "series", "bronze", "Séries terminées", "device-tv", "series_terminees", "1"),
        listOf("series_argent", "series", "argent", "Séries terminées", "device-tv", "series_terminees", "5"),
        listOf("series_or", "series", "or", "Séries terminées", "device-tv", "series_terminees", "20"),
        listOf("series_platine", "series", "platine", "Séries terminées", "device-tv", "series_terminees", "50"),
        listOf("animes_bronze", "animes", "bronze", "Animés terminés", "sparkles", "animes_termines", "1"),
        listOf("animes_argent", "animes", "argent", "Animés terminés", "sparkles", "animes_termines", "5"),
        listOf("animes_or", "animes", "or", "Animés terminés", "sparkles", "animes_termines", "20"),
        listOf("animes_platine", "animes", "platine", "Animés terminés", "sparkles", "animes_termines", "50"),
        listOf("premier_episode", "defis", "", "Premier épisode", "player-play", "episodes_coches", "1"),
        listOf("longue_haleine", "defis", "", "Longue haleine", "run", "max_episodes_titre", "100"),
        listOf("increvable", "defis", "", "Increvable", "flame", "max_episodes_titre", "500"),
        listOf("grande_saga", "defis", "", "Grande saga", "books", "max_saisons_termine", "5"),
        listOf("deux_mondes", "defis", "", "Deux mondes", "world", "mondes_termines", "2"),
        listOf("coulisses", "defis", "", "Les coulisses", "movie", "speciaux_vus", "10"),
    )

    // Badges tels que la vue les renverrait pour ces totaux.
    private fun vue(mesures: Map<String, Long>): List<Badge> = definitions.map { d ->
        val valeur = mesures[d[5]] ?: 0
        val seuil = d[6].toLong()
        Badge(
            code = d[0], famille = d[1], palier = d[2].ifEmpty { null }, nom = d[3], icone = d[4],
            valeur = valeur, seuil = seuil, obtenu = valeur >= seuil,
        )
    }

    private fun codes(badges: List<Badge>) = badges.joinToString(" ") { it.code + ":" + sousTitreBadge(it) }

    @Test
    fun badgesDuProfilCommeSurLeSite() {
        val a = mapOf(
            "minutes_vues" to 31000L, "series_terminees" to 5L, "animes_termines" to 0L, "episodes_coches" to 1L,
            "max_episodes_titre" to 120L, "max_saisons_termine" to 2L, "mondes_termines" to 1L, "speciaux_vus" to 10L,
        )
        val tout = mapOf(
            "minutes_vues" to 99999L, "series_terminees" to 60L, "animes_termines" to 50L, "episodes_coches" to 9L,
            "max_episodes_titre" to 500L, "max_saisons_termine" to 5L, "mondes_termines" to 2L, "speciaux_vus" to 10L,
        )
        assertEquals(
            "temps_or:Or series_argent:Argent premier_episode:Défi longue_haleine:Défi coulisses:Défi",
            codes(badgesObtenus(vue(a))),
        )
        assertEquals("", codes(badgesObtenus(vue(emptyMap()))))
        // Ordre de la base inversé : l'ordre reçu est gardé, le palier le plus haut aussi.
        assertEquals(
            "coulisses:Défi longue_haleine:Défi premier_episode:Défi series_argent:Argent temps_or:Or",
            codes(badgesObtenus(vue(a).reversed())),
        )
        assertEquals(
            "temps_platine:Platine series_platine:Platine animes_platine:Platine premier_episode:Défi " +
                "longue_haleine:Défi increvable:Défi grande_saga:Défi deux_mondes:Défi coulisses:Défi",
            codes(badgesObtenus(vue(tout))),
        )
        assertEquals(emptyList<Badge>(), badgesObtenus(null))
    }

    private fun progression(points: Long, pn: Long, pns: Long, niveau: Int = 1, rang: String = "Touriste du salon", nrs: Int? = 3) =
        Progression(
            points = points, niveau = niveau, pointsNiveau = pn, pointsNiveauSuivant = pns,
            rangNumero = 1, rang = rang, niveauRangSuivant = nrs,
        )

    @Test
    fun barreDuNiveauCommeSurLeSite() {
        assertEquals(65, pourcentageNiveau(progression(23, 10, 30)))
        assertEquals(0, pourcentageNiveau(progression(0, 0, 10)))
        assertEquals(0, pourcentageNiveau(progression(30, 30, 60)))
        assertEquals(96, pourcentageNiveau(progression(59, 30, 60)))
        assertEquals(0, pourcentageNiveau(progression(40, 40, 40)))
        assertEquals(100, pourcentageNiveau(progression(70, 30, 60)))
        assertEquals(0, pourcentageNiveau(progression(5, 10, 30)))
    }

    @Test
    fun nombresEtTextesCommeSurLeSite() {
        // Espace fine insécable (U+202F) entre les groupes, comme toLocaleString('fr-FR').
        assertEquals("0", nombre(0))
        assertEquals("7", nombre(7))
        assertEquals("999", nombre(999))
        assertEquals("1 000", nombre(1000))
        assertEquals("12 345", nombre(12345))
        assertEquals("1 234 567", nombre(1234567))

        val p = progression(23, 10, 30, niveau = 2)
        assertEquals("Niveau 2 · Touriste du salon", titreNiveau(p))
        assertEquals("23 points · encore 7 pour le niveau 3", textePoints(p))
        assertEquals("Prochain rang au niveau 3", texteProchainRang(p))
        assertEquals("1 point · encore 9 pour le niveau 2", textePoints(progression(1, 0, 10)))
        assertEquals("0 point · encore 10 pour le niveau 2", textePoints(progression(0, 0, 10)))
        val dernier = progression(12345, 12000, 12400, niveau = 140, rang = "Canapilou d'or", nrs = null)
        assertEquals("Niveau 140 · Canapilou d'or", titreNiveau(dernier))
        assertEquals("12 345 points · encore 55 pour le niveau 141", textePoints(dernier))
        assertNull(texteProchainRang(dernier))
    }
}
