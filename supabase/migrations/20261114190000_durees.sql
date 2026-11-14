-- =====================================================================
-- Suivi de séries — durée des épisodes, pour le « temps passé » (décision 31)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents,
-- et AVANT de redéployer la fonction « tmdb » (elle écrit ces colonnes).
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Durée moyenne des épisodes de chaque saison, en minutes.
--    Calculée par la fonction « tmdb » à partir des durées que TMDB donne
--    épisode par épisode. Vide (NULL) = inconnue.
--    Comme le reste de la fiche, elle est remise à jour avec la série
--    (limite des 6 mois des conditions TMDB, décision 12).
-- ---------------------------------------------------------------------
alter table public.saisons
  add column duree_moyenne integer
    check (duree_moyenne > 0 and duree_moyenne < 600);


-- ---------------------------------------------------------------------
-- 2. Date du dernier calcul des durées pour une série.
--    Vide = pas encore calculées (séries ajoutées avant ce changement) :
--    le site les fera calculer, comme pour le classement série / animé.
-- ---------------------------------------------------------------------
alter table public.series
  add column durees_maj_le timestamptz;


-- ---------------------------------------------------------------------
-- 3. La vue « ma_liste » gagne trois colonnes, ajoutées À LA FIN
--    (PostgreSQL l'exige pour « create or replace view ») :
--      minutes_vues      : temps passé estimé sur cette série, en minutes
--                          (épisodes vus × durée moyenne de leur saison ;
--                          épisodes spéciaux non comptés, comme nb_vus) ;
--      nb_vus_sans_duree : épisodes vus dont la saison n'a pas de durée
--                          connue (non comptés dans minutes_vues) ;
--      durees_a_calculer : vrai si les durées n'ont jamais été calculées.
--    Le reste est identique à 20261114150000_series_animes.sql.
-- ---------------------------------------------------------------------
create or replace view public.ma_liste
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
  greatest(
    su.ajoute_le,
    (select max(v.vu_le)
       from public.episodes_vus v
      where v.utilisateur_id = su.utilisateur_id
        and v.serie_id = su.serie_id)
  ) as derniere_activite,
  su.categorie_manuelle,
  se.est_anime as anime_detecte,
  coalesce(
    case su.categorie_manuelle when 'anime' then true when 'serie' then false end,
    se.est_anime,
    false
  ) as est_anime,
  -- Nouvelles colonnes :
  calc.minutes_vues,
  calc.nb_vus_sans_duree,
  (se.durees_maj_le is null) as durees_a_calculer
from public.suivis su
join public.series se on se.id = su.serie_id
cross join lateral (
  select
    count(*)::integer                           as nb_diffuses,
    (count(*) filter (where d.vu))::integer     as nb_vus,
    (array_agg(d.saison  order by d.saison, d.episode) filter (where not d.vu))[1] as prochaine_saison,
    (array_agg(d.episode order by d.saison, d.episode) filter (where not d.vu))[1] as prochain_episode,
    coalesce(sum(d.duree) filter (where d.vu), 0)::integer                 as minutes_vues,
    (count(*) filter (where d.vu and d.duree is null))::integer            as nb_vus_sans_duree
  from (
    select
      sa.numero as saison,
      e.episode,
      sa.duree_moyenne as duree,
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
      and sa.numero >= 1
      and (
        se.statut_diffusion in ('Ended', 'Canceled')
        or sa.numero < se.derniere_saison_diffusee
        or (sa.numero = se.derniere_saison_diffusee
            and e.episode <= se.dernier_episode_diffuse)
      )
  ) d
) calc;

-- Les droits déjà donnés sur la vue (lecture pour « authenticated », rien
-- pour « anon ») et l'option security_invoker sont conservés.
