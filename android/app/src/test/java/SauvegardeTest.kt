package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.compterSauvegarde
import io.github.tomuius.canapilou.donnees.contenuSauvegarde
import io.github.tomuius.canapilou.donnees.dateExport
import io.github.tomuius.canapilou.donnees.messageExport
import io.github.tomuius.canapilou.donnees.nomFichierSauvegarde
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

// Vérifie que le fichier de sauvegarde de l'appli est celui du site, au
// caractère près. Fichier attendu (test/resources/sauvegarde-attendue.json)
// produit le 23/11/2026 par lireDonneesPourExport (js/base.js, partie qui suit
// la lecture) et telechargerSauvegarde (js/app.js) du site, recopiés tels
// quels et lancés sous Node, à partir des mêmes lignes imitées de la base
// (sauvegarde-suivis.json, sauvegarde-vus.json) : accents, guillemets,
// barre oblique inverse, retour à la ligne, émoji, séparateur de ligne
// U+2028, série absente du catalogue, épisode d'une série non suivie, dates
// avec ou sans fractions de seconde.
class SauvegardeTest {

    private fun ressource(nom: String): String =
        javaClass.classLoader!!.getResource(nom)!!.readText(Charsets.UTF_8)

    private fun lignes(nom: String): List<JsonObject> =
        (Json.parseToJsonElement(ressource(nom)) as JsonArray).map { it as JsonObject }

    @Test
    fun fichierCommeSurLeSite() {
        val contenu = contenuSauvegarde(
            lignes("sauvegarde-suivis.json"),
            lignes("sauvegarde-vus.json"),
            exporteLe = "2026-11-23T20:45:12.345Z",
        )
        assertEquals(ressource("sauvegarde-attendue.json"), contenu)
        assertEquals(3 to 3, compterSauvegarde(contenu))
    }

    @Test
    fun nomEtMessageCommeSurLeSite() {
        assertEquals("suivi-series-2026-11-23.json", nomFichierSauvegarde(LocalDate.of(2026, 11, 23)))
        assertEquals("suivi-series-2027-01-05.json", nomFichierSauvegarde(LocalDate.of(2027, 1, 5)))
        // Le site dit « téléchargée » ; l'appli dit « enregistrée » (seule différence voulue).
        assertEquals("Sauvegarde enregistrée : 0 série, 0 épisode coché.", messageExport(0, 0))
        assertEquals("Sauvegarde enregistrée : 1 série, 1 épisode coché.", messageExport(1, 1))
        assertEquals("Sauvegarde enregistrée : 3 séries, 120 épisodes cochés.", messageExport(3, 120))
        // Comme new Date().toISOString() : heure universelle, millièmes toujours écrits.
        assertEquals("2026-11-23T20:45:12.345Z", dateExport(Instant.parse("2026-11-23T20:45:12.345Z")))
        assertEquals("2026-11-23T20:45:12.000Z", dateExport(Instant.parse("2026-11-23T20:45:12Z")))
        assertEquals("2026-11-23T20:45:12.999Z", dateExport(Instant.parse("2026-11-23T20:45:12.999999Z")))
    }
}
