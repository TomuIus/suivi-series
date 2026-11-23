package io.github.tomuius.canapilou.ui.profil

import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.liste.FiltreCategorie
import io.github.tomuius.canapilou.ui.liste.Section
import io.github.tomuius.canapilou.ui.liste.filtrerTitres

// Statistiques du Profil (décisions 28, 31, 32, 34, 35 et 36), sans affichage
// (testables seules), recopiées du site : RAYON_ANNEAU, ECART_ANNEAU, la
// propriété « statistiques » et dureeApprox de js/app.js, textes de la page
// « profil » de index.html. Calculées à partir de la liste (vue
// « ma_liste ») : aucune requête en plus. Le filtre « Tout · Séries ·
// Animés » est celui de « Ma liste » (ui/liste/Sections.kt), mais son choix
// est propre au Profil et n'est pas retenu.

/** Un chiffre clé : « Épisodes vus », « 252 ». */
data class Chiffre(val libelle: String, val texte: String)

/** Une ligne de la légende de l'anneau : une section de « Ma liste » et son nombre de titres. */
data class LigneStatut(val section: Section, val nombre: Int)

/**
 * Une part de l'anneau, dans les unités du dessin du site (cercle de rayon
 * [RAYON_ANNEAU] dans un carré de 120) : [debut] = où la part commence, et
 * [tracee] = sa longueur tracée, comptées le long du cercle depuis le haut,
 * dans le sens des aiguilles d'une montre. [pourcentage] : part du total, arrondie.
 */
data class PartAnneau(
    val section: Section,
    val nombre: Int,
    val pourcentage: Int,
    val debut: Double,
    val tracee: Double,
)

data class Statistiques(
    val chiffres: List<Chiffre>,
    /** En cours, À jour, Terminé, Abandonné (sans « Plus tard », décision 36), y compris à 0. */
    val parStatut: List<LigneStatut>,
    /** Parts de l'anneau : seulement les statuts qui ont des titres. */
    val parts: List<PartAnneau>,
    /** Nombre au centre de l'anneau (sans « Plus tard »). */
    val total: Int,
    val plusTard: Int,
    /** Épisodes vus sans durée connue (pas comptés dans le temps passé). */
    val sansDuree: Int,
)

/** Rayon du cercle de l'anneau, dans un carré de 120 (comme le site). */
const val RAYON_ANNEAU = 44.0

/** Espace laissé entre deux parts, le long du cercle. */
const val ECART_ANNEAU = 2.0

/** Épaisseur du trait de l'anneau, dans le même carré de 120 (stroke-width du site). */
const val EPAISSEUR_ANNEAU = 18.0

/** Longueur du cercle de l'anneau. */
val CIRCONFERENCE_ANNEAU: Double = 2 * Math.PI * RAYON_ANNEAU

/** Statistiques des titres gardés par [filtre], comme la propriété « statistiques » du site. */
fun statistiques(series: List<SerieSuivie>, filtre: FiltreCategorie): Statistiques {
    val titres = filtrerTitres(series, filtre)
    fun compter(section: Section) = titres.count { it.statut in section.statuts }
    val parStatut = Section.entries.filter { it != Section.PLUS_TARD }.map { LigneStatut(it, compter(it)) }
    val plusTard = compter(Section.PLUS_TARD)
    val totalAnneau = titres.size - plusTard
    val termines = parStatut.first { it.section == Section.TERMINE }.nombre

    // Parts : les statuts à 0 n'en ont pas ; un petit écart les sépare quand
    // il y en a plusieurs.
    val avecTitres = parStatut.filter { it.nombre > 0 }
    val ecart = if (avecTitres.size > 1) ECART_ANNEAU else 0.0
    var debut = 0.0
    val parts = avecTitres.map { l ->
        val longueur = CIRCONFERENCE_ANNEAU * l.nombre / totalAnneau
        val part = PartAnneau(
            section = l.section,
            nombre = l.nombre,
            pourcentage = Math.round(100.0 * l.nombre / totalAnneau).toInt(),
            debut = debut,
            tracee = maxOf(longueur - ecart, 0.0),
        )
        debut += longueur
        part
    }

    // Épisodes spéciaux : comptés s'ils sont cochés (décision 32), mais jamais
    // pour les statuts ni la barre de progression (nb_vus les ignore).
    val episodesVus = titres.sumOf { it.nbVus.toLong() + (it.nbSpeciauxVus ?: 0) }
    val minutes = titres.sumOf { it.minutesVues ?: 0L }
    return Statistiques(
        chiffres = listOf(
            Chiffre("Épisodes vus", nombre(episodesVus)),
            Chiffre("Temps passé", dureeApprox(minutes)),
            Chiffre("Titres", nombre(titres.size.toLong())),
            Chiffre("Terminés", nombre(termines.toLong())),
        ),
        parStatut = parStatut,
        parts = parts,
        total = totalAnneau,
        plusTard = plusTard,
        sansDuree = titres.sumOf { it.nbVusSansDuree ?: 0 },
    )
}

/** Temps passé, estimé (décision 31) : « 0 h », « ≈ 45 min », « ≈ 312 h ». */
fun dureeApprox(minutes: Long?): String {
    if (minutes == null || minutes == 0L) return "0 h"
    if (minutes < 60) return "≈ $minutes min"
    return "≈ ${nombre(Math.round(minutes / 60.0))} h"
}

/** Sous le total de l'anneau : « titre » ou « titres ». */
fun uniteTotal(total: Int): String = if (total > 1) "titres" else "titre"

/** « + 3 titres en « Plus tard », hors diagramme. » (décision 36). */
fun textePlusTard(n: Int): String {
    val s = if (n > 1) "s" else ""
    return "+ ${nombre(n.toLong())} titre$s en « Plus tard », hors diagramme."
}

/** Avertissement quand des durées manquent (décision 48). */
fun texteSansDuree(n: Int): String =
    if (n > 1) {
        "${nombre(n.toLong())} épisodes vus sans durée connue ne sont pas comptés dans le temps passé."
    } else {
        "1 épisode vu sans durée connue n'est pas compté dans le temps passé."
    }
