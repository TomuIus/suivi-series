-- =====================================================================
-- Canapilou — choix du statut et cochage automatique (décision 51)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents et
-- AVANT d'envoyer la v33 du site sur GitHub : la v33 appelle cette
-- fonction ; sans elle, changer le statut d'une série échouerait.
-- Le site actuel (v32) ne s'en sert pas : l'exécuter tôt est sans risque.
--
-- Pourquoi : choisir à la main « Terminé » ou « À jour » coche aussi tous
-- les épisodes déjà sortis qui ne l'étaient pas, hors spéciaux
-- (décision 36). Jusqu'ici, ce calcul était fait dans le navigateur
-- (js/fiche.js). Le site et l'appli Android appelleront désormais cette
-- fonction : la règle n'est écrite qu'une fois. Au passage, le statut et
-- les épisodes sont enregistrés ensemble (tout ou rien) : avant, un échec
-- entre les deux requêtes laissait le statut changé sans les épisodes.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- Fonction « choisir_statut » : donne à une série de sa liste le statut
-- choisi (NULL ou '' = revenir au statut automatique). Si ce statut est
-- « a_jour » ou « termine », coche aussi tous les épisodes déjà sortis
-- et pas encore cochés, sauf les spéciaux (saison 0).
--
-- Renvoie la liste des épisodes qu'elle vient de cocher (et seulement
-- eux), du premier au dernier, sous la forme
--   [{"saison": 1, "episode": 3}, {"saison": 1, "episode": 4}, …]
-- ([] si aucun) : c'est ce que le bouton « Annuler » décoche ensuite.
-- Une seule valeur JSON plutôt qu'une ligne par épisode : la limite de
-- 1 000 lignes par réponse de l'API Supabase ne peut pas la couper
-- (un long animé dépasse 1 000 épisodes).
--
-- Ordre : le statut est enregistré AVANT de cocher. Ainsi, une série
-- « Plus tard » passe bien à « Terminé » : le déclencheur
-- « sortir_de_plus_tard », qui remet le statut automatique quand on
-- coche un épisode, ne touche qu'aux séries encore en « Plus tard ».
--
-- Règle « épisode déjà sorti » : la MÊME que dans la vue « ma_liste »
-- (fichier 20261119220000_fiches_tmdb.sql) et dans js/fiche.js
-- (fonction estDiffuse) : série finie (Ended / Canceled), ou épisode
-- jusqu'au dernier épisode sorti selon TMDB. Si elle change, la modifier
-- à tous ces endroits (et dans l'appli Android).
--
-- « security invoker » (comportement par défaut) : la fonction s'exécute
-- avec les droits de la personne qui l'appelle ; les règles RLS
-- s'appliquent, elle ne peut toucher qu'à ses propres suivis et épisodes.
-- « search_path » vide : noms de tables écrits en entier (public.suivis),
-- précaution recommandée par Supabase.
-- ---------------------------------------------------------------------
create function public.choisir_statut(id_serie bigint, statut_choisi text)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  nouveau  text := nullif(statut_choisi, '');  -- '' = statut automatique
  coches   jsonb := '[]'::jsonb;
begin
  -- 1. Le statut (la règle « check » de la table refuse une valeur inconnue).
  update public.suivis su
     set statut_manuel = nouveau
   where su.utilisateur_id = (select auth.uid())
     and su.serie_id = id_serie;
  if not found then
    raise exception 'Cette série ne fait pas partie de votre liste.';
  end if;

  -- 2. « À jour » ou « Terminé » : on coche ce qui est déjà sorti.
  if nouveau in ('a_jour', 'termine') then
    with ajoutes as (
      insert into public.episodes_vus as v (serie_id, saison, episode)
      select se.id, sa.numero, e.episode
        from public.series se
        join public.saisons sa on sa.serie_id = se.id
        cross join lateral generate_series(1, sa.nb_episodes) as e(episode)
       where se.id = id_serie
         and sa.numero >= 1
         and (
           se.statut_diffusion in ('Ended', 'Canceled')
           or sa.numero < se.derniere_saison_diffusee
           or (sa.numero = se.derniere_saison_diffusee
               and e.episode <= se.dernier_episode_diffuse)
         )
      -- Épisode déjà coché : laissé tel quel (sa date ne change pas)
      -- et absent de la liste renvoyée.
      on conflict (utilisateur_id, serie_id, saison, episode) do nothing
      returning v.saison, v.episode
    )
    select coalesce(
             jsonb_agg(jsonb_build_object('saison', a.saison, 'episode', a.episode)
                       order by a.saison, a.episode),
             '[]'::jsonb)
      into coches
      from ajoutes a;
  end if;

  return coches;
end;
$$;

-- Droit d'appel : utilisateurs connectés seulement (pas les visiteurs).
revoke execute on function public.choisir_statut(bigint, text) from public, anon;
grant execute on function public.choisir_statut(bigint, text) to authenticated;
