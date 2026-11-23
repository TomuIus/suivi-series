package io.github.tomuius.canapilou.ui.profil

import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.Progression
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Règles d'affichage du Profil et de la page « Tous les badges », sans
// affichage (testables seules), recopiées du site : badgesObtenus,
// libellePalier, pourcentageNiveau, pageBadges, compteBadges et
// texteAvancement de js/badges.js, nombre de js/app.js, textes des pages
// « profil » et « badges » de index.html ; et les annonces des nouveaux
// badges et rangs (nomComplet, nouveautes, texteAnnonce et lireMemoire de
// js/badges.js).
// Les calculs eux-mêmes (points, niveau, rang, badges) sont faits par la base.

/** Paliers des badges, du plus bas au plus haut. */
val PALIERS = listOf("bronze", "argent", "or", "platine")

private val LIBELLES_PALIER = mapOf("bronze" to "Bronze", "argent" to "Argent", "or" to "Or", "platine" to "Platine")

/** « Bronze », « Argent », « Or », « Platine » ; vide pour un palier inconnu. */
fun libellePalier(palier: String?): String = LIBELLES_PALIER[palier] ?: ""

/** Ligne sous le nom d'un badge : son palier, ou « Défi ». */
fun sousTitreBadge(badge: Badge): String =
    if (badge.palier.isNullOrEmpty()) "Défi" else libellePalier(badge.palier)

/**
 * Badges montrés sur le Profil (décision 43) : seulement ceux obtenus ; pour
 * une famille à paliers, seulement le palier le plus haut obtenu. L'ordre
 * reste celui de la base (familles, puis défis).
 */
fun badgesObtenus(badges: List<Badge>?): List<Badge> {
    val liste = badges.orEmpty()
    val plusHaut = mutableMapOf<String, Badge>() // famille → badge du palier le plus haut obtenu
    for (b in liste) {
        if (!b.obtenu || b.palier.isNullOrEmpty()) continue
        val actuel = plusHaut[b.famille]
        if (actuel == null || PALIERS.indexOf(b.palier) > PALIERS.indexOf(actuel.palier)) plusHaut[b.famille] = b
    }
    // « === » : le badge lui-même, comme sur le site (pas un autre badge identique).
    return liste.filter { it.obtenu && (it.palier.isNullOrEmpty() || plusHaut[it.famille] === it) }
}

/** Pourcentage de la barre vers le niveau suivant (0 à 100, arrondi vers le bas). */
fun pourcentageNiveau(p: Progression): Int {
    val etendue = p.pointsNiveauSuivant - p.pointsNiveau
    if (etendue <= 0) return 0
    val fait = p.points - p.pointsNiveau
    if (fait <= 0) return 0
    return minOf(100L, 100 * fait / etendue).toInt()
}

/**
 * « 1 000 » plutôt que « 1000 », comme toLocaleString('fr-FR') sur le site :
 * chiffres groupés par trois, séparés par une espace fine insécable (U+202F,
 * vérifié sous Node). Écrit à la main pour donner le même résultat sur tous
 * les téléphones (les données de langue d'Android peuvent varier).
 */
fun nombre(n: Long): String {
    val chiffres = kotlin.math.abs(n).toString()
    val groupes = chiffres.reversed().chunked(3).joinToString(" ").reversed()
    return if (n < 0) "-$groupes" else groupes
}

/** « Niveau 12 · As de la télécommande ». */
fun titreNiveau(p: Progression): String = "Niveau ${p.niveau} · ${p.rang}"

/** « 23 points · encore 7 pour le niveau 3 » (« 1 point », « 0 point »). */
fun textePoints(p: Progression): String {
    val manquants = p.pointsNiveauSuivant - p.points
    val s = if (p.points > 1) "s" else ""
    return "${nombre(p.points)} point$s · encore ${nombre(manquants)} pour le niveau ${p.niveau + 1}"
}

/** « Prochain rang au niveau 5 », ou null au dernier rang (nom du rang caché, décision 48). */
fun texteProchainRang(p: Progression): String? =
    p.niveauRangSuivant?.takeIf { it != 0 }?.let { "Prochain rang au niveau $it" }

// --- Page « Tous les badges » (décision 48) ---

/** Un palier d'une famille : obtenu (rond en couleur) ou non (rond en pointillé). */
data class PalierFamille(val palier: String, val obtenu: Boolean)

/**
 * Tuile d'une famille à paliers. [palier] : palier le plus haut obtenu (null
 * si aucun : pastille grise) ; [prochain] : premier palier pas encore obtenu
 * (null quand tout est obtenu) ; [pourcentage] : avancement vers lui (barre) ;
 * [texte] : « 312 / 500 h pour Or », ou « Tous les paliers obtenus ».
 */
data class FamilleBadges(
    val cle: String,
    val nom: String,
    val icone: String,
    val palier: String?,
    val nbObtenus: Int,
    val nbPaliers: Int,
    val paliers: List<PalierFamille>,
    val prochain: Badge?,
    val pourcentage: Int,
    val texte: String,
)

/** Contenu de la page : une tuile par famille à paliers, puis les défis un par un. */
data class PageBadges(val familles: List<FamilleBadges>, val defis: List<Badge>)

/** « 5 badges obtenus sur 18 » : chaque palier compte pour un badge ; pourcentage de la barre. */
data class CompteBadges(val obtenus: Int, val total: Int, val pourcentage: Int)

/**
 * Regroupe les badges pour la page « Tous les badges », comme pageBadges sur
 * le site : familles dans l'ordre de la base, paliers du plus bas au plus haut
 * dans chaque famille (nom et image de la famille : ceux de son premier badge).
 */
fun pageBadges(badges: List<Badge>?): PageBadges {
    val membres = linkedMapOf<String, MutableList<Badge>>() // garde l'ordre d'arrivée
    val defis = mutableListOf<Badge>()
    for (b in badges.orEmpty()) {
        if (b.palier.isNullOrEmpty()) defis += b else membres.getOrPut(b.famille) { mutableListOf() } += b
    }
    val familles = membres.map { (cle, liste) ->
        val tries = liste.sortedBy { PALIERS.indexOf(it.palier) } // tri stable, comme sur le site
        val obtenus = tries.filter { it.obtenu }
        val prochain = tries.firstOrNull { !it.obtenu }
        FamilleBadges(
            cle = cle,
            nom = liste.first().nom,
            icone = liste.first().icone,
            palier = obtenus.lastOrNull()?.palier,
            nbObtenus = obtenus.size,
            nbPaliers = tries.size,
            paliers = tries.map { PalierFamille(it.palier.orEmpty(), it.obtenu) },
            prochain = prochain,
            pourcentage = if (prochain != null) avancement(prochain) else 100,
            texte = if (prochain != null) {
                "${texteAvancement(prochain)} pour ${libellePalier(prochain.palier)}"
            } else {
                "Tous les paliers obtenus"
            },
        )
    }
    return PageBadges(familles, defis)
}

/** Avancement d'un badge (0 à 100, arrondi vers le bas). */
private fun avancement(badge: Badge): Int {
    if (badge.seuil <= 0) return 0
    if (badge.valeur <= 0) return 0
    return minOf(100L, 100 * badge.valeur / badge.seuil).toInt()
}

/** Nombre de badges obtenus sur le total (chaque palier compte), et pourcentage arrondi vers le bas. */
fun compteBadges(badges: List<Badge>?): CompteBadges {
    val liste = badges.orEmpty()
    val obtenus = liste.count { it.obtenu }
    val total = liste.size
    return CompteBadges(obtenus, total, if (total > 0) 100 * obtenus / total else 0)
}

/**
 * Avancement d'un badge pas encore obtenu : « 312 / 500 h », « 3 / 5 »,
 * « 87 / 100 épisodes », « 2 / 5 saisons ». Les minutes sont arrondies à
 * l'heure inférieure (jamais « 500 / 500 h » avant d'y être vraiment). Les
 * seuils en minutes sont supposés être des heures entières (vrai pour les 4
 * seuils actuels ; sinon le site écrirait une décimale, et l'appli arrondirait).
 */
fun texteAvancement(badge: Badge): String {
    val v = badge.valeur
    val s = badge.seuil
    return when (badge.unite) {
        "minutes" -> "${nombre(Math.floorDiv(v, 60L))} / ${nombre(s / 60)} h"
        "episodes" -> "${nombre(v)} / ${nombre(s)} épisode${if (s > 1) "s" else ""}"
        "saisons" -> "${nombre(v)} / ${nombre(s)} saisons"
        else -> "${nombre(v)} / ${nombre(s)}" // « titres », « mondes »
    }
}

/** « 5 badges obtenus sur 18 », « 1 badge obtenu sur 18 », « 0 badge obtenu sur 18 ». */
fun texteCompteBadges(c: CompteBadges): String {
    val s = if (c.obtenus > 1) "s" else ""
    return "${c.obtenus} badge$s obtenu$s sur ${c.total}"
}

/** Sous le nom d'une famille : son palier le plus haut, ou « Aucun palier ». */
fun sousTitreFamille(f: FamilleBadges): String = if (f.palier != null) libellePalier(f.palier) else "Aucun palier"

/** Ce que disent les petits ronds, pour les lecteurs d'écran : « 2 paliers obtenus sur 4. ». */
fun textePaliersObtenus(f: FamilleBadges): String {
    val s = if (f.nbObtenus > 1) "s" else ""
    return "${f.nbObtenus} palier$s obtenu$s sur ${f.nbPaliers}."
}

/** État d'un défi : « Obtenu », ou son avancement. */
fun etatDefi(badge: Badge): String = if (badge.obtenu) "Obtenu" else texteAvancement(badge)

// --- Annonces des nouveaux badges et rangs (décisions 43 et 96) ---
// Ce qui a déjà été annoncé est retenu sur le téléphone (DepotReglages),
// pour chaque compte. La première fois, rien n'est annoncé : on retient
// seulement l'état du moment (sinon tous les badges déjà gagnés seraient
// annoncés d'un coup). La mémoire suit l'état actuel : un badge perdu
// (épisode décoché) en sort, et sera donc annoncé de nouveau s'il est
// regagné (défaut accepté, comme sur le site).

/** Nom complet d'un badge, pour les annonces : « Temps passé (or) », « Longue haleine ». */
fun nomComplet(badge: Badge): String =
    if (badge.palier.isNullOrEmpty()) badge.nom else "${badge.nom} (${libellePalier(badge.palier).lowercase()})"

/**
 * Ce qu'il faut annoncer : les badges nouvellement obtenus, le nom du
 * nouveau rang (null s'il n'a pas changé), et la mémoire à enregistrer à la
 * place de l'ancienne (texte JSON, comme sur le site : {"rang":3,"badges":[…]}).
 */
data class Nouveautes(val badges: List<Badge>, val rang: String?, val memoire: String)

/** Compare la progression lue dans la base avec la mémoire enregistrée ([memoire] : null la première fois). */
fun nouveautes(progression: Progression, memoire: String?): Nouveautes {
    val obtenus = progression.badges.orEmpty().filter { it.obtenu }
    val nouvelleMemoire = buildJsonObject {
        put("rang", progression.rangNumero)
        putJsonArray("badges") { obtenus.forEach { add(it.code) } }
    }.toString()
    val ancienne = lireMemoireAnnonces(memoire) ?: return Nouveautes(emptyList(), null, nouvelleMemoire)
    val (rang, dejaVus) = ancienne
    return Nouveautes(
        badges = obtenus.filter { it.code !in dejaVus },
        rang = if (progression.rangNumero > rang) progression.rang else null,
        memoire = nouvelleMemoire,
    )
}

// Mémoire enregistrée : numéro du rang et codes des badges déjà annoncés ;
// null si elle est absente, illisible ou d'une autre forme (alors : comme la
// première fois).
private fun lireMemoireAnnonces(texte: String?): Pair<Double, Set<String>>? {
    if (texte.isNullOrEmpty()) return null
    val objet = try {
        Json.parseToJsonElement(texte) as? JsonObject
    } catch (e: Exception) {
        null
    } ?: return null
    val rang = (objet["rang"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
        ?.takeIf { !it.isNaN() } ?: return null
    val badges = objet["badges"] as? JsonArray ?: return null
    return rang to badges.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet()
}

/**
 * Texte de l'annonce, ou vide s'il n'y a rien à annoncer. Ex. : « Nouveau
 * rang : Adepte du plaid. Nouveau badge : Longue haleine. »
 */
fun texteAnnonce(n: Nouveautes): String {
    val morceaux = mutableListOf<String>()
    if (!n.rang.isNullOrEmpty()) morceaux += "Nouveau rang : ${n.rang}."
    if (n.badges.size == 1) morceaux += "Nouveau badge : ${nomComplet(n.badges[0])}."
    if (n.badges.size > 1) morceaux += "Nouveaux badges : ${n.badges.joinToString(", ") { nomComplet(it) }}."
    return morceaux.joinToString(" ")
}
