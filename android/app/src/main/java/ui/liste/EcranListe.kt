package io.github.tomuius.canapilou.ui.liste

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.Progression
import io.github.tomuius.canapilou.donnees.ResultatTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.commun.AfficheSerie
import io.github.tomuius.canapilou.ui.commun.BarreProgression
import io.github.tomuius.canapilou.ui.commun.BarreSimple
import io.github.tomuius.canapilou.ui.commun.RangeeChoix
import io.github.tomuius.canapilou.ui.commun.DUREE_ANNULATION
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.Etiquette
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import io.github.tomuius.canapilou.ui.commun.DUREE_ANNONCE
import io.github.tomuius.canapilou.ui.fiche.libelleStatut
import io.github.tomuius.canapilou.ui.profil.pourcentageNiveau
import kotlinx.coroutines.flow.collectLatest

// Écran « Ma liste » : vos séries, rangées en sections par statut, comme sur
// le site : affiches, titre du prochain épisode, bouton « Vu » avec
// « Annuler » ; un appui sur une série ouvre sa fiche (décision 66). La liste
// tirée vers le bas, tout en haut, l'actualise (décision 104 ; « À propos » et
// « Se déconnecter » sont dans les Paramètres). Filtre « Tout · Séries · Animés » en
// haut de la liste, choix retenu sur le téléphone (décisions 23 et 88).
// Barre de recherche toujours en haut
// (décision 75) : dès qu'on tape, les résultats remplacent la liste, d'abord
// « Dans ma liste », puis « Sur TMDB » avec « Ajouter » et « Plus tard »
// (décisions 44 et 76).

// PullToRefreshBox est marqué « expérimental » dans certaines versions de
// Material 3 : on accepte ce statut ici (« OptIn »).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranListe(
    onOuvrirSerie: (Long) -> Unit,
    // Appui sur le nom et le niveau de l'en-tête (décision 82).
    onProfil: () -> Unit,
    // Aperçu d'une série de TMDB qui n'est pas dans la liste (identifiant TMDB).
    onApercu: (Int) -> Unit,
    // « Voir » d'une annonce de nouveau badge ou rang : page « Tous les badges ».
    onTousLesBadges: () -> Unit,
    // Message à afficher en arrivant (série retirée depuis sa fiche), puis oublié.
    messageEnAttente: String?,
    onMessagePris: () -> Unit,
    // Un ViewModel propre à cet écran (Navigation 3 le garde tant que l'écran
    // est dans l'historique), créé avec les dépôts du Conteneur.
    vm: ListeViewModel = viewModel {
        ListeViewModel(Conteneur.depotListe, Conteneur.depotCompte, Conteneur.depotTmdb, Conteneur.depotReglages)
    },
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val titres by vm.titres.collectAsStateWithLifecycle()
    val vusEnCours by vm.vusEnCours.collectAsStateWithLifecycle()
    val rechercheTmdb by vm.rechercheTmdb.collectAsStateWithLifecycle()
    val ajoutsEnCours by vm.ajoutsEnCours.collectAsStateWithLifecycle()
    val progression by vm.progression.collectAsStateWithLifecycle()
    val filtre by vm.filtre.collectAsStateWithLifecycle()
    val nom by vm.nom.collectAsStateWithLifecycle()
    val annonce by vm.annonce.collectAsStateWithLifecycle()
    val actualisation by vm.actualisation.collectAsStateWithLifecycle()
    val modeRecherche = vm.recherche.isNotBlank()

    // Position de la liste et sections dépliées, gardées ici (et non dans la
    // liste elle-même) pour les retrouver après une recherche. Mémorisées même
    // si Android recrée l'écran ; « key » donne à chaque section sa case.
    val defilementListe = rememberLazyListState()
    val ouvertes: Map<Section, MutableState<Boolean>> = Section.entries.associateWith { section ->
        key(section.name) { rememberSaveable { mutableStateOf(!section.replieeParDefaut) } }
    }

    // Pendant une recherche, le retour du téléphone l'efface (comme Échap
    // sur le site) au lieu de quitter l'appli.
    BackHandler(enabled = modeRecherche) { vm.effacerRecherche() }

    // Messages en bas de l'écran (« snackbar »). « collectLatest » : un
    // nouveau message remplace aussitôt le précédent, comme sur le site.
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    // Vrai pendant qu'un de ces messages est affiché : l'annonce attend.
    var messageEnCours by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.messages.collectLatest { m ->
            messageEnCours = true
            try {
                val resultat = if (m.annulation != null) {
                    etatMessages.afficherPendant(m.texte, DUREE_ANNULATION, accessibilite, action = "Annuler")
                } else {
                    etatMessages.afficherPendant(m.texte, DUREE_MESSAGE, accessibilite)
                }
                if (resultat == SnackbarResult.ActionPerformed && m.annulation != null) vm.annuler(m.annulation)
            } finally {
                messageEnCours = false
            }
        }
    }

    // Annonce d'un nouveau badge ou d'un nouveau rang (décision 96) : au
    // même endroit, après le message en cours, 8 s, « Voir » ouvre « Tous les
    // badges ». Interrompue par un autre message (ou si l'on quitte « Ma
    // liste »), elle revient ensuite : elle n'est oubliée qu'une fois montrée
    // jusqu'au bout, fermée d'un geste, ou après « Voir ».
    LaunchedEffect(annonce, messageEnCours) {
        val texte = annonce
        if (texte != null && !messageEnCours) {
            val resultat = etatMessages.afficherPendant(texte, DUREE_ANNONCE, accessibilite, action = "Voir")
            vm.annonceAffichee(texte)
            if (resultat == SnackbarResult.ActionPerformed) onTousLesBadges()
        }
    }

    // De retour d'une fiche où l'on a coché des épisodes : la liste se relit
    // (sans l'indicateur d'attente), comme le site au retour sur « Ma liste ».
    LaunchedEffect(vm) { vm.relireSiPerimee() }
    LaunchedEffect(messageEnAttente) {
        if (messageEnAttente != null) {
            vm.annoncer(messageEnAttente)
            onMessagePris()
        }
    }

    Scaffold(
        topBar = {
            EnTete(nom = nom, progression = progression, onProfil = onProfil)
        },
        snackbarHost = { SnackbarHost(etatMessages) },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize(),
        ) {
            BarreRecherche(
                texte = vm.recherche,
                onChange = vm::changerRecherche,
                onRechercher = vm::lancerRecherche,
                onEffacer = vm::effacerRecherche,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (modeRecherche) {
                    ResultatsRecherche(
                        texte = vm.recherche,
                        series = (etat as? EtatListe.Pret)?.series.orEmpty(),
                        rechercheTmdb = rechercheTmdb,
                        ajoutsEnCours = ajoutsEnCours,
                        titres = titres,
                        vusEnCours = vusEnCours,
                        onVu = vm::cocher,
                        onOuvrir = onOuvrirSerie,
                        onApercu = onApercu,
                        onAjouter = vm::ajouter,
                    )
                } else PullToRefreshBox(
                    // « Actualiser » (décision 104) : tout en haut de la liste, on la
                    // tire vers le bas ; l'indicateur tourne pendant la relecture.
                    // Pas pendant une recherche (résultats à la place de la liste).
                    isRefreshing = actualisation,
                    onRefresh = vm::actualiser,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (val e = etat) {
                        EtatListe.Chargement ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center))

                        is EtatListe.Erreur -> ZoneTirable {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(e.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                Button(onClick = { vm.charger() }) { Text("Réessayer") }
                            }
                        }

                        is EtatListe.Pret ->
                            if (e.total == 0) {
                                ZoneTirable {
                                    Text(
                                        "Votre liste est vide pour l'instant.",
                                        modifier = Modifier.padding(24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                ListeEnSections(
                                    e.series, filtre, titres, vusEnCours, ouvertes, defilementListe,
                                    onFiltre = vm::choisirFiltre, onVu = vm::cocher, onOuvrir = onOuvrirSerie,
                                )
                            }
                    }
                }
            }
        }
    }
}

// Zone qu'on peut tirer vers le bas pour actualiser même sans liste (erreur,
// liste vide) : le glissement de Material 3 ne réagit qu'à un contenu qui
// défile. Une liste d'un seul élément, de la taille de l'écran, contenu centré.
@Composable
private fun ZoneTirable(contenu: @Composable BoxScope.() -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
                content = contenu,
            )
        }
    }
}

// Barre de recherche : loupe, texte, croix pour effacer. La touche
// « Rechercher » du clavier lance la recherche tout de suite (Entrée sur le site).
@Composable
private fun BarreRecherche(
    texte: String,
    onChange: (String) -> Unit,
    onRechercher: () -> Unit,
    onEffacer: () -> Unit,
) {
    val clavier = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = texte,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        placeholder = { Text("Rechercher une série ou un animé…") },
        leadingIcon = { Icon(painterResource(R.drawable.loupe), contentDescription = null) },
        trailingIcon = if (texte.isEmpty()) null else {
            {
                IconButton(onClick = onEffacer) {
                    Icon(painterResource(R.drawable.croix), contentDescription = "Effacer la recherche")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onRechercher()
            clavier?.hide()
        }),
    )
}

// Résultats de la recherche, à la place de la liste : d'abord vos séries qui
// correspondent (mêmes cartes que dans la liste, avec leur statut), puis les
// résultats de TMDB sans celles-ci (recherche commune, décision 44).
@Composable
private fun ResultatsRecherche(
    texte: String,
    series: List<SerieSuivie>,
    rechercheTmdb: RechercheTmdb,
    ajoutsEnCours: Map<Int, String>,
    titres: Map<CleSaison, Map<Int, String?>>,
    vusEnCours: Set<Long>,
    onVu: (SerieSuivie) -> Unit,
    onOuvrir: (Long) -> Unit,
    onApercu: (Int) -> Unit,
    onAjouter: (ResultatTmdb, Boolean) -> Unit,
) {
    val tropCourte = texte.trim().length < LONGUEUR_MIN
    val dansListe = remember(series, texte) { if (tropCourte) emptyList() else chercherDansListe(series, texte) }
    val autres = remember(rechercheTmdb, dansListe) { autresResultats(rechercheTmdb.resultats, dansListe) }
    // Séries suivies, par identifiant TMDB : mention « Dans ma liste » et
    // ouverture de leur fiche depuis les résultats TMDB.
    val suivies = remember(series) { series.associateBy { it.tmdbId } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tropCourte) {
            item(key = "court") { TexteInfo("Tapez au moins 2 caractères.") }
            return@LazyColumn
        }
        if (dansListe.isNotEmpty()) {
            item(key = "titre-liste") { TitreBloc("Dans ma liste (${dansListe.size})") }
            items(dansListe, key = { "liste-${it.serieId}" }) { serie ->
                CarteSerie(
                    s = serie,
                    titreProchain = titreProchain(serie, titres),
                    vuEnCours = serie.serieId in vusEnCours,
                    onVu = { onVu(serie) },
                    onOuvrir = { onOuvrir(serie.serieId) },
                    statutVisible = true,
                )
            }
            item(key = "titre-tmdb") { TitreBloc("Sur TMDB") }
        }
        val faite = rechercheTmdb.faite
        val info = when {
            rechercheTmdb.enCours -> "Recherche…"
            faite.isNotEmpty() && rechercheTmdb.resultats.isEmpty() -> "Aucun résultat pour « $faite »."
            faite.isNotEmpty() && autres.isEmpty() -> "Rien d'autre sur TMDB pour « $faite »."
            else -> null
        }
        if (info != null) item(key = "info") { TexteInfo(info) }
        items(autres, key = { "tmdb-${it.tmdbId}" }) { r ->
            CarteResultat(r, suivies[r.tmdbId], ajoutsEnCours[r.tmdbId], onAjouter, onOuvrir, onApercu)
        }
    }
}

@Composable
private fun TitreBloc(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TexteInfo(texte: String) {
    Text(texte, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Un résultat TMDB : affiche, titre, année · pays · titre original, début du
// résumé ; puis « Ajouter » et « Plus tard » (« Ajout… » pendant l'envoi),
// ou « Dans ma liste » si la série est déjà suivie. Un appui sur la carte
// ouvre la fiche d'une série suivie, sinon son aperçu (décisions 74 et 77).
@Composable
private fun CarteResultat(
    r: ResultatTmdb,
    suivie: SerieSuivie?,
    ajoutEnCours: String?,
    onAjouter: (ResultatTmdb, Boolean) -> Unit,
    onOuvrir: (Long) -> Unit,
    onApercu: (Int) -> Unit,
) {
    val contenu: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 64 × 96 dp, comme les 64 × 96 pixels du site.
            AfficheSerie(r.affiche, 64.dp, 96.dp, "w154")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        r.titre,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (r.anime) Etiquette("Animé")
                }
                val details = detailsResultat(r)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!r.resume.isNullOrBlank()) {
                    // Début du résumé seulement (2 lignes), comme sur le site.
                    Text(r.resume, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (suivie != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.coche),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(mentionDansListe(suivie.statut), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        val libelleAjouter = "Ajouter ${r.titre} à ma liste"
                        Button(
                            onClick = { onAjouter(r, false) },
                            enabled = ajoutEnCours == null,
                            modifier = Modifier.semantics { contentDescription = libelleAjouter },
                        ) {
                            Text(
                                if (ajoutEnCours == "liste") "Ajout…" else "Ajouter",
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                        val libellePlusTard = "Mettre ${r.titre} dans Plus tard"
                        OutlinedButton(
                            onClick = { onAjouter(r, true) },
                            enabled = ajoutEnCours == null,
                            modifier = Modifier.semantics { contentDescription = libellePlusTard },
                        ) {
                            Text(
                                if (ajoutEnCours == "plus_tard") "Ajout…" else "Plus tard",
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
    }
    OutlinedCard(
        onClick = { if (suivie != null) onOuvrir(suivie.serieId) else onApercu(r.tmdbId) },
        modifier = Modifier.fillMaxWidth(),
    ) { contenu() }
}

// Haut de l'écran : le nom de l'appli, puis votre nom et votre niveau (un
// appui ouvre le Profil, décision 82). Plus de menu « ⋮ » (décisions 103 et
// 104) : « Actualiser » se fait en tirant la liste vers le bas, « À propos » et
// « Se déconnecter » sont dans les Paramètres.
@Composable
private fun EnTete(
    nom: String,
    progression: Progression?,
    onProfil: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // sous la barre d'état du téléphone
                // À droite, 8 dp + les 8 dp du bloc du compte : 16 dp, comme à gauche.
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canapilou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Le reste de la largeur, aligné à droite ; un nom trop long finit par « … ».
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                BlocCompte(nom, progression, onProfil)
            }
        }
    }
}

// Nom (sinon e-mail), puis « Niveau N » et sa petite barre, de la largeur du
// texte, centrés sous le nom (comme l'en-tête du site, décisions 34 et 36).
// Sans progression (pas encore lue, ou illisible) : le nom seul. La barre est
// décorative pour les lecteurs d'écran ; le détail est sur le Profil.
@Composable
private fun BlocCompte(nom: String, progression: Progression?, onProfil: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Ouvrir le profil", role = Role.Button, onClick = onProfil)
            .heightIn(min = 48.dp) // zone d'appui assez grande
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(nom, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progression != null) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max) // largeur du texte « Niveau N »
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Niveau ${progression.niveau}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                BarreSimple(pourcentageNiveau(progression), 5.dp)
            }
        }
    }
}

// La liste : le filtre « Tout (9) · Séries (5) · Animés (4) », puis les
// sections de la liste filtrée (les sections vides ne sont pas affichées).
// L'étiquette « Animé » n'apparaît qu'avec « Tout » (comme sur le site).
@Composable
private fun ListeEnSections(
    series: List<SerieSuivie>,
    filtre: FiltreCategorie,
    titres: Map<CleSaison, Map<Int, String?>>,
    vusEnCours: Set<Long>,
    ouvertes: Map<Section, MutableState<Boolean>>,
    defilement: LazyListState,
    onFiltre: (FiltreCategorie) -> Unit,
    onVu: (SerieSuivie) -> Unit,
    onOuvrir: (Long) -> Unit,
) {
    val comptes = remember(series) { comptesFiltres(series) }
    val sections = remember(series, filtre) { regrouperParSection(filtrerTitres(series, filtre)) }
    LazyColumn(
        state = defilement,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filtre") {
            RangeeChoix(FiltreCategorie.entries, filtre, { "${it.libelle} (${comptes[it] ?: 0})" }, onFiltre)
        }
        if (sections.values.all { it.isEmpty() }) {
            item(key = "filtre-vide") {
                Text(texteFiltreVide(filtre), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (section in Section.entries) {
            val series = sections[section].orEmpty()
            if (series.isEmpty()) continue // section vide : pas affichée
            val ouverte = ouvertes.getValue(section)
            item(key = "section-${section.name}") {
                TitreSection(section.titre, series.size, ouverte.value) { ouverte.value = !ouverte.value }
            }
            if (ouverte.value) {
                items(series, key = { it.serieId }) { serie ->
                    CarteSerie(
                        s = serie,
                        titreProchain = titreProchain(serie, titres),
                        vuEnCours = serie.serieId in vusEnCours,
                        onVu = { onVu(serie) },
                        onOuvrir = { onOuvrir(serie.serieId) },
                        etiquetteAnime = filtre == FiltreCategorie.TOUT,
                    )
                }
            }
        }
    }
}

// Titre d'une section, « Terminé (3) », avec une flèche ; un appui la replie
// ou la déplie.
@Composable
private fun TitreSection(titre: String, nombre: Int, ouverte: Boolean, onBasculer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBasculer)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$titre ($nombre)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.chevron),
            contentDescription = if (ouverte) "Replier la section" else "Déplier la section",
            modifier = Modifier.rotate(if (ouverte) 180f else 0f),
        )
    }
}

// Une série de la liste : affiche, titre, progression, et bouton « Vu »
// dans la section « En cours ». Un appui sur la carte ouvre la fiche (le
// bouton « Vu » garde son propre appui).
@Composable
private fun CarteSerie(
    s: SerieSuivie,
    titreProchain: String?,
    vuEnCours: Boolean,
    onVu: () -> Unit,
    onOuvrir: () -> Unit,
    // Dans les résultats de recherche, il n'y a pas de sections : le statut
    // s'affiche alors à côté du titre.
    statutVisible: Boolean = false,
    // Étiquette « Animé » : dans la liste, seulement avec le filtre « Tout ».
    etiquetteAnime: Boolean = true,
) {
    OutlinedCard(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 46 × 69 dp, comme les 46 × 69 pixels du site ; taille TMDB w154
            // pour rester nette sur un téléphone (choix de Claude, à revoir).
            AfficheSerie(s.afficheChemin, 46.dp, 69.dp, "w154")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        s.titre,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.estAnime && etiquetteAnime) Etiquette("Animé")
                    if (statutVisible) Etiquette(libelleStatut(s.statut))
                }
                pourcentage(s)?.let { p -> BarreProgression(p) }
                Text(
                    ligneProgression(s, titreProchain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peutCocher(s)) BoutonVu(s, vuEnCours, onVu)
        }
    }
}

// Bouton « Vu » : coche dessinée + « Vu » ; « … » pendant l'envoi. Les
// lecteurs d'écran annoncent « Marquer S1·E6 de … comme vu », comme sur le site.
@Composable
private fun BoutonVu(s: SerieSuivie, enCours: Boolean, onVu: () -> Unit) {
    val saison = s.prochaineSaison ?: return
    val episode = s.prochainEpisode ?: return
    val description = "Marquer ${libelleEpisode(saison, episode)} de ${s.titre} comme vu"
    Button(
        onClick = onVu,
        enabled = !enCours,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        if (enCours) {
            Text("…", modifier = Modifier.clearAndSetSemantics {})
        } else {
            Icon(painterResource(R.drawable.coche), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Vu", modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
