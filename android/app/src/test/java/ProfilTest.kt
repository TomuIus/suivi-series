package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.ui.profil.FamilleBadges
import io.github.tomuius.canapilou.ui.profil.badgesObtenus
import io.github.tomuius.canapilou.ui.profil.compteBadges
import io.github.tomuius.canapilou.ui.profil.etatDefi
import io.github.tomuius.canapilou.ui.profil.nombre
import io.github.tomuius.canapilou.ui.profil.pageBadges
import io.github.tomuius.canapilou.ui.profil.pourcentageNiveau
import io.github.tomuius.canapilou.ui.profil.sousTitreBadge
import io.github.tomuius.canapilou.ui.profil.sousTitreFamille
import io.github.tomuius.canapilou.ui.profil.texteAvancement
import io.github.tomuius.canapilou.ui.profil.texteCompteBadges
import io.github.tomuius.canapilou.ui.profil.textePaliersObtenus
import io.github.tomuius.canapilou.ui.profil.textePoints
import io.github.tomuius.canapilou.ui.profil.texteProchainRang
import io.github.tomuius.canapilou.ui.profil.titreNiveau
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vérifie que le Profil et la page « Tous les badges » de l'appli s'affichent
// comme ceux du site. Résultats
// attendus calculés le 23/11/2026 avec les fonctions du site (badgesObtenus,
// libellePalier, pourcentageNiveau, pageBadges, compteBadges et
// texteAvancement de js/badges.js, nombre de js/app.js, textes des pages
// « profil » et « badges » de index.html), sur des badges au format de la
// vue « ma_progression », construits à partir des 18 définitions du fichier
// SQL de la progression.
class ProfilTest {

    // Les 18 badges du fichier SQL : code, famille, palier, nom, image, mesure, seuil, unité.
    private val definitions = listOf(
        listOf("temps_bronze", "temps", "bronze", "Temps passé", "clock", "minutes_vues", "1440", "minutes"),
        listOf("temps_argent", "temps", "argent", "Temps passé", "clock", "minutes_vues", "6000", "minutes"),
        listOf("temps_or", "temps", "or", "Temps passé", "clock", "minutes_vues", "30000", "minutes"),
        listOf("temps_platine", "temps", "platine", "Temps passé", "clock", "minutes_vues", "60000", "minutes"),
        listOf("series_bronze", "series", "bronze", "Séries terminées", "device-tv", "series_terminees", "1", "titres"),
        listOf("series_argent", "series", "argent", "Séries terminées", "device-tv", "series_terminees", "5", "titres"),
        listOf("series_or", "series", "or", "Séries terminées", "device-tv", "series_terminees", "20", "titres"),
        listOf("series_platine", "series", "platine", "Séries terminées", "device-tv", "series_terminees", "50", "titres"),
        listOf("animes_bronze", "animes", "bronze", "Animés terminés", "sparkles", "animes_termines", "1", "titres"),
        listOf("animes_argent", "animes", "argent", "Animés terminés", "sparkles", "animes_termines", "5", "titres"),
        listOf("animes_or", "animes", "or", "Animés terminés", "sparkles", "animes_termines", "20", "titres"),
        listOf("animes_platine", "animes", "platine", "Animés terminés", "sparkles", "animes_termines", "50", "titres"),
        listOf("premier_episode", "defis", "", "Premier épisode", "player-play", "episodes_coches", "1", "episodes"),
        listOf("longue_haleine", "defis", "", "Longue haleine", "run", "max_episodes_titre", "100", "episodes"),
        listOf("increvable", "defis", "", "Increvable", "flame", "max_episodes_titre", "500", "episodes"),
        listOf("grande_saga", "defis", "", "Grande saga", "books", "max_saisons_termine", "5", "saisons"),
        listOf("deux_mondes", "defis", "", "Deux mondes", "world", "mondes_termines", "2", "mondes"),
        listOf("coulisses", "defis", "", "Les coulisses", "movie", "speciaux_vus", "10", "episodes"),
    )

    // Badges tels que la vue les renverrait pour ces totaux.
    private fun vue(mesures: Map<String, Long>): List<Badge> = definitions.map { d ->
        val valeur = mesures[d[5]] ?: 0
        val seuil = d[6].toLong()
        Badge(
            code = d[0], famille = d[1], palier = d[2].ifEmpty { null }, nom = d[3], icone = d[4],
            unite = d[7], valeur = valeur, seuil = seuil, obtenu = valeur >= seuil,
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

    // Une famille résumée comme dans le script de calcul des résultats attendus :
    // clé, palier le plus haut, obtenus / paliers, ronds (b+ a+ o- p-),
    // prochain palier, pourcentage, texte, texte des ronds, sous-titre.
    private fun resume(f: FamilleBadges) = listOf(
        f.cle, f.nom, f.icone, f.palier ?: "-", "${f.nbObtenus}/${f.nbPaliers}",
        f.paliers.joinToString("") { it.palier.take(1) + if (it.obtenu) "+" else "-" },
        f.prochain?.code ?: "-", f.pourcentage.toString(), f.texte, textePaliersObtenus(f), sousTitreFamille(f),
    ).joinToString(" | ")

    private fun defis(badges: List<Badge>) =
        badges.joinToString(" ") { it.code + ":" + etatDefi(it) }

    private val casA = mapOf(
        "minutes_vues" to 31000L, "series_terminees" to 5L, "animes_termines" to 0L, "episodes_coches" to 1L,
        "max_episodes_titre" to 120L, "max_saisons_termine" to 2L, "mondes_termines" to 1L, "speciaux_vus" to 10L,
    )

    @Test
    fun pageDesBadgesCommeSurLeSite() {
        val a = vue(casA)
        val page = pageBadges(a)
        assertEquals(
            listOf(
                "temps | Temps passé | clock | or | 3/4 | b+a+o+p- | temps_platine | 51 | 516 / 1\u202F000 h pour Platine | 3 paliers obtenus sur 4. | Or",
                "series | Séries terminées | device-tv | argent | 2/4 | b+a+o-p- | series_or | 25 | 5 / 20 pour Or | 2 paliers obtenus sur 4. | Argent",
                "animes | Animés terminés | sparkles | - | 0/4 | b-a-o-p- | animes_bronze | 0 | 0 / 1 pour Bronze | 0 palier obtenu sur 4. | Aucun palier",
            ),
            page.familles.map(::resume),
        )
        assertEquals(
            "premier_episode:Obtenu longue_haleine:Obtenu increvable:120 / 500 épisodes grande_saga:2 / 5 saisons " +
                "deux_mondes:1 / 2 coulisses:Obtenu",
            defis(page.defis),
        )
        assertEquals("8 badges obtenus sur 18", texteCompteBadges(compteBadges(a)))
        assertEquals(44, compteBadges(a).pourcentage)

        // Ordre de la base inversé : familles et défis dans l'ordre reçu.
        val envers = pageBadges(a.reversed())
        assertEquals(listOf("animes", "series", "temps"), envers.familles.map { it.cle })
        assertEquals(page.familles.reversed().map(::resume), envers.familles.map(::resume))
        assertEquals(
            "coulisses:Obtenu deux_mondes:1 / 2 grande_saga:2 / 5 saisons increvable:120 / 500 épisodes " +
                "longue_haleine:Obtenu premier_episode:Obtenu",
            defis(envers.defis),
        )

        val vide = vue(emptyMap())
        assertEquals(
            "temps | Temps passé | clock | - | 0/4 | b-a-o-p- | temps_bronze | 0 | 0 / 24 h pour Bronze | 0 palier obtenu sur 4. | Aucun palier",
            resume(pageBadges(vide).familles[0]),
        )
        assertEquals(
            "premier_episode:0 / 1 épisode longue_haleine:0 / 100 épisodes increvable:0 / 500 épisodes " +
                "grande_saga:0 / 5 saisons deux_mondes:0 / 2 coulisses:0 / 10 épisodes",
            defis(pageBadges(vide).defis),
        )
        assertEquals("0 badge obtenu sur 18", texteCompteBadges(compteBadges(vide)))
        assertEquals(0, compteBadges(vide).pourcentage)

        val tout = vue(
            mapOf(
                "minutes_vues" to 99999L, "series_terminees" to 60L, "animes_termines" to 50L, "episodes_coches" to 9L,
                "max_episodes_titre" to 500L, "max_saisons_termine" to 5L, "mondes_termines" to 2L, "speciaux_vus" to 10L,
            ),
        )
        assertEquals(
            "temps | Temps passé | clock | platine | 4/4 | b+a+o+p+ | - | 100 | Tous les paliers obtenus | 4 paliers obtenus sur 4. | Platine",
            resume(pageBadges(tout).familles[0]),
        )
        assertEquals("18 badges obtenus sur 18", texteCompteBadges(compteBadges(tout)))
        assertEquals(100, compteBadges(tout).pourcentage)

        assertEquals(0, pageBadges(null).familles.size)
        assertEquals("0 badge obtenu sur 0", texteCompteBadges(compteBadges(null)))
    }

    @Test
    fun avancementCommeSurLeSite() {
        fun b(unite: String, valeur: Long, seuil: Long) =
            Badge(code = "x", famille = "x", nom = "X", icone = "x", unite = unite, valeur = valeur, seuil = seuil)
        assertEquals("0 / 24 h", texteAvancement(b("minutes", 59, 1440)))
        assertEquals("24\u202F999 / 1\u202F000 h", texteAvancement(b("minutes", 1499999, 60000)))
        assertEquals("0 / 1 épisode", texteAvancement(b("episodes", 0, 1)))
        assertEquals("1\u202F234 / 1\u202F000 épisodes", texteAvancement(b("episodes", 1234, 1000)))
        assertEquals("1 / 5 saisons", texteAvancement(b("saisons", 1, 5)))
        assertEquals("1 / 2", texteAvancement(b("mondes", 1, 2)))
        assertEquals("12 / 20", texteAvancement(b("titres", 12, 20)))
        assertEquals("3 / 4", texteAvancement(b("autre", 3, 4)))
        // Données incohérentes (argent obtenu, bronze non, seuil nul) : comme le site.
        val bizarre = pageBadges(
            listOf(
                Badge("x_bronze", "x", "bronze", "X", icone = "clock", unite = "titres", valeur = 3, seuil = 0, obtenu = false),
                Badge("x_argent", "x", "argent", "X", icone = "clock", unite = "titres", valeur = 3, seuil = 2, obtenu = true),
            ),
        ).familles[0]
        assertEquals(listOf("argent", "1", "x_bronze", "0", "3 / 0 pour Bronze"),
            listOf(bizarre.palier, bizarre.nbObtenus.toString(), bizarre.prochain?.code, bizarre.pourcentage.toString(), bizarre.texte))
    }
}
