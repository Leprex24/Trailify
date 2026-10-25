-- Zdjęcia w Supabase Storage (trasy, recenzje, punkty POI).
-- Uruchom RAZ w Supabase: SQL Editor -> New query -> wklej -> Run.
-- To nie jest migracja Flyway: schemat `storage` należy do Supabase, a błąd migracji zablokowałby start backendu.

-- Publiczny bucket: zdjęcia czyta się przez adres URL, bez logowania.
-- Nazwy plików to losowe UUID, więc adresu zdjęcia z trasy prywatnej nie da się odgadnąć.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Zalogowany użytkownik może dodawać pliki tylko do własnego folderu: photos/<jego-uuid>/...
-- (backend dodatkowo wymaga, żeby storageKey zaczynał się od uuid użytkownika).
create policy "photos_insert_own_folder"
on storage.objects for insert to authenticated
with check (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);

-- ...i usuwać tylko własne pliki.
create policy "photos_delete_own"
on storage.objects for delete to authenticated
using (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
