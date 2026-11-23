package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

// Import d'une sauvegarde (décisions 40, 90 et 94), comme sur le site :
// lireSauvegarde, planifierImport et compterEpisodes de js/import.js ;
// lancerImport et textesImport de js/app.js. Ce fichier ne parle ni à la base
// ni à TMDB (les écritures passent par ActionsImport) : il se teste seul,
// avec des résultats attendus calculés par le code du site (ImportTest).
//
// Écarts voulus avec le site (décision 95) :
// - dates : seul le format ISO 8601 est lu (celui des fichiers du site et de
//   l'appli). Le navigateur devine aussi d'autres écritures (« Nov 19 2026 »,
//   « 2026/11/19 », une espace à la place du « T »…) : l'appli ne les lit
//   pas ; la date manquante est alors remplacée comme sur le site ;
// - identifiant TMDB plus grand que ce que la base accepte (2 147 483 647) :
//   la série est ignorée (sur le site, l'import s'arrêterait sur une erreur de
//   la base) ;
// - fichier imbriqué sur plus de PROFONDEUR_MAX niveaux (une vraie sauvegarde
//   en a 5) : refusé comme illisible, par prudence pour la mémoire du téléphone.

/** Taille maximale d'un fichier de sauvegarde : 10 Mo, comme TAILLE_MAX sur le site (choix de Claude, à revoir). */
const val TAILLE_MAX_SAUVEGARDE = 10 * 1024 * 1024

private val STATUTS = setOf("a_voir", "en_cours", "a_jour", "termine", "abandonne", "plus_tard")
private val CATEGORIES = setOf("serie", "anime")

/** Un épisode coché lu dans le fichier ; [vuLe] : date ISO (« 2026-11-19T10:00:00.000Z ») ou null. */
data class EpisodeSauvegarde(val saison: Int, val episode: Int, val vuLe: String?)

/** Une série lue dans le fichier (statut et catégorie null : automatiques ou illisibles). */
data class SerieSauvegarde(
    val tmdbId: Int,
    val titre: String,
    val statutManuel: String?,
    val categorieManuelle: String?,
    val ajouteLe: String?,
    val episodes: List<EpisodeSauvegarde>,
)

/** Le fichier lu : sa date d'export, ses séries, et les titres des séries ignorées (sans identifiant TMDB valable). */
data class SauvegardeLue(val exporteLe: String?, val series: List<SerieSauvegarde>, val ignorees: List<String>)

/** Fichier refusé ; le message, en français, est affiché tel quel. */
class SauvegardeRefusee(message: String) : Exception(message)

// --- Lecture du fichier (lireSauvegarde du site) ---

/**
 * Lit le texte d'un fichier de sauvegarde, comme lireSauvegarde sur le site :
 * mêmes vérifications, mêmes messages d'erreur (SauvegardeRefusee), séries
 * présentes deux fois réunies en une seule (la première l'emporte, épisodes
 * mis ensemble). [zone] : fuseau horaire des dates écrites sans fuseau (celui
 * du téléphone, comme celui de l'ordinateur sur le site).
 */
fun lireSauvegarde(texte: String, zone: ZoneId = ZoneId.systemDefault()): SauvegardeLue {
    val donnees = try {
        require(jsonStrict(texte))
        Json.parseToJsonElement(texte).also { require(litterauxStricts(it)) }
    } catch (e: Exception) {
        throw SauvegardeRefusee("Ce fichier n'est pas lisible : ce n'est pas une sauvegarde du site.")
    }
    val series = donnees.champ("series")
    if (texteJson(donnees.champ("format")) != "suivi-series" || series !is JsonArray) {
        throw SauvegardeRefusee("Ce fichier n'est pas une sauvegarde du site.")
    }
    val version = donnees.champ("version")
    if (nombre(version) != 1.0) {
        throw SauvegardeRefusee("Cette sauvegarde est d'une version inconnue (${texteJs(version)}).")
    }

    // Une série en cours de lecture ; ses épisodes rangés par « saison-épisode ».
    class SerieLue(val serie: SerieSauvegarde, val episodes: LinkedHashMap<Pair<Int, Int>, EpisodeSauvegarde>)

    val parTmdb = LinkedHashMap<Int, SerieLue>()
    val ignorees = mutableListOf<String>()
    for (s in series) {
        if (s is JsonPrimitive) continue // ni objet ni liste : passé, comme sur le site
        val tmdbId = entier(s.champ("tmdb_id"))?.takeIf { it > 0 }
        if (tmdbId == null) {
            ignorees += texteOuDefaut(s.champ("titre"))
            continue
        }
        val lue = parTmdb.getOrPut(tmdbId) {
            SerieLue(
                SerieSauvegarde(
                    tmdbId = tmdbId,
                    titre = texteOuDefaut(s.champ("titre")),
                    statutManuel = texteJson(s.champ("statut_manuel"))?.takeIf { it in STATUTS },
                    categorieManuelle = texteJson(s.champ("categorie_manuelle"))?.takeIf { it in CATEGORIES },
                    ajouteLe = dateOuNull(s.champ("ajoute_le"), zone),
                    episodes = emptyList(),
                ),
                LinkedHashMap(),
            )
        }
        for (e in s.champ("episodes_vus") as? JsonArray ?: JsonArray(emptyList())) {
            val saison = entier(e.champ("saison")) ?: continue
            val episode = entier(e.champ("episode")) ?: continue
            if (saison < 0 || episode < 0) continue
            lue.episodes.getOrPut(saison to episode) {
                EpisodeSauvegarde(saison, episode, dateOuNull(e.champ("vu_le"), zone))
            }
        }
    }
    return SauvegardeLue(
        exporteLe = dateOuNull(donnees.champ("exporte_le"), zone),
        series = parTmdb.values.map { it.serie.copy(episodes = it.episodes.values.toList()) },
        ignorees = ignorees,
    )
}

// La bibliothèque JSON accepte des fichiers que JSON.parse refuse sur le site
// (vu en test : valeurs sans guillemets comme « 01 », « NaN » ou « tru »,
// tabulation ou retour à la ligne bruts dans un texte). Ces deux vérifications
// les refusent aussi.
private const val PROFONDEUR_MAX = 100
private val NOMBRE_JSON = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

// Caractères hors des textes, caractères de contrôle dans les textes, profondeur.
private fun jsonStrict(texte: String): Boolean {
    var dansTexte = false
    var echappe = false
    var profondeur = 0
    for (c in texte) {
        if (dansTexte) {
            when {
                echappe -> echappe = false
                c == '\\' -> echappe = true
                c == '"' -> dansTexte = false
                c < ' ' -> return false
            }
        } else {
            when (c) {
                '"' -> dansTexte = true
                '{', '[' -> if (++profondeur > PROFONDEUR_MAX) return false
                '}', ']' -> profondeur--
                ' ', '\t', '\n', '\r', ',', ':' -> {}
                else -> if (c !in '0'..'9' && c !in 'a'..'z' && c !in 'A'..'Z' && c != '+' && c != '-' && c != '.') return false
            }
        }
    }
    return true
}

// Valeurs sans guillemets : seulement true, false, null et les nombres du format JSON.
private fun litterauxStricts(e: JsonElement): Boolean = when (e) {
    is JsonObject -> e.values.all(::litterauxStricts)
    is JsonArray -> e.all(::litterauxStricts)
    is JsonPrimitive -> e.isString || e is JsonNull || e.content == "true" || e.content == "false" || NOMBRE_JSON.matches(e.content)
}

// « valeur.nom » en JavaScript : null si absent (« undefined ») ou si la valeur n'est pas un objet.
private fun JsonElement?.champ(nom: String): JsonElement? = (this as? JsonObject)?.get(nom)

// Le texte, si la valeur est un texte.
private fun texteJson(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content

// Le nombre, si la valeur est un nombre (après litterauxStricts, tout le reste est texte, booléen ou null).
private fun nombre(e: JsonElement?): Double? =
    (e as? JsonPrimitive)?.takeIf { !it.isString && NOMBRE_JSON.matches(it.content) }?.content?.toDouble()

// Nombre entier, comme Number.isInteger (« 2.0 » et « 1e3 » le sont). Au-delà
// de ce que la base accepte : null (écart voulu, voir en haut).
private fun entier(e: JsonElement?): Int? {
    val d = nombre(e) ?: return null
    if (d.isInfinite() || d != Math.floor(d) || d < Int.MIN_VALUE || d > Int.MAX_VALUE) return null
    return d.toInt()
}

// La valeur écrite comme `${valeur}` en JavaScript (message « version inconnue »).
// Nombre non entier ou très grand : écrit tel que dans le fichier (JavaScript
// le réécrit parfois autrement : « 1.50 » → « 1.5 »).
private fun texteJs(e: JsonElement?): String = when (e) {
    null -> "undefined"
    is JsonNull -> "null"
    is JsonObject -> "[object Object]"
    is JsonArray -> e.joinToString(",") { if (it is JsonNull) "" else texteJs(it) }
    is JsonPrimitive -> {
        val d = nombre(e)
        if (d != null && d == Math.floor(d) && Math.abs(d) < 1e21) BigDecimal(d).toBigInteger().toString() else e.content
    }
}

// Titre nettoyé des espaces au début et à la fin, ou « (sans titre) ».
private fun texteOuDefaut(e: JsonElement?): String =
    texteJson(e)?.let(::trimJs)?.takeIf { it.isNotEmpty() } ?: "(sans titre)"

/**
 * Comme texte.trim() en JavaScript, qui ne retire pas tout à fait les mêmes
 * caractères que trim() en Kotlin (U+FEFF oui, U+001C à U+001F non).
 */
fun trimJs(texte: String): String = texte.trim(::espaceJs)

private fun espaceJs(c: Char): Boolean =
    c in "\t\n\u000B\u000C\r \u00A0\u1680\u2028\u2029\u202F\u205F\u3000\uFEFF" || c in '\u2000'..'\u200A'

// --- Dates (dateOuNull du site, qui passe par Date.parse) ---

private fun dateOuNull(e: JsonElement?, zone: ZoneId): String? = texteJson(e)?.let { dateJs(it, zone) }

// Format ISO 8601 tel que Date.parse le lit (vérifié avec Node, voir ImportTest) :
// année (4 chiffres, ou signe et 6 chiffres), mois et jour facultatifs, heure
// facultative après « T », fuseau facultatif.
private val DATE_ISO = Regex(
    "([0-9]{4}|[+-][0-9]{6})(?:-([0-9]{2})(?:-([0-9]{2}))?)?" +
        "(?:[Tt]([0-9]{2}):([0-9]{2})(?::([0-9]{2})(?:\\.([0-9]+))?)?)?" +
        "([Zz]|[+-][0-9]{2}:?[0-9]{2})?",
)

// Plus grand écart avec le 1er janvier 1970 que JavaScript accepte : 8,64 × 10¹⁵ ms.
private const val LIMITE_DATE_MS = 8_640_000_000_000_000L

/**
 * Comme new Date(Date.parse(texte)).toISOString() : « 2026-11-19T10:00:00.123Z »,
 * ou null si la date est illisible. Mêmes tolérances que le navigateur :
 * 30 février → 2 mars, « 24:00 » → minuit du lendemain, millièmes coupés
 * (pas arrondis). Sans fuseau : heure de [zone] (une date seule est en
 * temps universel).
 */
fun dateJs(texte: String, zone: ZoneId): String? {
    val m = DATE_ISO.matchEntire(texte) ?: return null
    val g = m.groupValues
    val annee = g[1].toInt()
    if (g[1] == "-000000") return null
    val mois = g[2].ifEmpty { "1" }.toInt()
    val jour = g[3].ifEmpty { "1" }.toInt()
    val avecHeure = g[4].isNotEmpty()
    val heure = g[4].ifEmpty { "0" }.toInt()
    val minute = g[5].ifEmpty { "0" }.toInt()
    val seconde = g[6].ifEmpty { "0" }.toInt()
    val millieme = (g[7] + "000").take(3).toInt()
    val fuseau = g[8]
    if (mois !in 1..12 || jour !in 1..31 || heure !in 0..24 || minute !in 0..59 || seconde !in 0..59) return null
    if (heure == 24 && (minute != 0 || seconde != 0 || g[7].any { it != '0' })) return null
    if (!avecHeure && fuseau.length > 1) return null // décalage (« +01:00 ») sans heure : refusé

    // Jour au-delà de la fin du mois : reporté sur le mois suivant, comme le navigateur.
    val jours = LocalDate.of(annee, mois, 1).toEpochDay() + (jour - 1)
    val ms = jours * 86_400_000L + heure * 3_600_000L + minute * 60_000L + seconde * 1_000L + millieme
    val instant = when {
        fuseau.length == 1 -> ms // « Z »
        fuseau.isNotEmpty() -> {
            val chiffres = fuseau.substring(1).replace(":", "")
            val h = chiffres.take(2).toInt()
            val mn = chiffres.takeLast(2).toInt()
            if (h > 23 || mn > 59) return null
            ms - (if (fuseau[0] == '-') -1 else 1) * (h * 60 + mn) * 60_000L
        }
        !avecHeure -> ms // date seule : temps universel
        else -> LocalDateTime.ofEpochSecond(Math.floorDiv(ms, 1000L), Math.floorMod(ms, 1000L).toInt() * 1_000_000, ZoneOffset.UTC)
            .atZone(zone).toInstant().toEpochMilli()
    }
    if (Math.abs(instant) > LIMITE_DATE_MS) return null
    return isoJs(instant)
}

// Comme toISOString : année sur 4 chiffres, ou signe et 6 chiffres hors de 0 à 9999.
private fun isoJs(ms: Long): String {
    val t = LocalDateTime.ofEpochSecond(Math.floorDiv(ms, 1000L), Math.floorMod(ms, 1000L).toInt() * 1_000_000, ZoneOffset.UTC)
    val annee = if (t.year in 0..9999) "%04d".format(t.year) else (if (t.year < 0) "-" else "+") + "%06d".format(Math.abs(t.year))
    return "%s-%02d-%02dT%02d:%02d:%02d.%03dZ".format(
        annee, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second, t.nano / 1_000_000,
    )
}

/** Date courte, comme toLocaleDateString('fr-FR') : « 20/11/2026 » (jour du fuseau [zone]). */
fun dateCourteFr(iso: String, zone: ZoneId): String {
    val jour = Instant.parse(iso).atZone(zone).toLocalDate()
    return "%02d/%02d/%d".format(jour.dayOfMonth, jour.monthValue, jour.year)
}

// --- Votre liste actuelle et ce que l'import y ajoutera (planifierImport du site) ---

/** Une série de votre liste : identifiant TMDB (null si la série manque au catalogue), statut manuel, épisodes cochés. */
data class SerieActuelle(val tmdbId: Int?, val statutManuel: String?, val episodes: List<Pair<Int, Int>>)

/**
 * Votre liste, à partir des lignes lues pour l'export (DepotSauvegarde),
 * comme lireDonneesPourExport sur le site.
 */
fun seriesActuelles(suivis: List<JsonObject>, vus: List<JsonObject>): List<SerieActuelle> {
    val vusParSerie = HashMap<String, MutableList<Pair<Int, Int>>>()
    for (v in vus) {
        val saison = (v["saison"] as? JsonPrimitive)?.intOrNull ?: continue
        val episode = (v["episode"] as? JsonPrimitive)?.intOrNull ?: continue
        vusParSerie.getOrPut(v["serie_id"].toString()) { mutableListOf() } += saison to episode
    }
    return suivis.map { s ->
        SerieActuelle(
            tmdbId = ((s["series"] as? JsonObject)?.get("tmdb_id") as? JsonPrimitive)?.intOrNull,
            statutManuel = texteJson(s["statut_manuel"]),
            episodes = vusParSerie[s["serie_id"].toString()].orEmpty(),
        )
    }
}

/** Ajouter à la liste, ou la remplacer entièrement. */
enum class ModeImport { AJOUT, REMPLACEMENT }

/** Série déjà suivie à laquelle l'import ajoute des épisodes (son statut et sa catégorie ne changent pas). */
data class SerieCompletee(val tmdbId: Int, val titre: String, val statutActuel: String?, val episodes: List<EpisodeSauvegarde>)

/** Ce qu'un import fera : séries ajoutées entières, séries complétées, nombre d'épisodes à cocher, séries retirées. */
data class PlanImport(
    val nouvelles: List<SerieSauvegarde>,
    val completees: List<SerieCompletee>,
    val nbEpisodes: Int,
    val nbRetirees: Int,
)

/**
 * Comme planifierImport sur le site. AJOUT : les séries absentes de la liste
 * sont ajoutées entières ; pour les séries déjà suivies, seuls les épisodes
 * pas encore cochés sont ajoutés. REMPLACEMENT : la liste sera vidée, puis
 * toute la sauvegarde remise.
 */
fun planifierImport(sauvegarde: SauvegardeLue, actuelles: List<SerieActuelle>, mode: ModeImport): PlanImport {
    if (mode == ModeImport.REMPLACEMENT) {
        return PlanImport(sauvegarde.series, emptyList(), sauvegarde.series.sumOf { it.episodes.size }, actuelles.size)
    }
    val parTmdb = actuelles.filter { it.tmdbId != null }.associateBy { it.tmdbId }
    val nouvelles = mutableListOf<SerieSauvegarde>()
    val completees = mutableListOf<SerieCompletee>()
    for (s in sauvegarde.series) {
        val actuelle = parTmdb[s.tmdbId]
        if (actuelle == null) {
            nouvelles += s
            continue
        }
        val dejaVus = actuelle.episodes.toSet()
        val manquants = s.episodes.filter { (it.saison to it.episode) !in dejaVus }
        if (manquants.isNotEmpty()) completees += SerieCompletee(s.tmdbId, s.titre, actuelle.statutManuel, manquants)
    }
    return PlanImport(nouvelles, completees, nouvelles.sumOf { it.episodes.size } + completees.sumOf { it.episodes.size }, 0)
}

// --- Textes du panneau (textesImport du site) ---

/**
 * Textes avant l'import, comme textesImport sur le site, sauf la copie
 * (« enregistrée » à l'endroit choisi, au lieu de « téléchargée »).
 */
data class TextesImport(
    val resume: String,
    val ignorees: String,
    val listeActuelle: String,
    val ajoutVide: Boolean,
    val ajout: String,
    val remplacement: String,
    val confirmation: String,
)

fun textesImport(sauvegarde: SauvegardeLue, actuelles: List<SerieActuelle>, ajout: PlanImport, zone: ZoneId): TextesImport {
    val nbSauvegarde = sauvegarde.series.sumOf { it.episodes.size }
    val date = sauvegarde.exporteLe?.let { " du ${dateCourteFr(it, zone)}" }.orEmpty()
    val nbActuels = actuelles.sumOf { it.episodes.size }
    val ajoutVide = ajout.nouvelles.isEmpty() && ajout.nbEpisodes == 0
    return TextesImport(
        resume = "Sauvegarde$date : ${pluriel(sauvegarde.series.size, "série")}, " +
            "${pluriel(nbSauvegarde, "épisode coché", "épisodes cochés")}.",
        ignorees = if (sauvegarde.ignorees.isEmpty()) "" else
            "${pluriel(sauvegarde.ignorees.size, "série ignorée", "séries ignorées")} (sans identifiant TMDB) : " +
                "${sauvegarde.ignorees.joinToString(", ")}.",
        listeActuelle = "Votre liste contient déjà ${pluriel(actuelles.size, "série")}. Que voulez-vous faire ?",
        ajoutVide = ajoutVide,
        ajout = if (ajoutVide) "Rien à ajouter : tout est déjà dans votre liste." else
            "${pluriel(ajout.nouvelles.size, "nouvelle série", "nouvelles séries")} et " +
                "${pluriel(ajout.nbEpisodes, "épisode", "épisodes")} à cocher. Vos séries actuelles, " +
                "leurs statuts et leurs épisodes cochés sont gardés.",
        remplacement = "Votre liste actuelle (${pluriel(actuelles.size, "série")}) est retirée, puis la sauvegarde " +
            "est remise telle quelle. Une copie de votre liste actuelle est d'abord enregistrée, à l'endroit de votre choix.",
        confirmation = "Votre liste actuelle (${pluriel(actuelles.size, "série")}, " +
            "${pluriel(nbActuels, "épisode coché", "épisodes cochés")}) sera retirée, puis remplacée par la sauvegarde " +
            "(${pluriel(sauvegarde.series.size, "série")}, ${pluriel(nbSauvegarde, "épisode coché", "épisodes cochés")}). " +
            "Vous choisirez d'abord où enregistrer une copie de votre liste actuelle : gardez-la, elle permet de " +
            "revenir en arrière. Sans copie, rien n'est remplacé.",
    )
}

/** Résultat d'un import terminé. */
data class BilanImport(val series: Int, val completees: Int, val episodes: Int, val introuvables: List<String>)

fun texteBilan(b: BilanImport): String =
    "Import terminé : ${pluriel(b.series, "série ajoutée", "séries ajoutées")}" +
        (if (b.completees > 0) ", ${pluriel(b.completees, "série complétée", "séries complétées")}" else "") +
        ", ${pluriel(b.episodes, "épisode coché", "épisodes cochés")}."

fun texteIntrouvables(b: BilanImport): String =
    if (b.introuvables.isEmpty()) "" else "Non importées (introuvables sur TMDB) : ${b.introuvables.joinToString(", ")}."

fun texteApresErreur(listeVidee: Boolean): String =
    if (listeVidee) {
        "Votre liste a déjà été vidée : réimportez la copie « avant-remplacement » enregistrée juste avant, ou relancez cet import."
    } else {
        "Vous pouvez relancer l'import : ce qui a déjà été ajouté ne sera pas compté deux fois."
    }

/** « suivi-series-2026-11-23-avant-remplacement.json » : la copie faite avant un remplacement. */
fun nomFichierAvantRemplacement(jour: LocalDate): String =
    "suivi-series-%04d-%02d-%02d-avant-remplacement.json".format(jour.year, jour.monthValue, jour.dayOfMonth)

/** Message après la copie : « Copie enregistrée : 5 séries, 120 épisodes cochés. » */
fun messageCopie(nbSeries: Int, nbEpisodes: Int): String =
    "Copie enregistrée : ${pluriel(nbSeries, "série")}, ${pluriel(nbEpisodes, "épisode coché", "épisodes cochés")}."

// --- L'import lui-même (lancerImport du site) ---

/** Une série à suivre, avec sa catégorie et sa date d'ajout d'origine. */
data class LigneSuivi(val serieId: Long, val categorieManuelle: String?, val ajouteLe: String)

/** Un épisode à cocher, avec sa date d'origine. */
data class LigneEpisode(val serieId: Long, val saison: Int, val episode: Int, val vuLe: String)

/** Les lectures et écritures dont l'import a besoin (la base et TMDB dans l'appli, des doublures dans les tests). */
interface ActionsImport {
    /** Identifiants internes des séries déjà dans le catalogue : identifiant TMDB → identifiant interne. */
    suspend fun idsCatalogue(tmdbIds: List<Int>): Map<Int, Long>

    /** Copie la fiche TMDB dans le catalogue ; renvoie l'identifiant interne. */
    suspend fun importerSerie(tmdbId: Int): Long
    suspend fun viderListe()
    suspend fun suivreSeriesImportees(lignes: List<LigneSuivi>)
    suspend fun cocherEpisodesImportes(lignes: List<LigneEpisode>)
    suspend fun changerStatut(serieId: Long, statut: String)
}

/** L'import s'est arrêté sur une erreur ; [listeVidee] : la liste avait déjà été vidée (remplacement). */
class ImportInterrompu(val listeVidee: Boolean, cause: Throwable) : Exception(cause)

/**
 * L'import, dans l'ordre du site, choisi pour ne rien perdre en cas de souci
 * (la copie, pour un remplacement, est faite avant, par l'écran) :
 * 1) fiches des séries absentes du catalogue, redemandées à TMDB (une série
 *    introuvable est passée et notée) ;
 * 2) seulement ensuite, retrait de la liste actuelle (remplacement) ;
 * 3) séries, puis épisodes cochés, puis statuts : cocher un épisode sort une
 *    série de « Plus tard » (décision 26), donc les statuts viennent en dernier.
 * [maintenant] : date du jour au format ISO, utilisée quand le fichier n'en
 * donne pas. [progression] : texte de l'étape en cours.
 * Lève ImportInterrompu si une étape échoue.
 */
suspend fun executerImport(
    plan: PlanImport,
    mode: ModeImport,
    actions: ActionsImport,
    maintenant: () -> String,
    progression: (String) -> Unit,
): BilanImport {
    val introuvables = mutableListOf<String>()
    var listeVidee = false
    try {
        val aTraiter = plan.nouvelles.map { it.tmdbId to it.titre } + plan.completees.map { it.tmdbId to it.titre }
        progression("recherche des séries dans le catalogue…")
        val ids = HashMap(actions.idsCatalogue(aTraiter.map { it.first }))
        val manquantes = aTraiter.filter { it.first !in ids }
        manquantes.forEachIndexed { i, (tmdbId, titre) ->
            progression("fiches TMDB ${i + 1} / ${manquantes.size} (« $titre »)…")
            try {
                ids[tmdbId] = actions.importerSerie(tmdbId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                introuvables += titre
            }
        }

        if (mode == ModeImport.REMPLACEMENT) {
            progression("retrait de la liste actuelle…")
            actions.viderListe()
            listeVidee = true
        }

        val nouvelles = plan.nouvelles.filter { it.tmdbId in ids }
        val completees = plan.completees.filter { it.tmdbId in ids }
        val date = maintenant()
        progression("ajout des séries…")
        actions.suivreSeriesImportees(
            nouvelles.map { LigneSuivi(ids.getValue(it.tmdbId), it.categorieManuelle, it.ajouteLe ?: date) },
        )
        progression("épisodes cochés…")
        // Comme sur le site : date d'origine, sinon date d'ajout de la série,
        // sinon date du jour (une série complétée n'a pas de date d'ajout ici).
        val episodes = nouvelles.flatMap { s ->
            s.episodes.map { LigneEpisode(ids.getValue(s.tmdbId), it.saison, it.episode, it.vuLe ?: s.ajouteLe ?: date) }
        } + completees.flatMap { s ->
            s.episodes.map { LigneEpisode(ids.getValue(s.tmdbId), it.saison, it.episode, it.vuLe ?: date) }
        }
        actions.cocherEpisodesImportes(episodes)
        progression("statuts…")
        val statuts = nouvelles.mapNotNull { s -> s.statutManuel?.let { ids.getValue(s.tmdbId) to it } } +
            // Ajout : une série déjà « Plus tard » y reste, même si on lui a coché des épisodes.
            completees.filter { it.statutActuel == "plus_tard" }.map { ids.getValue(it.tmdbId) to "plus_tard" }
        for ((serieId, statut) in statuts) actions.changerStatut(serieId, statut)

        return BilanImport(nouvelles.size, completees.size, episodes.size, introuvables)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ImportInterrompu(listeVidee, e)
    }
}
