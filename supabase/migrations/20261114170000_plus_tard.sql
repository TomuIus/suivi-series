-- =====================================================================
-- Suivi de séries — statut « Plus tard » (décision 26)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Nouvelle valeur autorisée pour le statut choisi à la main.
--    La règle « check » d'origine n'avait pas de nom dans le schéma
--    initial : PostgreSQL l'a nommée « suivis_statut_manuel_check »
--    (table_colonne_check). On la remplace par la même liste + 'plus_tard'.
--    Si ce nom n'existait pas, la toute première commande échouerait avec
--    un message d'erreur : dans ce cas, ne pas relancer, demander de l'aide.
--
--    La vue « ma_liste » n'a pas besoin de changer : son statut vaut le
--    statut manuel s'il existe, donc 'plus_tard' pour ces séries.
-- ---------------------------------------------------------------------
alter table public.suivis
  drop constraint suivis_statut_manuel_check;

alter table public.suivis
  add constraint suivis_statut_manuel_check
    check (statut_manuel in
      ('a_voir', 'en_cours', 'a_jour', 'termine', 'abandonne', 'plus_tard'));


-- ---------------------------------------------------------------------
-- 2. Cocher un épisode d'une série « Plus tard » la sort de « Plus tard » :
--    son statut redevient automatique (donc « En cours » en général).
--    Les épisodes spéciaux (saison 0) ne comptent pas, comme pour les
--    statuts automatiques (décision 14) : en cocher un ne change rien.
--
--    Fait dans la base (un « déclencheur », ou trigger) plutôt que dans le
--    site : la règle vaudra aussi pour l'appli Android, et pour toutes les
--    façons de cocher (bouton, fiche, « cocher les précédents »).
--
--    La fonction s'exécute avec les droits de la personne qui coche
--    (« security invoker », le comportement par défaut) : les règles RLS
--    s'appliquent, elle ne peut toucher qu'au suivi de cette personne.
--    « search_path = '' » : bonne pratique recommandée par Supabase, les
--    noms de tables sont écrits en entier (public.suivis).
-- ---------------------------------------------------------------------
create function public.sortir_de_plus_tard()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  update public.suivis
     set statut_manuel = null
   where utilisateur_id = new.utilisateur_id
     and serie_id = new.serie_id
     and statut_manuel = 'plus_tard';
  return null;  -- déclencheur « après » : la valeur renvoyée est ignorée
end;
$$;

-- Personne n'a besoin d'appeler cette fonction directement.
revoke execute on function public.sortir_de_plus_tard() from public;

-- Après chaque épisode réellement ajouté, hors spéciaux (un épisode déjà
-- coché, ignoré par le site, ne déclenche rien).
create trigger sortir_de_plus_tard
  after insert on public.episodes_vus
  for each row
  when (new.saison > 0)
  execute function public.sortir_de_plus_tard();
