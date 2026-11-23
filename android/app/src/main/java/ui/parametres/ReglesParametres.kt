package io.github.tomuius.canapilou.ui.parametres

// Règles de la page Paramètres (décisions 25, 48 et 90), sans affichage
// (testables seules), recopiées du site : LONGUEUR_MAX_NOM, THEMES,
// enregistrerNom et changerTheme de js/app.js, script du thème en tête de
// index.html (fonctions choix et changer).

/** Nom d'utilisateur : 30 caractères au plus (décision 25, à revoir après des tests). */
const val LONGUEUR_MAX_NOM = 30

/**
 * Thème de l'appli (décision 48) : Automatique (suit le téléphone), Clair ou
 * Sombre, retenu sur le téléphone. [valeur] : les mêmes mots que la clé
 * « suivi-series.theme » du site ; « Automatique » n'est pas enregistré (la
 * clé est effacée), comme sur le site.
 */
enum class ChoixTheme(val libelle: String, val valeur: String) {
    AUTO("Automatique", "auto"),
    CLAIR("Clair", "clair"),
    SOMBRE("Sombre", "sombre"),
    ;

    companion object {
        /** Thème retenu ; tout sauf « clair » et « sombre » vaut Automatique (comme choix() sur le site). */
        fun depuisValeur(valeur: String?): ChoixTheme = when (valeur) {
            "clair" -> CLAIR
            "sombre" -> SOMBRE
            else -> AUTO
        }
    }
}

/** Couleurs sombres ? Automatique suit le réglage du téléphone ([telephoneSombre]). */
fun themeSombre(choix: ChoixTheme, telephoneSombre: Boolean): Boolean = when (choix) {
    ChoixTheme.AUTO -> telephoneSombre
    ChoixTheme.CLAIR -> false
    ChoixTheme.SOMBRE -> true
}

/** Message d'erreur si le nom (déjà débarrassé de ses espaces au début et à la fin) est trop long, sinon null. */
fun erreurNom(nom: String): String? =
    if (nom.length > LONGUEUR_MAX_NOM) "Le nom doit faire au plus $LONGUEUR_MAX_NOM caractères." else null

/** Message après l'enregistrement du nom (vide : l'e-mail est de nouveau affiché). */
fun messageNom(nom: String): String =
    if (nom.isNotEmpty()) "Vous vous appelez maintenant « $nom »." else "Nom retiré : votre e-mail est de nouveau affiché."
