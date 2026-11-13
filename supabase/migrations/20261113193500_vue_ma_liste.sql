-- =====================================================================
-- Suivi de séries — vue « ma_liste » : statut et prochain épisode
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après le schéma initial
-- (20261113180000_schema_initial.sql).
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- Une « vue » est une requête enregistrée que l'on interroge comme une
-- table. Celle-ci donne, pour chaque série suivie par l'utilisateur
-- connecté :
--   - le nombre d'épisodes déjà diffusés et le nombre d'épisodes vus ;
--   - le prochain épisode à regarder ;
--   - le statut : celui choisi à la main s'il y en a un, sinon calculé ;
--   - la date de dernière activité (dernier épisode coché, ou ajout).
--
-- Règles de calcul (décisions 13 et 14 du journal du projet) :
--   - les épisodes spéciaux (saison 0) ne comptent pas ;
--   - un épisode est « diffusé » si la série est finie (Ended ou
--     Canceled chez TMDB), ou s'il vient avant le dernier épisode sorti
--     d'après TMDB, ou s'il est ce dernier épisode ;
--   - on suppose que la numérotation recommence à 1 à chaque saison
--     (à revoir après des tests, notamment sur les animés) ;
--   - prochain épisode = le premier épisode diffusé pas encore coché ;
--   - statut calculé :
--       aucun épisode vu                         → « a_voir »
--       des épisodes diffusés pas encore vus     → « en_cours »
--       tout vu et série finie                   → « termine »
--       tout vu et série encore en diffusion     → « a_jour »
--
-- « security_invoker = true » : la vue s'exécute avec les droits de la
-- personne qui l'interroge, donc les règles RLS des tables s'appliquent
-- et chacun ne voit que ses propres suivis. SANS cette option, la vue
-- s'exécuterait avec les droits de son créateur et montrerait les
-- suivis de tout le monde.
-- ---------------------------------------------------------------------
create view public.ma_liste
with (security_invoker = true)
as
select
  su.serie_id,
  se.tmdb_id,
  se.titre,
  se.affiche_chemin,
  se.statut_diffusion,
  su.statut_manuel,
  coalesce(
    su.statut_manuel,
    case
      when calc.nb_vus = 0               then 'a_voir'
      when calc.nb_vus < calc.nb_diffuses then 'en_cours'
      when se.statut_diffusion in ('Ended', 'Canceled') then 'termine'
      else 'a_jour'
    end
  ) as statut,
  calc.nb_diffuses,
  calc.nb_vus,
  calc.prochaine_saison,
  calc.prochain_episode,
  -- greatest() ignore les valeurs vides : sans épisode coché, c'est la date d'ajout.
  greatest(
    su.ajoute_le,
    (select max(v.vu_le)
       from public.episodes_vus v
      where v.utilisateur_id = su.utilisateur_id
        and v.serie_id = su.serie_id)
  ) as derniere_activite
from public.suivis su
join public.series se on se.id = su.serie_id
-- Pour chaque série suivie, on parcourt ses épisodes diffusés (sous-requête
-- « d » ci-dessous) et on en tire les compteurs et le prochain épisode.
cross join lateral (
  select
    count(*)::integer                           as nb_diffuses,
    (count(*) filter (where d.vu))::integer     as nb_vus,
    -- array_agg(... order by ...) range les épisodes non vus dans l'ordre ;
    -- [1] prend le premier.
    (array_agg(d.saison  order by d.saison, d.episode) filter (where not d.vu))[1] as prochaine_saison,
    (array_agg(d.episode order by d.saison, d.episode) filter (where not d.vu))[1] as prochain_episode
  from (
    -- Un épisode par ligne : generate_series(1, n) produit les numéros 1 à n.
    select
      sa.numero as saison,
      e.episode,
      exists (
        select 1
          from public.episodes_vus v
         where v.utilisateur_id = su.utilisateur_id
           and v.serie_id = su.serie_id
           and v.saison = sa.numero
           and v.episode = e.episode
      ) as vu
    from public.saisons sa
    cross join lateral generate_series(1, sa.nb_episodes) as e(episode)
    where sa.serie_id = su.serie_id
      and sa.numero >= 1                                  -- sans les spéciaux
      and (
        se.statut_diffusion in ('Ended', 'Canceled')      -- série finie : tout est sorti
        or sa.numero < se.derniere_saison_diffusee        -- saison déjà terminée
        or (sa.numero = se.derniere_saison_diffusee       -- saison en cours :
            and e.episode <= se.dernier_episode_diffuse)  --   jusqu'au dernier sorti
      )
  ) d
) calc;


-- ---------------------------------------------------------------------
-- Accès depuis l'API : lecture pour les utilisateurs connectés,
-- rien pour les visiteurs (« anon »).
-- ---------------------------------------------------------------------
grant select on public.ma_liste to authenticated;
revoke all on public.ma_liste from anon;
