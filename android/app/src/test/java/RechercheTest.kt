package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.ResultatTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.liste.autresResultats
import io.github.tomuius.canapilou.ui.liste.chercherDansListe
import io.github.tomuius.canapilou.ui.liste.correspond
import io.github.tomuius.canapilou.ui.liste.detailsResultat
import io.github.tomuius.canapilou.ui.liste.mentionDansListe
import io.github.tomuius.canapilou.ui.liste.messageAjout
import io.github.tomuius.canapilou.ui.liste.mots
import io.github.tomuius.canapilou.ui.liste.nomPays
import io.github.tomuius.canapilou.ui.liste.normaliser
import org.junit.Assert.assertEquals
import org.junit.Test

// Recherche commune (ui/liste/Recherche.kt). Les résultats attendus ont été
// calculés avec js/recherche.js du site (normaliser, mots, correspond,
// chercherDansListe, autresResultats) lancé sous Node, et avec les noms de
// pays de Intl.DisplayNames en français, pour que l'appli fasse pareil.
class RechercheTest {

    @Test
    fun texteSimplifieCommeSurLeSite() {
        val attendus = listOf(
            "L’Œuvre — Saison 2" to "l oeuvre saison 2",
            "One-Punch Man" to "one punch man",
            "L'Attaque des Titans" to "l attaque des titans",
            "  Élite  " to "elite",
            "ÆON FLUX" to "aeon flux",
            "Straße" to "strasse",
            "Café crème" to "cafe creme",
            "進撃の巨人" to "進撃の巨人",
            "S.W.A.T." to "s w a t",
            "" to "",
            "Ça (2017)" to "ca 2017",
            "Pokémon : Horizons" to "pokemon horizons",
            "Dr. STONE" to "dr stone",
            "Ñandú" to "nandu",
            "Spy×Family" to "spy family",
        )
        for ((texte, simplifie) in attendus) assertEquals(simplifie, normaliser(texte))
        assertEquals("", normaliser(null))
        assertEquals(listOf("one", "punch", "man"), mots("One-Punch Man"))
        assertEquals(listOf("s", "w", "a", "t"), mots("S.W.A.T."))
        assertEquals(emptyList<String>(), mots(""))
    }

    @Test
    fun correspondancesCommeSurLeSite() {
        val titres = listOf(
            "L'Attaque des Titans", "One-Punch Man", "Someone Special", "Élite",
            "Bluey", "Les Simpson", "Spy×Family", "Pokémon : Horizons",
        )
        fun cherche(q: String) = titres.filter { correspond(it, mots(q)) }
        assertEquals(listOf("L'Attaque des Titans"), cherche("titans att"))
        assertEquals(emptyList<String>(), cherche("unch"))          // pas le début d'un mot
        assertEquals(listOf("One-Punch Man"), cherche("one"))        // pas « Someone »
        assertEquals(listOf("Élite"), cherche("elite"))
        assertEquals(listOf("Élite"), cherche("ÉLITE"))
        assertEquals(listOf("Bluey"), cherche("blu"))
        assertEquals(emptyList<String>(), cherche("simpsons"))     // « Simpson » est plus court
        assertEquals(listOf("Spy×Family"), cherche("spy fam"))
        assertEquals(listOf("Pokémon : Horizons"), cherche("pokemon hor"))
        assertEquals(emptyList<String>(), cherche("zzz"))
        assertEquals(listOf("L'Attaque des Titans"), cherche("l att"))
    }

    private fun serie(titre: String, tmdbId: Int) = SerieSuivie(
        serieId = tmdbId.toLong(), tmdbId = tmdbId, titre = titre, afficheChemin = null,
        statutDiffusion = null, statutManuel = null, statut = "en_cours", nbDiffuses = 0, nbVus = 0,
        prochaineSaison = null, prochainEpisode = null, derniereActivite = null,
    )

    @Test
    fun blocsDeResultatsCommeSurLeSite() {
        val liste = listOf("L'Attaque des Titans", "One-Punch Man", "Someone Special", "Élite")
            .mapIndexed { i, t -> serie(t, 100 + i) }
        assertEquals(listOf(101), chercherDansListe(liste, "one").map { it.tmdbId })
        assertEquals(emptyList<Int>(), chercherDansListe(liste, " - ").map { it.tmdbId })
        val tmdb = listOf(101, 999, 102).map { ResultatTmdb(tmdbId = it, titre = "R$it") }
        assertEquals(listOf(999, 102), autresResultats(tmdb, listOf(serie("x", 101))).map { it.tmdbId })
    }

    @Test
    fun detailsEtMessagesCommeSurLeSite() {
        for ((code, nom) in listOf(
            "JP" to "Japon", "US" to "États-Unis", "KR" to "Corée du Sud",
            "GB" to "Royaume-Uni", "FR" to "France", "CN" to "Chine", "DE" to "Allemagne",
        )) assertEquals(nom, nomPays(code))
        assertEquals(
            "2013 · Japon · 進撃の巨人",
            detailsResultat(ResultatTmdb(1, "L'Attaque des Titans", "進撃の巨人", 2013, pays = listOf("JP"))),
        )
        assertEquals(
            "2019 · États-Unis, Royaume-Uni",
            detailsResultat(ResultatTmdb(2, "Bluey", "Bluey", 2019, pays = listOf("US", "GB"))),
        )
        assertEquals("", detailsResultat(ResultatTmdb(3, "Sans rien")))
        assertEquals("Dans « Plus tard »", mentionDansListe("plus_tard"))
        assertEquals("Dans ma liste", mentionDansListe("en_cours"))
        assertEquals("« Bluey » était déjà dans votre liste.", messageAjout("Bluey", nouvelle = false, plusTard = true))
        assertEquals("« Bluey » est maintenant dans « Plus tard ».", messageAjout("Bluey", nouvelle = true, plusTard = true))
        assertEquals("« Bluey » est maintenant dans votre liste.", messageAjout("Bluey", nouvelle = true, plusTard = false))
    }
}
