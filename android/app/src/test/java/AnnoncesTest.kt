package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.ui.profil.nomComplet
import io.github.tomuius.canapilou.ui.profil.nouveautes
import io.github.tomuius.canapilou.ui.profil.texteAnnonce
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// Vérifie que les annonces des nouveaux badges et rangs (décision 96) sont
// celles du site. Résultats attendus (test/resources/annonces-attendues.json)
// produits le 23/11/2026 par nouveautes, texteAnnonce, nomComplet et
// lireMemoire de js/badges.js, lancés tels quels sous Node : première fois,
// mémoires illisibles ou d'une autre forme, nouveau rang, un ou plusieurs
// nouveaux badges, rang descendu, badge perdu, valeurs bizarres, liste de
// badges absente ; avec les 18 badges de la base.
class AnnoncesTest {

    private val attendu: JsonObject by lazy {
        val texte = javaClass.classLoader!!.getResource("annonces-attendues.json")!!.readText(Charsets.UTF_8)
        Json.parseToJsonElement(texte).jsonObject
    }

    private fun texteOuNull(e: kotlinx.serialization.json.JsonElement?): String? =
        (e as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun badge(o: JsonObject) = Badge(
        code = o["code"]!!.jsonPrimitive.content,
        famille = o["famille"]!!.jsonPrimitive.content,
        palier = texteOuNull(o["palier"]),
        nom = o["nom"]!!.jsonPrimitive.content,
        icone = o["icone"]!!.jsonPrimitive.content,
        obtenu = o["obtenu"]!!.jsonPrimitive.boolean,
    )

    private fun progression(o: JsonObject) = Progression(
        points = 0,
        niveau = 1,
        pointsNiveau = 0,
        pointsNiveauSuivant = 10,
        rangNumero = o["rang_numero"]!!.jsonPrimitive.int,
        rang = o["rang"]!!.jsonPrimitive.content,
        badges = (o["badges"] as? JsonArray)?.map { badge(it.jsonObject) },
    )

    @Test
    fun annoncesCommeSurLeSite() {
        val cas = attendu["cas"]!!.jsonArray
        for (c in cas) {
            val o = c.jsonObject
            val memoire = texteOuNull(o["memoire"])
            val n = nouveautes(progression(o["progression"]!!.jsonObject), memoire)
            val nom = "mémoire $memoire"
            assertEquals(nom, o["badges"]!!.jsonArray.map { it.jsonPrimitive.content }, n.badges.map { it.code })
            assertEquals(nom, texteOuNull(o["rang"]), n.rang)
            assertEquals(nom, o["nouvelleMemoire"]!!.jsonPrimitive.content, n.memoire)
            assertEquals(nom, o["texte"]!!.jsonPrimitive.content, texteAnnonce(n))
        }
        assertEquals(25, cas.size)
    }

    @Test
    fun nomsCommeSurLeSite() {
        for (c in attendu["noms"]!!.jsonArray) {
            val code = c.jsonArray[0].jsonPrimitive.content
            val famille = code.substringBefore('_')
            val palier = code.substringAfter('_').takeIf { it in setOf("bronze", "argent", "or", "platine") }
            val nomSite = c.jsonArray[1].jsonPrimitive.content
            // Nom du badge sans le palier, tel que la base le donne.
            val nomBase = if (palier == null) nomSite else nomSite.substringBefore(" (")
            assertEquals(code, nomSite, nomComplet(Badge(code, famille, palier, nomBase, icone = "clock")))
        }
    }
}
