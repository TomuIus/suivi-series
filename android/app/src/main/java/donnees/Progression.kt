package io.github.tomuius.canapilou.donnees

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Points, niveau, rang et badges (décisions 33, 36 et 43), tels que la base
// les calcule dans la vue « ma_progression » (fichier
// supabase/migrations/20261120010000_progression.sql) : une seule ligne pour
// la personne connectée. Rien n'est enregistré : tout est recalculé à chaque
// lecture (décocher un épisode retire son point et peut faire perdre un badge).

/**
 * Un des 18 badges, dans l'ordre d'affichage donné par la base. [palier] :
 * « bronze », « argent », « or », « platine », ou null pour un défi.
 * [icone] : nom de l'image de Tilou (img/badges/<icone>.png sur le site,
 * décision 80). [valeur] : où en est la personne ; [seuil] : ce qu'il faut
 * atteindre ; [unite] : « minutes », « titres », « episodes », « saisons »,
 * « mondes ».
 */
@Serializable
data class Badge(
    val code: String,
    val famille: String,
    val palier: String? = null,
    val nom: String,
    val condition: String = "",
    val icone: String,
    val unite: String = "",
    val valeur: Long = 0,
    val seuil: Long = 0,
    val obtenu: Boolean = false,
)

/**
 * Une ligne de « ma_progression ». [pointsNiveau] : seuil du niveau atteint ;
 * [pointsNiveauSuivant] : seuil du niveau suivant. [rangSuivant] et
 * [niveauRangSuivant] sont vides au dernier rang (le nom du rang suivant
 * n'est pas affiché, décision 48).
 */
@Serializable
data class Progression(
    val points: Long,
    val niveau: Int,
    @SerialName("points_niveau") val pointsNiveau: Long,
    @SerialName("points_niveau_suivant") val pointsNiveauSuivant: Long,
    @SerialName("rang_numero") val rangNumero: Int,
    val rang: String,
    @SerialName("rang_suivant") val rangSuivant: String? = null,
    @SerialName("niveau_rang_suivant") val niveauRangSuivant: Int? = null,
    val badges: List<Badge>? = null,
)

// Colonnes demandées à la base : les mêmes que lireProgression sur le site.
internal val COLONNES_PROGRESSION = listOf(
    "points", "niveau", "points_niveau", "points_niveau_suivant",
    "rang_numero", "rang", "rang_suivant", "niveau_rang_suivant", "badges",
)
