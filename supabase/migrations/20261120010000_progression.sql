-- =====================================================================
-- Canapilou — points, niveau, rang et badges (décision 43)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents et
-- AVANT d'envoyer la v26 du site sur GitHub : la v26 lit la nouvelle
-- vue « ma_progression » ; sans elle, le niveau ne s'afficherait plus.
-- Le site actuel (v25) n'utilise pas cette vue : exécuter ce fichier
-- d'abord ne change rien au site en ligne.
--
-- Pourquoi dans la base (décision 43, option 3) : le site et la future
-- appli Android lisent la même vue ; le barème, le coût des niveaux,
-- les rangs et les seuils des badges ne sont écrits qu'ici. Rien n'est
-- enregistré : tout est recalculé à chaque lecture, à partir des
-- épisodes cochés (décocher un épisode retire son point, et peut faire
-- perdre un badge).
--
-- Pour changer une valeur plus tard : modifier ce fichier (ou en écrire
-- un nouveau qui refait les « create or replace » concernés), puis
-- l'exécuter dans Supabase ; le site et l'appli suivent tout de suite.
-- Toutes les valeurs ci-dessous sont à revoir après des tests.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Coût des niveaux (décision 36) — repris de js/app.js (v18 à v25).
--
--    Chaque niveau coûte 10 points de plus que le précédent (10, 20,
--    30…), mais jamais plus de 100. Points nécessaires, au total, pour
--    atteindre le niveau n :
--      niveau 1 = 0, niveau 2 = 10, niveau 3 = 30… niveau 11 = 550,
--      puis 100 de plus par niveau (niveau 12 = 650, niveau 13 = 750…).
--    Calcul en « bigint » (grands entiers) pour ne jamais dépasser la
--    limite des entiers ordinaires, même avec des valeurs absurdes.
-- ---------------------------------------------------------------------
create or replace function public.seuil_niveau(n integer)
returns bigint
language sql
immutable
set search_path = ''
as $$
  select case
    -- Tant que le coût augmente : 10 + 20 + … + 10 × (n − 1).
    when n - 1 <= c.niveaux_croissants
      then (c.palier * n::bigint * (n - 1)) / 2
    -- Ensuite : seuil du dernier niveau « croissant », + 100 par niveau.
    else (c.palier * c.niveaux_croissants::bigint * (c.niveaux_croissants + 1)) / 2
         + c.cout_max * (n::bigint - 1 - c.niveaux_croissants)
  end
  from (
    select
      10        as palier,              -- hausse du coût à chaque niveau
      100       as cout_max,            -- coût maximal d'un niveau
      100 / 10  as niveaux_croissants   -- = cout_max / palier
  ) c;
$$;

-- Niveau atteint avec un nombre de points : le plus grand n dont le
-- seuil ne dépasse pas les points. Recherche par dichotomie (on double
-- jusqu'à dépasser, puis on coupe en deux) : elle ne dépend que de
-- « seuil_niveau », qui reste donc le seul endroit à modifier.
create or replace function public.niveau_pour(points integer)
returns integer
language plpgsql
immutable
set search_path = ''
as $$
declare
  bas    integer := 1;  -- toujours : seuil_niveau(bas) <= points
  haut   integer := 2;  -- cherché : seuil_niveau(haut) > points
  milieu integer;
begin
  if points is null or points <= 0 then
    return 1;
  end if;
  while public.seuil_niveau(haut) <= points loop
    bas  := haut;
    haut := haut * 2;
  end loop;
  while haut - bas > 1 loop
    milieu := (bas + haut) / 2;
    if public.seuil_niveau(milieu) <= points then
      bas := milieu;
    else
      haut := milieu;
    end if;
  end loop;
  return bas;
end;
$$;


-- ---------------------------------------------------------------------
-- 2. La vue « ma_progression » : UNE ligne pour la personne connectée.
--
--    Colonnes :
--      points, niveau, points_niveau (seuil du niveau atteint),
--      points_niveau_suivant ;
--      rang_numero (1 à 11), rang (nom), rang_suivant et
--      niveau_rang_suivant (vides au dernier rang) ;
--      badges : liste des 18 badges (format JSON), dans l'ordre
--        d'affichage, chacun avec : code, famille (« temps »,
--        « series », « animes » ou « defis »), palier (« bronze »,
--        « argent », « or », « platine », vide pour un défi), nom,
--        condition (texte à afficher), icone (nom de l'icône Tabler),
--        unite (« minutes », « titres », « episodes », « saisons »,
--        « mondes »), valeur (où en est la personne), seuil, obtenu.
--
--    « security_invoker » : la vue lit « ma_liste » avec les droits de la
--    personne qui l'interroge ; chacun ne voit donc que sa propre
--    progression (règles RLS des tables).
--
--    Les totaux reprennent exactement les règles de « ma_liste » :
--      - épisodes cochés = épisodes sortis cochés (« nb_vus », spéciaux
--        exclus) + spéciaux cochés (« nb_speciaux_vus ») ;
--      - titre terminé = statut « termine », automatique ou manuel ;
--      - série ou animé = catégorie de la fiche (correction comprise) ;
--      - temps passé = « minutes_vues » (spéciaux cochés compris), comme
--        la tuile « Temps passé » du Profil.
-- ---------------------------------------------------------------------
create or replace view public.ma_progression
with (security_invoker = true)
as
with
-- Barème des points (décision 33) : 1 point par épisode coché,
-- + 20 points par titre au statut « Terminé ».
bareme (points_par_episode, bonus_titre_termine) as (
  values (1, 20)
),

-- Rangs (décision 43) : un rang commence au niveau indiqué.
rangs (numero, niveau_min, nom) as (
  values
    ( 1,   1, 'Touriste du salon'),
    ( 2,   3, 'Fan du dimanche'),
    ( 3,   5, 'Adepte du plaid'),
    ( 4,   8, 'Fidèle des coussins'),
    ( 5,  12, 'As de la télécommande'),
    ( 6,  17, 'Sentinelle du pop-corn'),
    ( 7,  25, 'Pro du marathon'),
    ( 8,  35, 'Pilier du canapé'),
    ( 9,  50, 'Institution du salon'),
    (10,  70, 'Légende du canapé'),
    (11, 100, 'Canapilou d''or')
),

-- Badges (décision 43). « mesure » dit quel total de la partie
-- « totaux » est comparé au seuil. Temps passé : seuils en minutes
-- (24 h = 1 440 min).
badges_def (ordre, code, famille, palier, nom, condition, icone, unite, mesure, seuil) as (
  values
    ( 1, 'temps_bronze',    'temps',  'bronze',  'Temps passé',      '24 h devant l''écran',     'clock',        'minutes', 'minutes_vues',      1440),
    ( 2, 'temps_argent',    'temps',  'argent',  'Temps passé',      '100 h devant l''écran',    'clock',        'minutes', 'minutes_vues',      6000),
    ( 3, 'temps_or',        'temps',  'or',      'Temps passé',      '500 h devant l''écran',    'clock',        'minutes', 'minutes_vues',     30000),
    ( 4, 'temps_platine',   'temps',  'platine', 'Temps passé',      '1 000 h devant l''écran',  'clock',        'minutes', 'minutes_vues',     60000),
    ( 5, 'series_bronze',   'series', 'bronze',  'Séries terminées', '1 série terminée',         'device-tv',    'titres',  'series_terminees',     1),
    ( 6, 'series_argent',   'series', 'argent',  'Séries terminées', '5 séries terminées',       'device-tv',    'titres',  'series_terminees',     5),
    ( 7, 'series_or',       'series', 'or',      'Séries terminées', '20 séries terminées',      'device-tv',    'titres',  'series_terminees',    20),
    ( 8, 'series_platine',  'series', 'platine', 'Séries terminées', '50 séries terminées',      'device-tv',    'titres',  'series_terminees',    50),
    ( 9, 'animes_bronze',   'animes', 'bronze',  'Animés terminés',  '1 animé terminé',          'sparkles',     'titres',  'animes_termines',      1),
    (10, 'animes_argent',   'animes', 'argent',  'Animés terminés',  '5 animés terminés',        'sparkles',     'titres',  'animes_termines',      5),
    (11, 'animes_or',       'animes', 'or',      'Animés terminés',  '20 animés terminés',       'sparkles',     'titres',  'animes_termines',     20),
    (12, 'animes_platine',  'animes', 'platine', 'Animés terminés',  '50 animés terminés',       'sparkles',     'titres',  'animes_termines',     50),
    (13, 'premier_episode', 'defis',  null,      'Premier épisode',  'Avoir coché un premier épisode',              'player-play',  'episodes', 'episodes_coches',     1),
    (14, 'longue_haleine',  'defis',  null,      'Longue haleine',   '100 épisodes vus d''un même titre',           'run',          'episodes', 'max_episodes_titre', 100),
    (15, 'increvable',      'defis',  null,      'Increvable',       '500 épisodes vus d''un même titre',           'flame',        'episodes', 'max_episodes_titre', 500),
    (16, 'grande_saga',     'defis',  null,      'Grande saga',      'Avoir terminé un titre d''au moins 5 saisons', 'books',        'saisons',  'max_saisons_termine',  5),
    (17, 'deux_mondes',     'defis',  null,      'Deux mondes',      'Avoir terminé au moins une série et un animé', 'world',        'mondes',   'mondes_termines',      2),
    (18, 'coulisses',       'defis',  null,      'Les coulisses',    '10 épisodes spéciaux vus',                    'movie',        'episodes', 'speciaux_vus',        10)
),

-- Totaux de la personne connectée (une seule ligne, même liste vide).
totaux as (
  select
    coalesce(sum(l.nb_vus + l.nb_speciaux_vus), 0)::integer                     as episodes_coches,
    (count(*) filter (where l.statut = 'termine'))::integer                     as titres_termines,
    (count(*) filter (where l.statut = 'termine' and not l.est_anime))::integer as series_terminees,
    (count(*) filter (where l.statut = 'termine' and l.est_anime))::integer     as animes_termines,
    coalesce(sum(l.minutes_vues), 0)::integer                                   as minutes_vues,
    -- Le plus d'épisodes vus sur un même titre (spéciaux exclus).
    coalesce(max(l.nb_vus), 0)::integer                                         as max_episodes_titre,
    coalesce(sum(l.nb_speciaux_vus), 0)::integer                                as speciaux_vus,
    -- Le plus de saisons (hors spéciaux, saisons non vides) d'un titre terminé.
    coalesce(max(sa.nb_saisons) filter (where l.statut = 'termine'), 0)::integer as max_saisons_termine
  from public.ma_liste l
  left join lateral (
    select count(*)::integer as nb_saisons
      from public.saisons s
     where s.serie_id = l.serie_id
       and s.numero >= 1
       and s.nb_episodes > 0
  ) sa on true
),

-- Points et niveau.
calcul as (
  select
    t.*,
    -- Une série ET un animé terminés = 2 « mondes » (défi « Deux mondes »).
    ((t.series_terminees > 0)::integer + (t.animes_termines > 0)::integer) as mondes_termines,
    (b.points_par_episode * t.episodes_coches
      + b.bonus_titre_termine * t.titres_termines)::integer               as points
  from totaux t
  cross join bareme b
)

select
  c.points,
  public.niveau_pour(c.points)                                  as niveau,
  public.seuil_niveau(public.niveau_pour(c.points))::integer     as points_niveau,
  public.seuil_niveau(public.niveau_pour(c.points) + 1)::integer as points_niveau_suivant,
  r.numero      as rang_numero,
  r.nom         as rang,
  rs.nom        as rang_suivant,
  rs.niveau_min as niveau_rang_suivant,
  (
    select jsonb_agg(
             jsonb_build_object(
               'code',      d.code,
               'famille',   d.famille,
               'palier',    d.palier,
               'nom',       d.nom,
               'condition', d.condition,
               'icone',     d.icone,
               'unite',     d.unite,
               'valeur',    v.valeur,
               'seuil',     d.seuil,
               'obtenu',    v.valeur >= d.seuil
             )
             order by d.ordre
           )
      from badges_def d
      cross join lateral (
        select case d.mesure
                 when 'minutes_vues'        then c.minutes_vues
                 when 'series_terminees'    then c.series_terminees
                 when 'animes_termines'     then c.animes_termines
                 when 'episodes_coches'     then c.episodes_coches
                 when 'max_episodes_titre'  then c.max_episodes_titre
                 when 'max_saisons_termine' then c.max_saisons_termine
                 when 'mondes_termines'     then c.mondes_termines
                 when 'speciaux_vus'        then c.speciaux_vus
               end as valeur
      ) v
  ) as badges
from calcul c
-- Rang atteint : le dernier dont le niveau de départ est atteint.
cross join lateral (
  select rg.numero, rg.nom
    from rangs rg
   where rg.niveau_min <= public.niveau_pour(c.points)
   order by rg.niveau_min desc
   limit 1
) r
-- Rang suivant (aucun au dernier rang).
left join lateral (
  select rg.nom, rg.niveau_min
    from rangs rg
   where rg.niveau_min > public.niveau_pour(c.points)
   order by rg.niveau_min
   limit 1
) rs on true;


-- ---------------------------------------------------------------------
-- 3. Droits : lecture pour les utilisateurs connectés seulement.
--    Les deux fonctions de niveau doivent pouvoir être appelées par
--    « authenticated », puisque la vue s'exécute avec ses droits.
-- ---------------------------------------------------------------------
-- (Tout retirer d'abord, au cas où des droits par défaut auraient été
-- donnés à la création de la vue, puis n'accorder que la lecture.)
revoke all on public.ma_progression from public, anon, authenticated;
grant select on public.ma_progression to authenticated;

revoke execute on function public.seuil_niveau(integer) from public, anon;
revoke execute on function public.niveau_pour(integer)  from public, anon;
grant execute on function public.seuil_niveau(integer) to authenticated;
grant execute on function public.niveau_pour(integer)  to authenticated;
