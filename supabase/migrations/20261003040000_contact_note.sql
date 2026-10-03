-- Note line under a Care Contact on `contacts` ("Memegang kunci cadangan rumah"), #35.
alter table public.care_contacts add column note text not null default '';
