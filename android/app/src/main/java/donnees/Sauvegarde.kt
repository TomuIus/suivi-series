package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Fichier de sauvegarde (décisions 22, 40 et 92), exactement comme celui du
// site (lireDonneesPourExport de js/base.js, telechargerSauvegarde, dateDuJour
// et pluriel de js/app.js) : format « suivi-series », version 1, pour que le
// site et l'appli relisent les fichiers l'un de l'autre. Sans accès à la base
// ni au téléphone : testable seul.

/**
 * Écrit [element] comme JSON.stringify(…, null, 2) sur le site : indentation
 * de 2 espaces, « [] » pour une liste vide. Écrit à la main plutôt qu'avec la
 * mise en forme de la bibliothèque, qui écrit une liste vide sur deux lignes
 * (vu en test avec la version 1.6.2) et peut changer d'une version à l'autre.
 * Textes et nombres : écrits par la bibliothèque (mêmes caractères que le
 * site sur l'essai : accents, guillemets, émoji, retours à la ligne…).
 */
private fun ecrireJson(element: JsonElement, retrait: String, sortie: StringBuilder) {
    val suivant = "$retrait  "
    when (element) {
        is JsonObject -> if (element.isEmpty()) {
            sortie.append("{}")
        } else {
            sortie.append("{\n")
            element.entries.forEachIndexed { i, (cle, valeur) ->
                sortie.append(suivant).append(JsonPrimitive(cle).toString()).append(": ")
                ecrireJson(valeur, suivant, sortie)
                if (i < element.size - 1) sortie.append(',')
                sortie.append('\n')
            }
            sortie.append(retrait).append('}')
        }
        is JsonArray -> if (element.isEmpty()) {
            sortie.append("[]")
        } else {
            sortie.append("[\n")
            element.forEachIndexed { i, valeur ->
                sortie.append(suivant)
                ecrireJson(valeur, suivant, sortie)
                if (i < element.size - 1) sortie.append(',')
                sortie.append('\n')
            }
            sortie.append(retrait).append(']')
        }
        is JsonPrimitive -> sortie.append(element.toString()) // texte entre guillemets, nombre, null…
    }
}

/**
 * Contenu du fichier, à partir des lignes lues dans la base : [suivis] (vos
 * séries, avec leur série du catalogue dans « series ») et [vus] (vos
 * épisodes cochés), dans l'ordre de lecture du site. Chaque série est repérée
 * par ses identifiants TMDB, IMDb et TVDB (pas par l'identifiant interne de la
 * base). Les valeurs sont recopiées telles que la base les donne.
 * [exporteLe] : date et heure de l'export, au format du site (« 2026-11-23T20:45:12.345Z »).
 */
fun contenuSauvegarde(suivis: List<JsonObject>, vus: List<JsonObject>, exporteLe: String): String {
    // Épisodes cochés regroupés par série, dans l'ordre de lecture.
    val vusParSerie = LinkedHashMap<String, MutableList<JsonObject>>()
    for (v in vus) {
        val cle = v["serie_id"].toString()
        vusParSerie.getOrPut(cle) { mutableListOf() } += buildJsonObject {
            put("saison", v["saison"] ?: JsonNull)
            put("episode", v["episode"] ?: JsonNull)
            put("vu_le", v["vu_le"] ?: JsonNull)
        }
    }
    val series = buildJsonArray {
        for (s in suivis) {
            val serie = s["series"] as? JsonObject // null si la série manque au catalogue
            add(
                buildJsonObject {
                    put("tmdb_id", serie?.get("tmdb_id") ?: JsonNull)
                    put("imdb_id", serie?.get("imdb_id") ?: JsonNull)
                    put("tvdb_id", serie?.get("tvdb_id") ?: JsonNull)
                    put("titre", serie?.get("titre") ?: JsonNull)
                    put("titre_original", serie?.get("titre_original") ?: JsonNull)
                    put("statut_manuel", s["statut_manuel"] ?: JsonNull) // null = statut automatique
                    put("categorie_manuelle", s["categorie_manuelle"] ?: JsonNull) // null = classement automatique
                    put("ajoute_le", s["ajoute_le"] ?: JsonNull)
                    put("episodes_vus", JsonArray(vusParSerie[s["serie_id"].toString()].orEmpty()))
                },
            )
        }
    }
    val sauvegarde = buildJsonObject {
        put("format", "suivi-series")
        put("version", 1)
        put("exporte_le", exporteLe)
        put("series", series)
    }
    return StringBuilder().also { ecrireJson(sauvegarde, "", it) }.toString()
}

/** Nombre de séries et d'épisodes cochés d'un fichier fait par [contenuSauvegarde]. */
fun compterSauvegarde(contenu: String): Pair<Int, Int> {
    val series = (Json.parseToJsonElement(contenu) as JsonObject)["series"] as JsonArray
    val episodes = series.sumOf { ((it as JsonObject)["episodes_vus"] as JsonArray).size }
    return series.size to episodes
}

// Date et heure de l'export, comme new Date().toISOString() sur le site :
// heure universelle, toujours avec les millièmes de seconde.
private val FORMAT_EXPORTE_LE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** « 2026-11-23T20:45:12.345Z » pour l'instant [instant]. */
fun dateExport(instant: Instant): String = FORMAT_EXPORTE_LE.format(instant)

/** « suivi-series-2026-11-23.json » (date du jour sur le téléphone, comme dateDuJour sur le site). */
fun nomFichierSauvegarde(jour: LocalDate): String =
    "suivi-series-%04d-%02d-%02d.json".format(jour.year, jour.monthValue, jour.dayOfMonth)

/** « 1 série », « 3 séries » (comme pluriel sur le site : pluriel au-delà de 1). */
fun pluriel(nombre: Int, singulier: String, formePlurielle: String = singulier + "s"): String =
    "$nombre ${if (nombre > 1) formePlurielle else singulier}"

/**
 * Message après l'export : « Sauvegarde enregistrée : 3 séries, 120 épisodes
 * cochés. » (le site dit « téléchargée » ; sur le téléphone, le fichier est
 * enregistré à l'endroit choisi).
 */
fun messageExport(nbSeries: Int, nbEpisodes: Int): String =
    "Sauvegarde enregistrée : ${pluriel(nbSeries, "série")}, ${pluriel(nbEpisodes, "épisode coché", "épisodes cochés")}."
