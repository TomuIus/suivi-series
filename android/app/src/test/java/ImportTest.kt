package io.github.tomuius.canapilou

import io.github.tomuius.canapilou.donnees.ActionsImport
import io.github.tomuius.canapilou.donnees.BilanImport
import io.github.tomuius.canapilou.donnees.ImportInterrompu
import io.github.tomuius.canapilou.donnees.LigneEpisode
import io.github.tomuius.canapilou.donnees.LigneSuivi
import io.github.tomuius.canapilou.donnees.ModeImport
import io.github.tomuius.canapilou.donnees.PlanImport
import io.github.tomuius.canapilou.donnees.SauvegardeLue
import io.github.tomuius.canapilou.donnees.SauvegardeRefusee
import io.github.tomuius.canapilou.donnees.SerieSauvegarde
import io.github.tomuius.canapilou.donnees.dateJs
import io.github.tomuius.canapilou.donnees.executerImport
import io.github.tomuius.canapilou.donnees.lireSauvegarde
import io.github.tomuius.canapilou.donnees.messageCopie
import io.github.tomuius.canapilou.donnees.nomFichierAvantRemplacement
import io.github.tomuius.canapilou.donnees.planifierImport
import io.github.tomuius.canapilou.donnees.seriesActuelles
import io.github.tomuius.canapilou.donnees.texteApresErreur
import io.github.tomuius.canapilou.donnees.texteBilan
import io.github.tomuius.canapilou.donnees.texteIntrouvables
import io.github.tomuius.canapilou.donnees.textesImport
import io.github.tomuius.canapilou.donnees.trimJs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Vérifie que l'import de l'appli fait comme celui du site. Résultats attendus
// (test/resources/import-attendu.json) produits le 23/11/2026 par le code du
// site tel quel, lancé sous Node avec le fuseau Europe/Paris : dateOuNull,
// texteOuDefaut, lireSauvegarde et planifierImport (js/import.js), le
// regroupement de lireDonneesPourExport (js/base.js), textesImport et
// lancerImport (js/app.js), ce dernier avec des doublures qui notent chaque
// appel à la base et à TMDB. Écarts voulus (décision 95) vérifiés à part.
class ImportTest {

    private val paris = ZoneId.of("Europe/Paris")

    private val attendu: JsonObject by lazy {
        val texte = javaClass.classLoader!!.getResource("import-attendu.json")!!.readText(Charsets.UTF_8)
        Json.parseToJsonElement(texte).jsonObject
    }

    private fun texteOuNull(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content

    // --- Dates et titres ---

    // Écritures que le navigateur devine (autre lecteur que celui du format ISO) : l'appli ne les lit pas.
    private val ecartsDates = setOf(
        "2026-1-19", "Nov 19 2026", "2026/11/19", " 2026-11-19", "2026-11-19 10:00:00", "2026-11-19 10:00:00Z",
        "2026-11-19 10:00:00.123456+00", "2026-11-19 24:00",
    )

    @Test
    fun datesCommeSurLeSite() {
        val dates = attendu["dates"]!!.jsonArray
        for (cas in dates) {
            val texte = cas.jsonArray[0].jsonPrimitive.content
            val site = texteOuNull(cas.jsonArray[1])
            if (texte in ecartsDates) {
                assertNotNull("le site lit « $texte »", site)
                assertNull("l'appli ne lit pas « $texte »", dateJs(texte, paris))
            } else {
                assertEquals("date « $texte »", site, dateJs(texte, paris))
            }
        }
        assertEquals(ecartsDates.size, dates.count { it.jsonArray[0].jsonPrimitive.content in ecartsDates })
    }

    @Test
    fun titresCommeSurLeSite() {
        for (cas in attendu["titres"]!!.jsonArray) {
            val texte = cas.jsonArray[0].jsonPrimitive.content
            val site = cas.jsonArray[1].jsonPrimitive.content
            assertEquals("titre « $texte »", site, trimJs(texte).ifEmpty { "(sans titre)" })
        }
    }

    // --- Lecture du fichier ---

    private fun enJson(s: SerieSauvegarde): JsonObject = buildJsonObject {
        put("tmdbId", s.tmdbId)
        put("titre", s.titre)
        put("statutManuel", s.statutManuel)
        put("categorieManuelle", s.categorieManuelle)
        put("ajouteLe", s.ajouteLe)
        putJsonArray("episodes") {
            for (e in s.episodes) {
                addJsonObject {
                    put("saison", e.saison)
                    put("episode", e.episode)
                    put("vuLe", e.vuLe)
                }
            }
        }
    }

    private fun enJson(s: SauvegardeLue): JsonObject = buildJsonObject {
        put("exporteLe", s.exporteLe)
        put("series", JsonArray(s.series.map(::enJson)))
        putJsonArray("ignorees") { s.ignorees.forEach { add(it) } }
    }

    private fun erreurDeLecture(texte: String): String? = try {
        lireSauvegarde(texte, paris)
        null
    } catch (e: SauvegardeRefusee) {
        e.message
    }

    @Test
    fun lecturesCommeSurLeSite() {
        var ecarts = 0
        for (cas in attendu["lectures"]!!.jsonArray) {
            val texte = cas.jsonObject["texte"]!!.jsonPrimitive.content
            val erreur = texteOuNull(cas.jsonObject["erreur"])
            if ("3000000000" in texte) {
                // Écart voulu : identifiant TMDB trop grand pour la base, série ignorée
                // (le site la garderait, puis l'import s'arrêterait sur une erreur de la base).
                ecarts++
                val lue = lireSauvegarde(texte, paris)
                assertEquals(listOf("Trop grand pour l'appli"), lue.ignorees)
                assertEquals(listOf(7), lue.series.map { it.tmdbId })
                continue
            }
            if (erreur != null) {
                assertEquals("erreur pour ${texte.take(60)}", erreur, erreurDeLecture(texte))
            } else {
                assertEquals("lecture de ${texte.take(60)}", cas.jsonObject["resultat"], enJson(lireSauvegarde(texte, paris)))
            }
        }
        assertEquals(1, ecarts)
    }

    @Test
    fun imbricationTropProfonde() {
        // Écart voulu : au-delà de 100 niveaux, fichier refusé (le site le lirait).
        val enTete = "{\"format\":\"suivi-series\",\"version\":1,\"series\":"
        val profond = enTete + "[".repeat(100) + "]".repeat(100) + "}"
        assertEquals("Ce fichier n'est pas lisible : ce n'est pas une sauvegarde du site.", erreurDeLecture(profond))
        val possible = enTete + "[".repeat(98) + "]".repeat(98) + "}"
        assertNull(erreurDeLecture(possible))
    }

    // --- Votre liste, plans et textes ---

    private fun lignes(e: JsonElement): List<JsonObject> = e.jsonArray.map { it.jsonObject }

    private fun enJson(plan: PlanImport): JsonObject = buildJsonObject {
        put("nouvelles", JsonArray(plan.nouvelles.map(::enJson)))
        putJsonArray("completees") {
            for (c in plan.completees) {
                addJsonObject {
                    put("tmdbId", c.tmdbId)
                    put("titre", c.titre)
                    put("statutActuel", c.statutActuel)
                    putJsonArray("episodes") {
                        for (e in c.episodes) {
                            addJsonObject {
                                put("saison", e.saison)
                                put("episode", e.episode)
                                put("vuLe", e.vuLe)
                            }
                        }
                    }
                }
            }
        }
        put("nbEpisodes", plan.nbEpisodes)
        put("nbRetirees", plan.nbRetirees)
    }

    // Les seuls textes adaptés : la copie est « enregistrée » (à l'endroit choisi) et non « téléchargée ».
    private fun remplacer(texte: String, avant: String, apres: String): String {
        assertTrue("« $avant » attendu dans « $texte »", avant in texte)
        return texte.replace(avant, apres)
    }

    private fun bilan(e: JsonElement?): BilanImport? = (e as? JsonObject)?.let { b ->
        BilanImport(
            b["series"]!!.jsonPrimitive.int,
            b["completees"]!!.jsonPrimitive.int,
            b["episodes"]!!.jsonPrimitive.int,
            b["introuvables"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun plansEtTextesCommeSurLeSite() {
        val lectures = attendu["lectures"]!!.jsonArray
        val listes = attendu["listes"]!!.jsonObject
        for (cas in attendu["plans"]!!.jsonArray) {
            val p = cas.jsonObject
            val texte = lectures[p["lecture"]!!.jsonPrimitive.int].jsonObject["texte"]!!.jsonPrimitive.content
            val sauvegarde = lireSauvegarde(texte, paris)
            val liste = listes[p["liste"]!!.jsonPrimitive.content]!!.jsonObject
            val actuelles = seriesActuelles(lignes(liste["suivis"]!!), lignes(liste["vus"]!!))

            // Votre liste : identifiants TMDB, statuts et épisodes cochés.
            val siteActuelles = p["actuelles"]!!.jsonArray.map { it.jsonObject }
            assertEquals(siteActuelles.map { it["tmdb_id"]!!.jsonPrimitive.intOrNull }, actuelles.map { it.tmdbId })
            assertEquals(siteActuelles.map { texteOuNull(it["statut_manuel"]) }, actuelles.map { it.statutManuel })
            assertEquals(
                siteActuelles.map { s -> s["episodes_vus"]!!.jsonArray.map { it.jsonObject["saison"]!!.jsonPrimitive.int to it.jsonObject["episode"]!!.jsonPrimitive.int } },
                actuelles.map { it.episodes },
            )

            val ajout = planifierImport(sauvegarde, actuelles, ModeImport.AJOUT)
            assertEquals(p["ajout"], enJson(ajout))
            assertEquals(p["remplacement"], enJson(planifierImport(sauvegarde, actuelles, ModeImport.REMPLACEMENT)))

            val textes = textesImport(sauvegarde, actuelles, ajout, paris)
            for (v in p["variantes"]!!.jsonArray) {
                val site = v.jsonObject["textes"]!!.jsonObject
                fun t(nom: String) = site[nom]!!.jsonPrimitive.content
                assertEquals(t("resume"), textes.resume)
                assertEquals(t("ignorees"), textes.ignorees)
                assertEquals(t("listeActuelle"), textes.listeActuelle)
                assertEquals(site["ajoutVide"]!!.jsonPrimitive.boolean, textes.ajoutVide)
                assertEquals(t("ajout"), textes.ajout)
                assertEquals(
                    remplacer(
                        t("remplacement"),
                        "Une copie de votre liste actuelle est d'abord téléchargée.",
                        "Une copie de votre liste actuelle est d'abord enregistrée, à l'endroit de votre choix.",
                    ),
                    textes.remplacement,
                )
                assertEquals(
                    remplacer(
                        t("confirmation"),
                        "Une copie de votre liste actuelle sera téléchargée juste avant : gardez-la, elle permet de revenir en arrière.",
                        "Vous choisirez d'abord où enregistrer une copie de votre liste actuelle : gardez-la, elle permet de " +
                            "revenir en arrière. Sans copie, rien n'est remplacé.",
                    ),
                    textes.confirmation,
                )
                val b = bilan(v.jsonObject["bilan"])
                assertEquals(t("bilan"), b?.let(::texteBilan).orEmpty())
                assertEquals(t("introuvables"), b?.let(::texteIntrouvables).orEmpty())
                val listeVidee = v.jsonObject["listeVidee"]!!.jsonPrimitive.boolean
                val apresErreur = if (listeVidee) {
                    remplacer(t("apresErreur"), "« avant-remplacement » téléchargée juste avant", "« avant-remplacement » enregistrée juste avant")
                } else {
                    t("apresErreur")
                }
                assertEquals(apresErreur, texteApresErreur(listeVidee))
            }
        }
    }

    // --- L'import (lancerImport du site) ---

    // Doublures : notent chaque appel comme celles du test du site, et tombent en panne à l'étape demandée.
    private class Doublures(
        val catalogue: Map<Int, Long>,
        val tmdb: Map<Int, Long?>,
        val panne: String?,
    ) : ActionsImport {
        val appels = mutableListOf<JsonArray>()

        fun note(nom: String, vararg args: JsonElement) {
            appels += JsonArray(listOf(JsonPrimitive(nom)) + args)
            if (nom == panne) throw IllegalStateException("panne : $nom")
        }

        override suspend fun idsCatalogue(tmdbIds: List<Int>): Map<Int, Long> {
            note("idsCatalogue", JsonArray(tmdbIds.map { JsonPrimitive(it) }))
            return tmdbIds.filter { it in catalogue }.associateWith { catalogue.getValue(it) }
        }

        override suspend fun importerSerie(tmdbId: Int): Long {
            note("importerSerie", JsonPrimitive(tmdbId))
            return tmdb[tmdbId] ?: throw IllegalStateException("introuvable")
        }

        override suspend fun viderListe() = note("viderListe")

        override suspend fun suivreSeriesImportees(lignes: List<LigneSuivi>) = note(
            "suivreSeriesImportees",
            buildJsonArray {
                for (l in lignes) {
                    addJsonObject {
                        put("serie_id", l.serieId)
                        put("categorie_manuelle", l.categorieManuelle)
                        put("ajoute_le", l.ajouteLe)
                    }
                }
            },
        )

        override suspend fun cocherEpisodesImportes(lignes: List<LigneEpisode>) = note(
            "cocherEpisodesImportes",
            buildJsonArray {
                for (l in lignes) {
                    addJsonObject {
                        put("serie_id", l.serieId)
                        put("saison", l.saison)
                        put("episode", l.episode)
                        put("vu_le", l.vuLe)
                    }
                }
            },
        )

        override suspend fun changerStatut(serieId: Long, statut: String) =
            note("changerStatut", JsonPrimitive(serieId), JsonPrimitive(statut))
    }

    @Test
    fun importsCommeSurLeSite() = runBlocking {
        val lectures = attendu["lectures"]!!.jsonArray
        val listes = attendu["listes"]!!.jsonObject
        val plans = attendu["plans"]!!.jsonArray
        val maintenant = attendu["maintenant"]!!.jsonPrimitive.content
        val catalogue = attendu["catalogue"]!!.jsonObject.map { (k, v) -> k.toInt() to v.jsonPrimitive.long }.toMap()
        val tmdb = attendu["tmdb"]!!.jsonObject.map { (k, v) -> k.toInt() to (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.long }.toMap()
        for (cas in attendu["imports"]!!.jsonArray) {
            val i = cas.jsonObject
            val p = plans[i["plan"]!!.jsonPrimitive.int].jsonObject
            val texte = lectures[p["lecture"]!!.jsonPrimitive.int].jsonObject["texte"]!!.jsonPrimitive.content
            val liste = listes[p["liste"]!!.jsonPrimitive.content]!!.jsonObject
            val sauvegarde = lireSauvegarde(texte, paris)
            val actuelles = seriesActuelles(lignes(liste["suivis"]!!), lignes(liste["vus"]!!))
            val mode = if (i["mode"]!!.jsonPrimitive.content == "ajout") ModeImport.AJOUT else ModeImport.REMPLACEMENT
            val plan = planifierImport(sauvegarde, actuelles, mode)
            val doublures = Doublures(catalogue, tmdb, texteOuNull(i["panne"]))
            val nom = "import ${i["plan"]} ${i["mode"]} ${i["panne"]}"
            try {
                val bilan = executerImport(plan, mode, doublures, { maintenant }) { doublures.note("progression", JsonPrimitive(it)) }
                assertEquals(nom, "fini", i["etape"]!!.jsonPrimitive.content)
                assertEquals(nom, bilan(i["bilan"]), bilan)
            } catch (e: ImportInterrompu) {
                assertEquals(nom, "erreur", i["etape"]!!.jsonPrimitive.content)
                assertEquals(nom, i["listeVidee"]!!.jsonPrimitive.boolean, e.listeVidee)
                assertEquals(nom, "panne : ${i["panne"]!!.jsonPrimitive.content}", e.cause?.message)
            }
            assertEquals(nom, i["appels"], JsonArray(doublures.appels))
        }
        if (attendu["imports"]!!.jsonArray.size != 9) fail("9 imports attendus")
    }

    @Test
    fun nomDeLaCopieEtMessage() {
        assertEquals("suivi-series-2026-01-05-avant-remplacement.json", nomFichierAvantRemplacement(LocalDate.of(2026, 1, 5)))
        assertEquals("Copie enregistrée : 1 série, 0 épisode coché.", messageCopie(1, 0))
        assertEquals("Copie enregistrée : 5 séries, 120 épisodes cochés.", messageCopie(5, 120))
    }
}
