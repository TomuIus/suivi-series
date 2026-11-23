package io.github.tomuius.canapilou.ui.profil

import io.github.tomuius.canapilou.donnees.Badge
import io.github.tomuius.canapilou.donnees.Progression

// Règles d'affichage du Profil, sans affichage (testables seules), recopiées
// du site : badgesObtenus, libellePalier et pourcentageNiveau de js/badges.js,
// nombre de js/app.js, textes de la page « profil » de index.html.
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
