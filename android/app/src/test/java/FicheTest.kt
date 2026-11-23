package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.Episode
import io.github.tomuius.canapilou.donnees.Saison
import io.github.tomuius.canapilou.donnees.SerieCatalogue
import io.github.tomuius.canapilou.donnees.Suite
import io.github.tomuius.canapilou.donnees.Suivi
import io.github.tomuius.canapilou.donnees.suitesConsecutives
import io.github.tomuius.canapilou.ui.fiche.EtatEpisode
import io.github.tomuius.canapilou.ui.fiche.STATUTS_MANUELS
import io.github.tomuius.canapilou.ui.fiche.categorieCalculee
import io.github.tomuius.canapilou.ui.fiche.episodesPrecedentsNonVus
import io.github.tomuius.canapilou.ui.fiche.estAnimeFiche
import io.github.tomuius.canapilou.ui.fiche.estDiffuse
import io.github.tomuius.canapilou.ui.fiche.etatEpisode
import io.github.tomuius.canapilou.ui.fiche.ficheAncienne
import io.github.tomuius.canapilou.ui.fiche.libelleCategorie
import io.github.tomuius.canapilou.ui.fiche.libelleDiffusion
import io.github.tomuius.canapilou.ui.fiche.libelleStatut
import io.github.tomuius.canapilou.ui.fiche.ligneApercu
import io.github.tomuius.canapilou.ui.fiche.ligneDiffusion
import io.github.tomuius.canapilou.ui.fiche.nbVusSaison
import io.github.tomuius.canapilou.ui.fiche.nombreEpisodes
import io.github.tomuius.canapilou.ui.fiche.nombreSaisons
import io.github.tomuius.canapilou.ui.fiche.ordonnerSaisons
import io.github.tomuius.canapilou.ui.fiche.perteEpisodes
import io.github.tomuius.canapilou.ui.fiche.saisonInitiale
import io.github.tomuius.canapilou.ui.fiche.statutCalcule
import io.github.tomuius.canapilou.ui.fiche.texteDate
import io.github.tomuius.canapilou.ui.fiche.texteEpisodesCoches
import io.github.tomuius.canapilou.ui.fiche.texteInvite
import io.github.tomuius.canapilou.ui.fiche.texteRemisCommeAvant
import io.github.tomuius.canapilou.ui.fiche.texteRetire
import io.github.tomuius.canapilou.ui.fiche.titreEpisodeAffiche
import io.github.tomuius.canapilou.ui.fiche.titreSaison
import io.github.tomuius.canapilou.ui.fiche.valeurStatutManuel
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

// Règles de la fiche (ui/fiche/ReglesFiche.kt, donnees/Fiche.kt). Les
// résultats attendus ont été calculés avec les fonctions du site (estDiffuse,
// etatEpisode, nbVusSaison, proposerPrecedents, preparer, statutCalcule de
// js/fiche.js ; libelleDiffusion, libelleStatut, valeurStatutManuel,
// perteEpisodes de js/app.js ; suitesConsecutives de js/base.js ; format des
// dates de Intl en français), extraites telles quelles et lancées sous Node,
// pour que l'appli fasse exactement pareil.
class FicheTest {

    private fun serie(
        statut: String?,
        derniereSaison: Int? = null,
        dernierEpisode: Int? = null,
        majLe: String? = null,
        estAnime: Boolean? = false,
    ) = SerieCatalogue(
        id = 1, tmdbId = 1, titre = "Essai", statutDiffusion = statut,
        derniereSaisonDiffusee = derniereSaison, dernierEpisodeDiffuse = dernierEpisode,
        ficheMajLe = majLe, estAnime = estAnime,
    )

    private fun suivi(
        nbVus: Int = 0,
        nbDiffuses: Int = 0,
        saison: Int? = null,
        episode: Int? = null,
        statutManuel: String? = null,
    ) = Suivi(
        serieId = 1, statut = "en_cours", nbDiffuses = nbDiffuses, nbVus = nbVus,
        prochaineSaison = saison, prochainEpisode = episode, statutManuel = statutManuel,
    )

    private fun saisons(vararg numeros: Int) = numeros.map { Saison(it, 1) }

    @Test
    fun episodesSortisCommeSurLeSite() {
        assertEquals(true, estDiffuse(serie("Ended"), 1, 999))
        assertEquals(true, estDiffuse(serie("Canceled"), 3, 1))
        assertEquals(true, estDiffuse(serie("Returning Series"), 0, 5))   // spécial : toujours
        assertEquals(false, estDiffuse(serie("Returning Series"), 1, 1))  // rien de sorti
        assertEquals(true, estDiffuse(serie("Returning Series", 2, 5), 1, 10))
        assertEquals(true, estDiffuse(serie("Returning Series", 2, 5), 2, 5))
        assertEquals(false, estDiffuse(serie("Returning Series", 2, 5), 2, 6))
        assertEquals(false, estDiffuse(serie("Returning Series", 2, 5), 3, 1))
        assertEquals(false, estDiffuse(serie("Returning Series", 2, null), 2, 1))
        assertEquals(true, estDiffuse(serie("Returning Series", 2, null), 1, 3))
        assertEquals(true, estDiffuse(serie(null, 1, 3), 1, 3))
    }

    // Série en diffusion (dernier épisode sorti : S2·E3) avec spéciaux et
    // saisons 1 à 3 ; cochés : S1·E2, S2·E4, spécial 1, et un « S1·E9 » qui
    // n'existe plus dans la saison (ne doit pas être compté).
    private val serieEnCours = serie("Returning Series", 2, 3)
    private val saisonsEnCours = listOf(Saison(0, 2), Saison(1, 3), Saison(2, 5), Saison(3, 4))
    private val vusEnCours = setOf(Episode(1, 2), Episode(2, 4), Episode(0, 1), Episode(1, 9))

    @Test
    fun etatsEtCompteursCommeSurLeSite() {
        assertEquals(EtatEpisode.VU, etatEpisode(serieEnCours, vusEnCours, 1, 2))
        assertEquals(EtatEpisode.NON_VU, etatEpisode(serieEnCours, vusEnCours, 1, 1))
        assertEquals(EtatEpisode.VU, etatEpisode(serieEnCours, vusEnCours, 2, 4)) // coché, même pas sorti
        assertEquals(EtatEpisode.A_VENIR, etatEpisode(serieEnCours, vusEnCours, 2, 5))
        assertEquals(EtatEpisode.NON_VU, etatEpisode(serieEnCours, vusEnCours, 0, 2))
        assertEquals(EtatEpisode.VU, etatEpisode(serieEnCours, vusEnCours, 0, 1))
        assertEquals(listOf(1, 1, 1, 0), saisonsEnCours.map { nbVusSaison(it, vusEnCours) })
    }

    @Test
    fun episodesPrecedentsCommeSurLeSite() {
        fun precedents(saison: Int, episode: Int) =
            episodesPrecedentsNonVus(serieEnCours, saisonsEnCours, vusEnCours, saison, episode)
        assertEquals(listOf(Episode(1, 1), Episode(1, 3), Episode(2, 1)), precedents(2, 2))
        assertEquals(
            listOf(Episode(1, 1), Episode(1, 3), Episode(2, 1), Episode(2, 2), Episode(2, 3)),
            precedents(3, 1), // les épisodes pas encore sortis ne sont pas proposés
        )
        assertEquals(emptyList<Episode>(), precedents(1, 1))
        assertEquals(emptyList<Episode>(), precedents(0, 2)) // spécial : rien à proposer
        // Série finie : tout ce qui précède est sorti.
        assertEquals(
            listOf(
                Episode(1, 1), Episode(1, 2), Episode(1, 3),
                Episode(2, 1), Episode(2, 2), Episode(2, 3), Episode(2, 4), Episode(2, 5),
                Episode(3, 1),
            ),
            episodesPrecedentsNonVus(serie("Ended", 3, 4), saisonsEnCours, emptySet(), 3, 2),
        )
    }

    @Test
    fun saisonOuverteEtOrdreCommeSurLeSite() {
        assertEquals(2, saisonInitiale(saisons(0, 1, 2), suivi(saison = 2, episode = 1)))
        assertEquals(2, saisonInitiale(saisons(0, 1, 2), suivi()))
        assertEquals(0, saisonInitiale(saisons(0), suivi()))
        assertEquals(0, saisonInitiale(emptyList(), suivi()))
        assertEquals(1, saisonInitiale(saisons(1, 2, 3), suivi(saison = 1, episode = 4)))
        assertEquals(listOf(1, 2, 0), ordonnerSaisons(saisons(0, 1, 2)).map { it.numero })
        assertEquals(listOf(0), ordonnerSaisons(saisons(0)).map { it.numero })
        assertEquals(listOf(1, 2, 3), ordonnerSaisons(saisons(1, 2, 3)).map { it.numero })
    }

    // Aperçu d'une série qui n'est pas dans la liste (valeurs obtenues avec
    // preparer, nombreSaisons, libelleDiffusion et estAnimeFiche du site).
    @Test
    fun apercuCommeSurLeSite() {
        assertEquals(1, saisonInitiale(saisons(0, 1, 2), null))
        assertEquals(2, saisonInitiale(saisons(2, 1), null))
        assertEquals(0, saisonInitiale(saisons(0), null))
        assertEquals(0, saisonInitiale(emptyList(), null))
        assertEquals(3, saisonInitiale(saisons(3, 0, 4), null))
        assertEquals("Aucune saison annoncée", nombreSaisons(emptyList()))
        assertEquals("Aucune saison annoncée", nombreSaisons(saisons(0)))
        assertEquals("1 saison", nombreSaisons(saisons(1)))
        assertEquals("1 saison", nombreSaisons(saisons(0, 1)))
        assertEquals("3 saisons", nombreSaisons(saisons(1, 2, 3)))
        assertEquals("2 saisons", nombreSaisons(saisons(0, 1, 2)))
        assertEquals("Terminée | 2 saisons", ligneApercu(serie("Ended"), saisons(1, 2)))
        assertEquals("En diffusion | 1 saison", ligneApercu(serie("Returning Series"), saisons(1)))
        assertEquals("Statut de diffusion inconnu | Aucune saison annoncée", ligneApercu(serie(null), emptyList()))
        assertEquals("Bizarre | 1 saison", ligneApercu(serie("Bizarre"), saisons(0, 1)))
        // Le site écrit « 1 épisodes » et « 0 épisodes » : l'appli accorde
        // (même règle que « épisode vu »), différence voulue.
        assertEquals("0 épisode", nombreEpisodes(0))
        assertEquals("1 épisode", nombreEpisodes(1))
        assertEquals("2 épisodes", nombreEpisodes(2))
        assertEquals("10 épisodes", nombreEpisodes(10))
        assertEquals(true, estAnimeFiche(serie(null, estAnime = true), null))
        assertEquals(false, estAnimeFiche(serie(null, estAnime = false), null))
        assertEquals(false, estAnimeFiche(serie(null, estAnime = null), null))
        assertEquals(false, estAnimeFiche(serie(null, estAnime = true), suivi().copy(estAnime = false)))
        assertEquals(true, estAnimeFiche(serie(null, estAnime = false), suivi().copy(estAnime = true)))
    }

    @Test
    fun textesCommeSurLeSite() {
        assertEquals("En diffusion", libelleDiffusion("Returning Series"))
        assertEquals("En production", libelleDiffusion("In Production"))
        assertEquals("Prévue", libelleDiffusion("Planned"))
        assertEquals("Pilote", libelleDiffusion("Pilot"))
        assertEquals("Terminée", libelleDiffusion("Ended"))
        assertEquals("Annulée", libelleDiffusion("Canceled"))
        assertEquals("Weird", libelleDiffusion("Weird"))
        assertEquals("Statut de diffusion inconnu", libelleDiffusion(null))
        assertEquals(
            "En diffusion | 5 / 808 épisodes vus",
            ligneDiffusion(serie("Returning Series"), suivi(nbVus = 5, nbDiffuses = 808)),
        )
        // Accord avec le nombre d'épisodes vus (décision 70).
        assertEquals("Terminée | 1 / 12 épisode vu", ligneDiffusion(serie("Ended"), suivi(nbVus = 1, nbDiffuses = 12)))
        assertEquals("Prévue | 0 / 0 épisode vu", ligneDiffusion(serie("Planned"), suivi(nbVus = 0, nbDiffuses = 0)))
        assertEquals("Saison 2", titreSaison(2))
        assertEquals("Épisodes spéciaux", titreSaison(0))
        // Textes repris de index.html (barre « cocher les précédents ») et de titreEpisode.
        assertEquals("Cocher aussi l'épisode précédent non vu ?", texteInvite(1))
        assertEquals("Cocher aussi les 4 épisodes précédents non vus ?", texteInvite(4))
        assertEquals("Pilote", titreEpisodeAffiche("Pilote", chargement = false, episode = 1))
        assertEquals("…", titreEpisodeAffiche(null, chargement = true, episode = 3))
        assertEquals("Épisode 3", titreEpisodeAffiche(null, chargement = false, episode = 3))
        assertEquals("Épisode 3", titreEpisodeAffiche("", chargement = false, episode = 3))
    }

    @Test
    fun datesEnFrancaisCommeSurLeSite() {
        val attendues = listOf(
            "2023-01-02" to "2 janv. 2023", "2023-02-28" to "28 févr. 2023", "2023-03-04" to "4 mars 2023",
            "2023-04-05" to "5 avr. 2023", "2023-05-06" to "6 mai 2023", "2023-06-07" to "7 juin 2023",
            "2023-07-08" to "8 juil. 2023", "2023-08-09" to "9 août 2023", "2023-09-10" to "10 sept. 2023",
            "2023-10-11" to "11 oct. 2023", "2023-11-12" to "12 nov. 2023", "2023-12-13" to "13 déc. 2023",
        )
        for ((date, texte) in attendues) assertEquals(texte, texteDate(date, aVenir = false))
        assertEquals("à venir · 29 sept. 2023", texteDate("2023-09-29", aVenir = true))
        assertEquals("", texteDate(null, aVenir = false))
        assertEquals("", texteDate("", aVenir = true))
        assertEquals("", texteDate("pas une date", aVenir = false))
    }

    @Test
    fun ficheARemettreAJour() {
        val maintenant = Instant.parse("2026-11-23T12:00:00Z")
        fun il_y_a(heures: Long) = maintenant.minus(Duration.ofHours(heures)).toString()
        assertEquals(true, ficheAncienne(serie(null, majLe = il_y_a(25)), maintenant))
        assertEquals(true, ficheAncienne(serie(null, majLe = il_y_a(24)), maintenant)) // 24 h pile : oui
        assertEquals(false, ficheAncienne(serie(null, majLe = il_y_a(23)), maintenant))
        assertEquals(true, ficheAncienne(serie(null, majLe = il_y_a(1), estAnime = null), maintenant))
        // Format renvoyé par la base (microsecondes, décalage horaire).
        assertEquals(false, ficheAncienne(serie(null, majLe = "2026-11-23T10:15:30.123456+00:00"), maintenant))
        assertEquals(true, ficheAncienne(serie(null, majLe = "2026-11-20T10:15:30.123456+01:00"), maintenant))
        // Date absente ou illisible : pas ancienne (comme sur le site).
        assertEquals(false, ficheAncienne(serie(null, majLe = null), maintenant))
        assertEquals(false, ficheAncienne(serie(null, majLe = "hier"), maintenant))
    }

    @Test
    fun suitesPourDecocherCommeSurLeSite() {
        fun ep(vararg l: Pair<Int, Int>) = l.map { Episode(it.first, it.second) }
        assertEquals(
            listOf(Suite(1, 1, 3), Suite(1, 5, 5)),
            suitesConsecutives(ep(1 to 1, 1 to 2, 1 to 3, 1 to 5)),
        )
        assertEquals( // dans le désordre, avec un doublon
            listOf(Suite(1, 1, 3), Suite(2, 4, 5)),
            suitesConsecutives(ep(2 to 4, 1 to 3, 1 to 2, 2 to 5, 2 to 5, 1 to 1)),
        )
        assertEquals(
            listOf(Suite(0, 1, 1), Suite(1, 1, 1), Suite(3, 7, 7)),
            suitesConsecutives(ep(0 to 1, 1 to 1, 3 to 7)),
        )
        assertEquals(emptyList<Suite>(), suitesConsecutives(emptyList()))
    }

    @Test
    fun statutsCommeSurLeSite() {
        assertEquals(listOf("en_cours", "plus_tard", "a_jour", "termine", "abandonne"), STATUTS_MANUELS)
        assertEquals(
            listOf("En cours", "En cours", "Plus tard", "À jour", "Terminé", "Abandonné", "—", "—"),
            listOf("a_voir", "en_cours", "plus_tard", "a_jour", "termine", "abandonne", "bizarre", null)
                .map { libelleStatut(it) },
        )
        // Choix affiché dans le menu (null = « Automatique »).
        assertEquals(null, valeurStatutManuel(suivi(statutManuel = null)))
        assertEquals(null, valeurStatutManuel(suivi(statutManuel = "")))
        assertEquals("en_cours", valeurStatutManuel(suivi(statutManuel = "a_voir")))
        assertEquals("en_cours", valeurStatutManuel(suivi(statutManuel = "en_cours")))
        assertEquals("termine", valeurStatutManuel(suivi(statutManuel = "termine")))
        assertEquals("plus_tard", valeurStatutManuel(suivi(statutManuel = "plus_tard")))
        // Statut automatique, affiché à côté de « Automatique ».
        fun calcule(vus: Int, diffuses: Int, diffusion: String?) =
            statutCalcule(serie(diffusion), suivi(nbVus = vus, nbDiffuses = diffuses))
        assertEquals("a_voir", calcule(0, 0, "Returning Series"))
        assertEquals("a_voir", calcule(0, 10, "Ended"))
        assertEquals("en_cours", calcule(3, 10, "Ended"))
        assertEquals("termine", calcule(10, 10, "Ended"))
        assertEquals("termine", calcule(10, 10, "Canceled"))
        assertEquals("a_jour", calcule(10, 10, "Returning Series"))
        assertEquals("a_jour", calcule(12, 10, "In Production"))
        assertEquals("a_jour", calcule(5, 5, null))
    }

    @Test
    fun categoriesEtTextesDesReglages() {
        assertEquals("anime", categorieCalculee(serie(null, estAnime = true)))
        assertEquals("serie", categorieCalculee(serie(null, estAnime = false)))
        assertEquals("serie", categorieCalculee(serie(null, estAnime = null)))
        assertEquals("Animé", libelleCategorie("anime"))
        assertEquals("Série", libelleCategorie("serie"))
        // Textes repris de js/fiche.js, js/app.js et index.html.
        assertEquals("« Bluey » : 1 épisode coché.", texteEpisodesCoches("Bluey", 1))
        assertEquals("« Bluey » : 12 épisodes cochés.", texteEpisodesCoches("Bluey", 12))
        assertEquals("« Bluey » : épisodes décochés et statut remis comme avant.", texteRemisCommeAvant("Bluey"))
        assertEquals("« Bluey » ne fait plus partie de votre liste.", texteRetire("Bluey"))
        assertEquals("Aucun épisode n'est coché.", perteEpisodes(0))
        assertEquals("L'épisode coché sera effacé.", perteEpisodes(1))
        assertEquals("Les 2 épisodes cochés seront effacés.", perteEpisodes(2))
        assertEquals("Les 57 épisodes cochés seront effacés.", perteEpisodes(57))
    }
}
