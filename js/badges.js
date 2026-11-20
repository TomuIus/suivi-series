// Badges, rang et annonces (décision 43) : fonctions de présentation.
//
// Les badges, le niveau et le rang sont CALCULÉS PAR LA BASE (vue
// « ma_progression », fichier supabase/migrations/20261120010000_progression.sql) :
// ce fichier ne fait que les mettre en forme pour l'affichage, et retenir
// sur l'appareil ce qui a déjà été annoncé. Il ne parle ni à Supabase ni à
// Vue : on peut le tester seul (sous Node).
//
// Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).

// Noms affichés des paliers, du plus bas au plus haut.
export const PALIERS = ['bronze', 'argent', 'or', 'platine'];
const LIBELLES_PALIER = { bronze: 'Bronze', argent: 'Argent', or: 'Or', platine: 'Platine' };

export function libellePalier(palier) {
  return LIBELLES_PALIER[palier] ?? '';
}

// Nom complet d'un badge, pour les annonces : « Temps passé (or) »,
// « Longue haleine ».
export function nomComplet(badge) {
  return badge.palier ? `${badge.nom} (${libellePalier(badge.palier).toLowerCase()})` : badge.nom;
}

// « 1 000 » plutôt que « 1000 ».
function nombre(n) {
  return Number(n).toLocaleString('fr-FR');
}

// Avancement d'un badge pas encore obtenu : « 312 / 500 h », « 3 / 5 »,
// « 87 / 100 épisodes »… (les minutes sont arrondies à l'heure inférieure,
// pour ne jamais afficher « 500 / 500 h » avant d'y être vraiment).
export function texteAvancement(badge) {
  const { valeur, seuil, unite } = badge;
  switch (unite) {
    case 'minutes':
      return `${nombre(Math.floor(valeur / 60))} / ${nombre(seuil / 60)} h`;
    case 'episodes':
      return `${nombre(valeur)} / ${nombre(seuil)} épisode${seuil > 1 ? 's' : ''}`;
    case 'saisons':
      return `${nombre(valeur)} / ${nombre(seuil)} saisons`;
    default: // 'titres', 'mondes'
      return `${nombre(valeur)} / ${nombre(seuil)}`;
  }
}

// Nombre de badges obtenus sur le total, chaque palier comptant pour un
// badge (« 5 badges obtenus sur 18 »), avec le pourcentage de la barre
// (arrondi vers le bas : 100 % seulement quand tout est obtenu).
export function compteBadges(badges) {
  const liste = badges ?? [];
  const obtenus = liste.filter((b) => b.obtenu).length;
  const total = liste.length;
  return { obtenus, total, pourcentage: total > 0 ? Math.floor((100 * obtenus) / total) : 0 };
}

// Badges montrés sur le Profil (décision 43) : seulement ceux obtenus ;
// pour une famille à paliers, seulement le palier le plus haut obtenu.
// L'ordre reste celui de la base (familles, puis défis).
export function badgesObtenus(badges) {
  const plusHaut = new Map(); // famille → badge du palier le plus haut obtenu
  for (const b of badges ?? []) {
    if (!b.obtenu || !b.palier) continue;
    const actuel = plusHaut.get(b.famille);
    if (!actuel || PALIERS.indexOf(b.palier) > PALIERS.indexOf(actuel.palier)) plusHaut.set(b.famille, b);
  }
  return (badges ?? []).filter((b) => b.obtenu && (!b.palier || plusHaut.get(b.famille) === b));
}

// Page « Tous les badges » (décision 48, option 2) : une tuile par famille à
// paliers (au lieu d'une par palier), puis les défis un par un.
// Pour chaque famille :
//   palier     : palier le plus haut obtenu (null si aucun : pastille grise) ;
//   paliers    : les 4 paliers dans l'ordre, obtenus ou non (petits ronds) ;
//   prochain   : premier palier pas encore obtenu (null quand tout est obtenu) ;
//   pourcentage: avancement vers ce palier (barre), arrondi vers le bas ;
//   texte      : « 312 / 500 h pour Or », ou « Tous les paliers obtenus ».
// Le nom d'une famille est celui de ses badges ; l'ordre est celui de la base.
export function pageBadges(badges) {
  const familles = [];
  const defis = [];
  for (const b of badges ?? []) {
    if (!b.palier) {
      defis.push(b);
      continue;
    }
    let famille = familles.find((f) => f.cle === b.famille);
    if (!famille) {
      famille = { cle: b.famille, nom: b.nom, icone: b.icone, membres: [] };
      familles.push(famille);
    }
    famille.membres.push(b);
  }
  return {
    familles: familles.map(({ membres, ...f }) => {
      const tries = [...membres].sort((a, b) => PALIERS.indexOf(a.palier) - PALIERS.indexOf(b.palier));
      const obtenus = tries.filter((b) => b.obtenu);
      const prochain = tries.find((b) => !b.obtenu) ?? null;
      return {
        ...f,
        palier: obtenus.length ? obtenus[obtenus.length - 1].palier : null,
        nbObtenus: obtenus.length,
        nbPaliers: tries.length,
        paliers: tries.map((b) => ({ palier: b.palier, obtenu: b.obtenu })),
        prochain,
        pourcentage: prochain ? avancement(prochain) : 100,
        texte: prochain
          ? `${texteAvancement(prochain)} pour ${libellePalier(prochain.palier)}`
          : 'Tous les paliers obtenus',
      };
    }),
    defis,
  };
}

// Pourcentage d'avancement d'un badge (0 à 100, arrondi vers le bas).
function avancement(badge) {
  if (!(badge.seuil > 0)) return 0;
  return Math.min(100, Math.max(0, Math.floor((100 * badge.valeur) / badge.seuil)));
}

// Pourcentage de la barre vers le niveau suivant (arrondi vers le bas).
export function pourcentageNiveau(p) {
  const etendue = p.points_niveau_suivant - p.points_niveau;
  if (!(etendue > 0)) return 0;
  return Math.min(100, Math.max(0, Math.floor((100 * (p.points - p.points_niveau)) / etendue)));
}

// --- Annonces des nouveaux badges et rangs (décision 43, option 2) ---
// Ce qui a déjà été annoncé est retenu dans le stockage local du navigateur
// (propre à chaque appareil), sous une clé propre à chaque compte.
// La première fois sur un appareil, rien n'est annoncé : on retient
// seulement l'état du moment (sinon tous les badges déjà gagnés seraient
// annoncés d'un coup).

const PREFIXE_CLE = 'suivi-series.annonces.';

export function cleAnnonces(utilisateurId) {
  return PREFIXE_CLE + utilisateurId;
}

// Compare la progression lue dans la base avec la mémoire de l'appareil.
// memoire : { rang: numéro, badges: [codes] } ou null (première fois).
// Renvoie { badges: [badges à annoncer], rang: nom du rang à annoncer ou
// null, memoire: nouvelle mémoire à enregistrer }.
// La mémoire suit l'état actuel : un badge perdu (épisode décoché) en
// sort, et sera donc annoncé de nouveau s'il est regagné (défaut accepté).
export function nouveautes(progression, memoire) {
  const obtenus = (progression.badges ?? []).filter((b) => b.obtenu);
  const nouvelleMemoire = { rang: progression.rang_numero, badges: obtenus.map((b) => b.code) };
  if (!memoire || !Array.isArray(memoire.badges) || typeof memoire.rang !== 'number') {
    return { badges: [], rang: null, memoire: nouvelleMemoire };
  }
  const dejaVus = new Set(memoire.badges);
  return {
    badges: obtenus.filter((b) => !dejaVus.has(b.code)),
    rang: progression.rang_numero > memoire.rang ? progression.rang : null,
    memoire: nouvelleMemoire,
  };
}

// Texte de l'annonce, ou '' s'il n'y a rien à annoncer.
// Ex. : « Nouveau rang : As de la télécommande. Nouveau badge : Longue haleine. »
export function texteAnnonce({ badges, rang }) {
  const morceaux = [];
  if (rang) morceaux.push(`Nouveau rang : ${rang}.`);
  if (badges.length === 1) morceaux.push(`Nouveau badge : ${nomComplet(badges[0])}.`);
  if (badges.length > 1) morceaux.push(`Nouveaux badges : ${badges.map(nomComplet).join(', ')}.`);
  return morceaux.join(' ');
}

// Lecture et écriture de la mémoire ; en cas de stockage indisponible
// (navigation privée…), rien n'est annoncé et rien ne casse.
export function lireMemoire(stockage, utilisateurId) {
  try {
    const texte = stockage.getItem(cleAnnonces(utilisateurId));
    return texte ? JSON.parse(texte) : null;
  } catch {
    return null;
  }
}

export function enregistrerMemoire(stockage, utilisateurId, memoire) {
  try {
    stockage.setItem(cleAnnonces(utilisateurId), JSON.stringify(memoire));
    return true;
  } catch {
    return false;
  }
}
