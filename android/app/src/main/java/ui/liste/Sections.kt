package io.github.tomuius.canapilou.ui.liste

import io.github.tomuius.canapilou.donnees.SerieSuivie
import kotlin.math.min

// Règles d'affichage de « Ma liste », sans rien dessiner : ce fichier se teste
// seul (voir app/src/test/java/ListeTest.kt). Mêmes règles que le site
// (constantes SECTIONS et FILTRES, fonctions lireFiltre, filtrerTitres,
// pourcentage, libelleEpisode, peutCocher, titreProchainListe, progression et
// « comptes » de js/app.js) : à modifier aux deux endroits.

/**
 * Sections de « Ma liste », dans l'ordre d'affichage. « À voir » (pas encore
 * commencée) est réuni avec « En cours » (décision 27). Les trois dernières
 * sont repliées au départ.
 */
enum class Section(val titre: String, val statuts: Set<String>, val replieeParDefaut: Boolean) {
    EN_COURS("En cours", setOf("en_cours", "a_voir"), false),
    A_JOUR("À jour", setOf("a_jour"), false),
    TERMINE("Terminé", setOf("termine"), true),
    ABANDONNE("Abandonné", setOf("abandonne"), true),
    PLUS_TARD("Plus tard", setOf("plus_tard"), true),
}

/**
 * Filtre « Tout · Séries · Animés » (décision 23), dans « Ma liste » (choix
 * retenu sur le téléphone, décision 88) et dans les statistiques du Profil
 * (choix non retenu). [valeur] : ce qui est retenu, les mêmes mots que la
 * clé « suivi-series.filtre » du site (FILTRES de js/app.js).
 */
enum class FiltreCategorie(val libelle: String, val valeur: String) {
    TOUT("Tout", "tout"),
    SERIES("Séries", "series"),
    ANIMES("Animés", "animes"),
    ;

    companion object {
        /** Filtre retenu ; une valeur absente ou inconnue vaut « Tout » (comme lireFiltre du site). */
        fun depuisValeur(valeur: String?): FiltreCategorie = entries.firstOrNull { it.valeur == valeur } ?: TOUT
    }
}

/** Titres gardés par le filtre (classement série / animé de la vue, correction comprise). */
fun filtrerTitres(series: List<SerieSuivie>, filtre: FiltreCategorie): List<SerieSuivie> = when (filtre) {
    FiltreCategorie.ANIMES -> series.filter { it.estAnime }
    FiltreCategorie.SERIES -> series.filter { !it.estAnime }
    FiltreCategorie.TOUT -> series
}

/** Nombre de titres de chaque filtre, affiché sur ses boutons (« Animés (4) »), comme « comptes » sur le site. */
fun comptesFiltres(series: List<SerieSuivie>): Map<FiltreCategorie, Int> {
    val animes = series.count { it.estAnime }
    return mapOf(
        FiltreCategorie.TOUT to series.size,
        FiltreCategorie.SERIES to series.size - animes,
        FiltreCategorie.ANIMES to animes,
    )
}

/** Message quand le filtre ne garde aucun titre (la liste n'étant pas vide). */
fun texteFiltreVide(filtre: FiltreCategorie): String =
    if (filtre == FiltreCategorie.ANIMES) "Aucun animé dans votre liste." else "Aucune série dans votre liste."

/** Range les séries par section, en gardant leur ordre (activité la plus récente d'abord). */
fun regrouperParSection(series: List<SerieSuivie>): Map<Section, List<SerieSuivie>> =
    Section.entries.associateWith { section -> series.filter { it.statut in section.statuts } }

/**
 * Pourcentage d'épisodes vus parmi les épisodes déjà sortis (spéciaux exclus,
 * comme dans la vue « ma_liste »). Arrondi vers le bas : 100 % seulement quand
 * tout est vu. null s'il n'y a encore aucun épisode sorti.
 */
fun pourcentage(s: SerieSuivie): Int? = pourcentage(s.nbVus, s.nbDiffuses)

/** Même calcul à partir des deux compteurs (sert aussi à la fiche). */
fun pourcentage(nbVus: Int, nbDiffuses: Int): Int? {
    if (nbDiffuses <= 0) return null
    return 100 * min(nbVus, nbDiffuses) / nbDiffuses // division entière = arrondi vers le bas
}

/** « S2·E5 » (point médian, sans espaces : demande de l'utilisateur, v21). */
fun libelleEpisode(saison: Int, episode: Int): String = "S$saison·E$episode"

/**
 * Le bouton « Vu » n'apparaît que s'il y a un épisode sorti à cocher, et
 * seulement dans la section « En cours » (statuts en_cours et a_voir), comme
 * peutCocher sur le site. C'est aussi pour ces séries qu'on demande le titre
 * du prochain épisode. (Le site ne regarde que la saison : la vue donne
 * toujours la saison et l'épisode ensemble ; Kotlin demande de vérifier les deux.)
 */
fun peutCocher(s: SerieSuivie): Boolean =
    s.prochaineSaison != null && s.prochainEpisode != null && (s.statut == "en_cours" || s.statut == "a_voir")

/** Une saison d'une série chez TMDB : c'est par saison que l'on demande les titres. */
data class CleSaison(val tmdbId: Int, val saison: Int)

/**
 * Titre du prochain épisode de [s] tel que TMDB le donne, s'il est déjà connu.
 * [titres] : pour chaque saison déjà reçue, le titre de chaque épisode (par numéro).
 */
fun titreProchain(s: SerieSuivie, titres: Map<CleSaison, Map<Int, String?>>): String? {
    val saison = s.prochaineSaison ?: return null
    val episode = s.prochainEpisode ?: return null
    return titres[CleSaison(s.tmdbId, saison)]?.get(episode)
}

// Titre générique (« Épisode 6 », quand TMDB n'a pas de vrai titre) : il
// n'apporterait rien à côté de « E6 », on ne l'affiche pas (décision 38).
// Même expression que sur le site ; « É » et « é » écrits tous les deux pour ne
// pas dépendre de la gestion des majuscules accentuées.
private val TITRE_GENERIQUE = Regex("^[ÉéE]pisode\\s*\\d+$", RegexOption.IGNORE_CASE)

/** Titre à afficher pour le prochain épisode, ou null (titre absent, vide ou générique). */
fun titreAffichable(titre: String?): String? =
    if (titre.isNullOrEmpty() || TITRE_GENERIQUE.matches(titre.trim())) null else titre

/**
 * « épisode vu » ou « épisodes vus », accordé avec le nombre d'épisodes vus,
 * y compris quand un total suit : « 1 / 808 épisode vu », « 0 épisode vu »
 * (décision 70 : choix de l'utilisateur ; zéro est suivi du singulier). Même
 * règle sur le site (episodesVus dans js/app.js).
 */
fun episodesVus(nombre: Int): String = if (nombre <= 1) "épisode vu" else "épisodes vus"

/**
 * Ligne d'informations sous le titre, comme sur le site :
 * « Prochain : S1·E6 "Ste Lisa Blues" | 5 / 808 épisodes vus ».
 * [titreProchain] : titre du prochain épisode tel que TMDB le donne, s'il est
 * déjà connu (il est demandé à part, il arrive un instant après la liste).
 */
fun ligneProgression(s: SerieSuivie, titreProchain: String? = null): String {
    val compte = "${s.nbVus} / ${s.nbDiffuses} ${episodesVus(s.nbVus)}"
    return when {
        s.statut == "termine" -> "${s.nbVus} ${episodesVus(s.nbVus)}"
        s.statut == "a_jour" -> "${s.nbVus} ${episodesVus(s.nbVus)} | en attente de la suite"
        s.nbDiffuses == 0 -> "Aucun épisode diffusé pour l'instant"
        s.statut == "plus_tard" -> compte // pas de « Prochain » : série mise de côté
        s.prochaineSaison != null && s.prochainEpisode != null && s.statut != "abandonne" -> {
            val titre = titreAffichable(titreProchain)
            val episode = libelleEpisode(s.prochaineSaison, s.prochainEpisode) + (if (titre != null) " \"$titre\"" else "")
            "Prochain : $episode | $compte"
        }
        else -> compte
    }
}
