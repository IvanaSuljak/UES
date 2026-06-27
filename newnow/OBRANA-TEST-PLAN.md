# NewNow — Plan testiranja i odbrane (UES 2026)

> **Projekat:** Spring Boot + Angular + MySQL + Elasticsearch + MinIO  
> **Backend:** `http://localhost:8080`  
> **Frontend:** `http://localhost:4200`  
> **Elasticsearch:** `http://localhost:9200`  
> **MinIO:** API `:9000`, konzola `:9001` — `minioadmin` / `minioadmin`, bucket `newnow-files`

---

## Demo nalozi

| Uloga | Email | Lozinka | Napomena |
|-------|-------|---------|----------|
| Admin | `admin@newnow.com` | `admin123` | Kreira se automatski pri startu (`AuthController.initAdmin()`) |
| Menadžer | `petar@manager.com` ili menadžeri iz baze | iz seed podataka | Vidi Manager Dashboard i Analitiku |
| Korisnik | bilo koji odobren nalog | — | Registracija preko `/register` + admin odobrenje |

---

## Pokretanje sistema pre testiranja

1. Pokrenuti **Docker Desktop**
2. `docker-compose up -d` (ES 8.13 + MinIO)
3. MySQL baza `newnow` na `:3306`
4. `.\mvnw.cmd spring-boot:run` — automatski reindeksira ES
5. `cd frontend && ng serve` — Angular na `:4200`

---

# K1 — Zahtev za registraciju

## Naziv funkcionalnosti

Zahtev za registraciju (ne direktna registracija). Korisnik šalje zahtev koji admin mora odobriti pre prijave.

## Preduslovi (user, role, data needed)

- **Korisnik:** neregistrovan posetilac (bez JWT tokena)
- **Podaci:** ime, email, lozinka (min. 6 karaktera u frontend validaciji)
- **Baza:** email ne sme postojati u tabeli `users` sa aktivnim nalogom

## Koraci testiranja

1. Otvoriti `http://localhost:4200/register`
2. Popuniti polja: Ime, Email, Lozinka
3. Kliknuti dugme za slanje zahteva
4. Pokušati login sa istim emailom pre odobrenja
5. Admin odobri zahtev (vidi A1)
6. Ponoviti registraciju sa emailom koji je **odbijen** (REJECTED) — novi zahtev

## Očekivani rezultat (po koraku)

1. Stranica **Registracija** se učitava
2. Forma prihvata podatke
3. Poruka: *„Zahtev za registraciju uspešno poslat! Administrator će ga obraditi."*; u bazi `account_requests` status = `PENDING`
4. Login vraća: *„Vaš zahtev za registraciju je na čekanju..."* — **ne** izdaje JWT
5. Nakon odobrenja korisnik može da se prijavi (K2)
6. **Re-registracija posle REJECTED:** postojeći zapis se ažurira (ime, lozinka), status postaje `PENDING` ponovo — nema duplog reda

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Dupli PENDING zahtev za isti email | 400 — *„Zahtev za ovaj email je već poslat i čeka odobrenje."* |
| E2 | Email već postoji u `users` | 400 — *„Email već postoji u sistemu!"* |
| E3 | Prazna polja | 400 — *„Ime, email i lozinka su obavezni."* |
| E4 | REJECTED → ponovni zahtev | 200 — isti email, novi PENDING (implementirano u `AccountRequestController.createRequest()`) |
| E5 | Pokušaj direktne registracije (`POST /api/auth/register`) | Endpoint ne postoji — zaobilazi K1 |

## Demonstracija kroz UI (Task 4)

- **Navbar (gost):** link **„Registruj se"** → ruta `/register`
- **Stranica Registracija:** forma sa poljima, dugme za slanje
- **Navbar (admin):** dropdown → **„⚙️ Admin panel"** → tab **„📋 Zahtevi"** — odobrenje/odbijanje

## Implementacija za odbranu (Task 7)

| Sloj | Fajl / klasa | Metoda / uloga |
|------|--------------|----------------|
| Frontend | `frontend/src/app/components/register/` | Forma → `POST /api/account-requests` |
| Controller | `AccountRequestController.java` | `createRequest()` — validacija, BCrypt hash, PENDING |
| Service | `AccountRequestServiceImpl.java` | `save()` |
| Repository | `AccountRequestRepository.java` | `findByEmail`, `existsByEmailAndStatus` |
| DB | MySQL tabela `account_requests` | Entitet `AccountRequest` |

**Tok:** RegisterComponent → `AccountRequestController.createRequest()` → `AccountRequestService` → `AccountRequestRepository` → MySQL

**Zašto ovako:** Specifikacija K1 zabranjuje direktno kreiranje `User` entiteta; privremeni `AccountRequest` sa statusom omogućava admin kontrolu (A1) pre aktivacije naloga.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto AccountRequest umesto odmah User?**  
O: K1 zahteva da admin prvo obradi zahtev. AccountRequest je privremeni entitet sa statusom PENDING/APPROVED/REJECTED pre nego što postane User sa `enabled=true`.

**P: Šta se dešava posle odbijenog zahteva?**  
O: Korisnik može ponovo poslati zahtev sa istim emailom — ažuriramo postojeći REJECTED zapis na PENDING, ne kreiramo duplikat.

**P: Da li se lozinka vraća u JSON odgovoru?**  
O: Ne — `@JsonProperty(access = WRITE_ONLY)` na `AccountRequest.password`; prima se pri POST, ne izlazi u GET.

---

# K2 — Prijava i odjava

## Naziv funkcionalnosti

Prijava na sistem emailom i lozinkom sa JWT tokenom; odjava briše token na klijentu.

## Preduslovi

- **Korisnik:** registrovan i odobren (`enabled=true`) ili postojeći AccountRequest
- **Podaci:** email, lozinka
- **Admin:** `admin@newnow.com` / `admin123`

## Koraci testiranja

1. Otvoriti `/login`, uneti validne kredencijale odobrenog korisnika
2. Proveriti navbar — prikazuje ime korisnika
3. Kliknuti **„🚪 Odjavi se"** u dropdown meniju
4. Pokušati login sa pogrešnom lozinkom
5. Pokušati login sa PENDING zahtevom (pre admin odobrenja)
6. Pokušati login sa REJECTED zahtevom
7. Pokušati login sa tačnom lozinkom ali pogrešnim emailom

## Očekivani rezultat

1. Uspešan login — JWT u `localStorage`, redirect na početnu
2. Navbar: **„👤 {ime}"** dropdown sa Profil, Odjavi se
3. Token obrisan, redirect na `/login`, linkovi Prijavi se / Registruj se
4. *„Pogrešan email ili lozinka."* — **bez** otkrivanja statusa zahteva
5. *„Vaš zahtev za registraciju je na čekanju..."* — samo posle provere lozinke
6. *„Vaš zahtev za registraciju je odbijen..."* — samo posle provere lozinke
7. *„Pogrešan email ili lozinka."* — ne otkriva da li email postoji

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Pogrešna lozinka za postojeći User | 401 — generička poruka (ne curenje statusa) |
| E2 | Tačna lozinka, PENDING AccountRequest | 401 — poruka o čekanju (**posle** provere lozinke — K2 fix) |
| E3 | Prazan email ili lozinka | 400 |
| E4 | `enabled=false` User | 401 — nalog nije aktiviran |
| E5 | Logout bez tokena | 200 — endpoint radi, nema greške |

## Demonstracija kroz UI

- **Navbar (gost):** **„Prijavi se"** → `/login`
- **Login stranica:** polja Email, Lozinka, dugme Prijavi se
- **Navbar (ulogovan):** dropdown → **„🚪 Odjavi se"**

## Implementacija za odbranu

| Sloj | Fajl | Detalj |
|------|------|--------|
| Frontend | `login.ts`, `auth.service.ts` | `login()`, `logout()`, `localStorage` token |
| Controller | `AuthController.java` | `login()`, `logout()` |
| Security | `JwtUtil.java` | `generateToken()`, `extractUsername()` — HS256, 10h |
| Filter | `JwtAuthenticationFilter.java` | Parsira `Authorization: Bearer` |
| Config | `SecurityConfig.java` | Javni `/api/auth/login`, `/logout` |
| DB | `UserRepository` | `findByEmail` |

**Tok login:** LoginComponent → `AuthController.login()` → `UserRepository` + `PasswordEncoder.matches()` → `JwtUtil.generateToken()` → JSON odgovor

**K2 fix:** Ako User ne postoji, `AccountRequest` se proverava **tek posle** `passwordEncoder.matches()` — sprečava curenje PENDING/REJECTED statusa pre provere lozinke.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto JWT a ne sesija?**  
O: Specifikacija traži token autorizaciju. JWT je stateless — server ne drži sesiju; klijent šalje token u svakom zahtevu.

**P: Da li se token briše sa servera pri logout?**  
O: Ne — JWT je stateless. Klijent briše token iz localStorage; server samo loguje događaj (log4j).

**P: Zašto generička poruka za pogrešnu lozinku?**  
O: Bezbednosna praksa — ne otkrivamo da li email postoji u sistemu pre provere lozinke.

---

# K3 — Rukovanje mestima

## Naziv funkcionalnosti

CRUD nad mestima (lokacijama): obavezna polja, slika, autorizacija — admin kreira/briše/menja sve; menadžer menja samo adresu, tip i opis.

## Preduslovi

- **Admin:** JWT + uloga ADMIN — kreiranje/brisanje/dodela menadžera
- **Menadžer:** JWT + dodeljeno mesto — ažuriranje ograničenih polja
- **Podaci:** naziv, adresa, tip, opis, slika (URL ili MinIO upload)

## Koraci testiranja

1. Admin → `/admin` → tab **„🏢 Lokacije"** → **„➕ Dodaj lokaciju"**
2. Popuniti sva obavezna polja + upload slike (MinIO)
3. Sačuvati — proveriti listu na `/locations`
4. Menadžer → `/manager` → izmeniti adresu, tip, opis svog mesta
5. Menadžer pokušati promenu naziva/slike — ne uspeva
6. Admin obriše mesto
7. Anonimni korisnik pokušava `POST /api/locations` bez tokena

## Očekivani rezultat

1. Modal forma se otvara
2. Slika se uploaduje u MinIO bucket `newnow-files/images/`
3. Mesto u listi sa `averageRating` i `totalReviews`
4. Menadžer menja samo address, type, description — 200 OK
5. Naziv i slika ostaju nepromenjeni (backend ignoriše ta polja za menadžera)
6. Mesto nestaje iz baze i ES indeksa
7. 401/403 Forbidden

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | POST bez `imageUrl` | 400 — sva polja obavezna |
| E2 | USER pokušava kreirati mesto | 403 |
| E3 | Menadžer menja tuđe mesto | 403 |
| E4 | GET `/api/locations` bez auth | 200 — javno čitanje |
| E5 | Brisanje mesta | ES `removeFromIndex()` + MySQL delete |

## Demonstracija kroz UI

- **Navbar:** **„📍 Mesta"** → `/locations` — lista sa prosečnom ocenom
- **Admin panel** (`/admin`): tab Lokacije — **„➕ Dodaj lokaciju"**, **„✏️"**, **„🗑️"**, **„📄"** (PDF download)
- **Manager Dashboard** (`/manager`): sekcija za izmenu atributa mesta
- **Detalji mesta:** `/locations/:id` — `location-details.component`

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `admin-dashboard.ts/html`, `locations.ts`, `location-details.component.ts` | CRUD UI, MinIO upload |
| Controller | `LocationController.java` | `createLocation()`, `updateLocation()`, `deleteLocation()` |
| Service | `LocationServiceImpl.java` | Poslovna logika, `searchLocations()` |
| Repository | `LocationRepository.java` | JPA CRUD, `searchLocations()` query |
| Files | `FileController.java` | `POST /api/files/images` → MinIO |
| ES | `LocationIndexService.java` | `indexLocation()` / `removeFromIndex()` nakon promene |
| DB | MySQL `locations` | Entitet `Location` |

**Tok kreiranja:** AdminDashboard → `LocationController.createLocation()` → `LocationServiceImpl.save()` → `LocationRepository` → MySQL → `LocationIndexService.indexLocation()` → Elasticsearch

**Zašto ručna autorizacija u kontroleru:** Treba provera „menadžer **ovog** mesta", ne samo uloge MANAGER — fleksibilnije od `@PreAuthorize` na nivou role.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto menadžer ne može menjati naziv?**  
O: Specifikacija kaže da menadžer ažurira adresu, tip i opis — naziv i slika su admin prerogativ.

**P: Odakle dolazi averageRating u listi?**  
O: Računa se iz tabele utisaka u realnom vremenu preko `LocationReviewRepository`, ne čuva se denormalizovano u Location (osim u ES indeksu).

---

# K4 — Rukovanje događajima

## Naziv funkcionalnosti

CRUD događaja na mestu: obavezna polja (naziv, tip, datum, redovnost, slika), autorizacija — menadžer samo svog mesta, admin može sve.

## Preduslovi

- **Menadžer:** dodeljeno mesto sa postojećim događajima u seed-u
- **Admin:** pun pristup
- **Podaci:** title, type, dateTime, isRegular, imageUrl, opciono price i address

## Koraci testiranja

1. Menadžer → `/manager` → **„Dodaj događaj"**
2. Popuniti obavezna polja, postaviti cenu 0 ili prazno za besplatno
3. Sačuvati — proveriti na `/events` i na stranici lokacije
4. Izmeniti događaj
5. Obrisati događaj
6. Menadžer pokušava kreirati događaj na tuđem mestu
7. Anonimni POST bez tokena

## Očekivani rezultat

1. Forma dostupna u Manager Dashboard
2. Besplatni događaj: `price = null` ili `0` — prikaz **„💚 Besplatno"**
3. Događaj vidljiv u gridu na stranici Događaji
4. Izmena uspešna — 200
5. Brisanje uspešno
6. 403 Forbidden
7. 401/403

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | POST bez imageUrl | 400 |
| E2 | Budući datum | Dozvoljeno — događaji mogu biti u budućnosti |
| E3 | `isRegular=false` | Dozvoljeno — ali K5 ne dozvoljava utisak za neredovne |
| E4 | Cena null vs 0 | Oba tretirana kao besplatno (K4/K6 alignment) |

## Demonstracija kroz UI

- **Navbar:** **„🎉 Događaji"** → `/events`
- **Manager Dashboard** (`/manager`): forma za dodavanje/izmenu/brisanje događaja
- **Detalji lokacije** (`/locations/:id`): sekcija **„🎉 Predstojeći događaji"**

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `manager-dashboard.ts/html`, `events-page.component.ts` | CRUD, filteri |
| Controller | `EventController.java` | `createEvent()`, `updateEvent()`, `deleteEvent()` |
| Service | `EventServiceImpl.java` | `findByDate()`, `countEventOccurrences()` |
| Repository | `EventRepository.java` | `findByDate()`, `findTodayEvents()` |
| DB | MySQL `events` | Entitet `Event` |

**Tok:** ManagerDashboard → `EventController` → `isManagerOf(user, location)` → `EventServiceImpl` → `EventRepository` → MySQL

### Moguća pitanja profesora + Kratak odgovor

**P: Kako proveravate da menadžer radi na svom mestu?**  
O: Metoda `isManagerOf()` — role==MANAGER, location.manager != null, location.manager.id == user.id.

**P: Ko može brisati događaj?**  
O: Menadžer svog mesta ili admin — oba prolaze autorizaciju u kontroleru.

---

# K5 — Ostavljanje utiska na mesto

## Naziv funkcionalnosti

Ulogovani korisnik ostavlja utisak na mesto za redovan događaj koji se već održao; 4 opcione kategorije ocena (1–10), komentar opcion.

## Preduslovi

- **Korisnik:** ulogovan (JWT)
- **Podaci:** lokacija sa bar jednim prošlim redovnim događajem
- **Ocene:** nastup, zvuk/svetlo, prostor, ukupan utisak — svaka 1–10 ako je uneta

## Koraci testiranja

1. Login kao običan korisnik
2. Otvoriti `/locations/:id` (npr. Exit Festival)
3. U sekciji **„✍️ Ostavite svoj utisak"** izabrati događaj iz dropdown-a
4. Uneti ocene (1–10), opciono komentar
5. Kliknuti **„Pošalji utisak"**
6. Proveriti da se utisak pojavljuje u listi sa nazivom događaja i `eventOccurrenceCount`
7. Pokušati utisak bez događaja / sa budućim događajem / sa ocenom 11

## Očekivani rezultat

1. JWT validan
2. Stranica lokacije učitana
3. Dropdown prikazuje samo `isRegular=true` i `dateTime < now`
4. Utisak sačuvan; prosečna ocena lokacije ažurirana; ES reindeksiran
5. Na kartici utiska: naziv događaja + broj održavanja do trenutka pisanja
6. 400 sa jasnom porukom greške
7. 400 — *„ocena mora biti između 1 i 10"*

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Nema prošlih redovnih događaja | Frontend: žuta poruka; forma onemogućena |
| E2 | `isRegular=false` | 400 |
| E3 | Budući događaj | 400 |
| E4 | Komentar prazan | Dozvoljeno (optional comment fix) |
| E5 | Ocena 0 ili 11 | 400 — `validateRating()` |
| E6 | `eventOccurrenceCount` | Broji sve termine istog naslova na lokaciji pre trenutnog |

## Demonstracija kroz UI

- **Navbar:** **„📍 Mesta"** → klik na karticu → `/locations/:id`
- **Sekcija:** **„✍️ Ostavite svoj utisak"** — dropdown **„🎪 Izaberite događaj"**, ocene, **„Pošalji utisak"**
- **Lista utisaka** ispod sa sortiranjem (K7)

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `location-details.component.ts/html` | `submitReview()`, `pastRegularEvents` filter |
| Controller | `LocationController.java` | `addReview()`, `validateRating()` |
| Service | `LocationReviewServiceImpl.java`, `EventServiceImpl.java` | `save()`, `countEventOccurrences()` |
| Repository | `LocationReviewRepository.java` | CRUD utisaka |
| ES | `LocationIndexService.indexLocation()` | Reinindeks posle novog utiska |
| DB | MySQL `location_reviews` | Entitet `LocationReview` |

**Tok:** LocationDetails → `POST /api/locations/{id}/reviews` → validacija događaja → `reviewService.save()` → `locationIndexService.indexLocation()` → ES

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto mora biti redovan događaj?**  
O: Za redovne programe ima smisla ocena po kategorijama (nastup, zvuk, prostor); neredovni su jednokratni.

**P: Šta je eventOccurrenceCount?**  
O: Broj dosadašnjih termina tog redovnog događaja u trenutku pisanja — čuva se u bazi jer se broj menja vremenom.

---

# K6 — Pretraga i filtriranje mesta i događaja

## Naziv funkcionalnosti

Unified pretraga mesta (naziv, adresa, tip) i filtriranje događaja (tekst, tip, datum, cena).

## Preduslovi

- **Korisnik:** bilo ko (javni GET endpointi)
- **Podaci:** seed lokacije i događaji u bazi

## Koraci testiranja

1. Otvoriti `/locations`, uneti tekst u polje pretrage (naziv/adresa/tip)
2. Otvoriti `/events`, pretražiti po nazivu, mestu ili adresi
3. Izabrati filter **tip događaja**
4. Izabrati **datum** u date picker-u (K6 backend: `GET /api/events/filter/date`)
5. Filter **Besplatno** / **Plaćeno**
6. Kombinovati više filtera

## Očekivani rezultat

1. Lista mesta filtrirana — AND logika između parametara
2. Događaji filtrirani na frontendu + po datumu
3. Samo događaji izabranog tipa
4. Samo događaji tog kalendarskog datuma
5. Besplatno = `price == null || price === 0`
6. Presek svih aktivnih filtera

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Prazan filter na `/locations` | Sva validna mesta |
| E2 | Datum u prošlosti | Vraća događaje tog dana |
| E3 | Datum u budućnosti | Vraća događaje tog dana |
| E4 | Cena 0 vs null | Oba = besplatno |
| E5 | Nepostojeći tip | Prazna lista |

## Demonstracija kroz UI

- **Navbar:** **„📍 Mesta"** → `/locations` — search polje
- **Navbar:** **„🎉 Događaji"** → `/events` — search, tip, datum, cena, **„✕ Resetuj"**, toggle **„📅 Samo danas"** / **„📆 Svi događaji"**

## Implementacija za odbranu

| Sloj | Fajl | Detalj |
|------|------|--------|
| Frontend | `locations.ts`, `events-page.component.ts` | Client-side filteri + API pozivi |
| Controller | `LocationController.java`, `EventController.java` | GET sa query parametrima |
| Repository | `LocationRepository.searchLocations()`, `EventRepository.findByDate()` | JPQL LIKE + CAST date |
| DB | MySQL | |

**Tok mesta:** LocationsComponent → `GET /api/locations?name=&address=&type=` → `LocationServiceImpl.searchLocations()` → JPQL

**Tok događaja po datumu:** EventsService → `GET /api/events/filter/date?date=YYYY-MM-DD` → `EventRepository.findByDate()`

### Moguća pitanja profesora + Kratak odgovor

**P: Kako radi filter po datumu u bazi?**  
O: JPQL `CAST(e.dateTime AS date) = :date` — poredi samo datum bez vremena.

**P: Zašto K6 na MySQL a S1 na Elasticsearch?**  
O: K6 je osnovna pretraga iz specifikacije ocene 7; S1 (UES) je napredna full-text pretraga sa analizerom, fuzzy, highlight.

---

# K7 — Sortiranje utisaka

## Naziv funkcionalnosti

Sortiranje utisaka na stranici lokacije po datumu ili prosečnoj oceni, rastuće/opadajuće.

## Preduslovi

- Lokacija sa više utisaka
- Javni GET endpoint (skriveni/obrisani filtrirani)

## Koraci testiranja

1. Otvoriti `/locations/:id`
2. Dropdown **„Sortiraj po"** → Datum
3. Dropdown **„Redosled"** → Opadajuće / Rastuće
4. Promeniti na **Ocena** → Opadajuće / Rastuće
5. Proveriti da sakriveni utisci nisu vidljivi

## Očekivani rezultat

1. Utisci sortirani po datumu DESC
2. ASC — najstariji prvi
3. Po oceni DESC — najbolji prvi (sort u Javi)
4. Po oceni ASC — najniži prvi
5. Samo vidljivi (`!isDeleted && !isHidden`)

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Lokacija bez utisaka | Prazna lista |
| E2 | Svi utisci iste ocene | Stabilan redosled po datumu |
| E3 | Nevalidan sortBy | Default: date desc |

## Demonstracija kroz UI

- **`/locations/:id`:** dropdown-i **„Sortiraj po"** (datum/ocena) i **„Redosled"** (rastuće/opadajuće)

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `location-details.component.ts` | `loadReviews(sortBy, sortOrder)` |
| Controller | `LocationController.getReviews()` | Query params `sortBy`, `order` |
| Service | `LocationReviewServiceImpl` | `findByLocationIdSortedByDate*`, `findByLocationIdSortedByRating*` |
| Repository | `LocationReviewRepository` | ORDER BY createdAt; rating sort u servisu |

**Zašto rating sort u Javi:** Prosečna ocena je izvedena vrednost (prosek 4 kategorija), nije kolona u bazi.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto datum u bazi a ocena u memoriji?**  
O: Za datum imamo indeksiranu kolonu `createdAt` — efikasno ORDER BY. Za ocenu nema direktne kolone pa stream().sorted() u servisu.

---

# K8 — Početna stranica

## Naziv funkcionalnosti

Početna stranica prikazuje: događaje danas (max 6), top 4 mesta po oceni, 3 najskorija utiska sa najpopularnijeg mesta.

## Preduslovi

- Seed događaj sa današnjim datumom (za test sekcije „Događaji danas")
- Bar jedna lokacija sa utiscima

## Koraci testiranja

1. Otvoriti `/` (Početna)
2. Proveriti sekciju **„Događaji danas"**
3. Proveriti **„Najbolje ocenjena mesta"** (top 4)
4. Proveriti **„Najnoviji utisci"** (3 sa top lokacije)
5. Kliknuti link na lokaciju/događaj

## Očekivani rezultat

1. Tri sekcije učitane iz `GET /api/home`
2. Samo događaji između 00:00 i 23:59:59.999 **danas** (boundary fix: `startOfDay` do `startOfNextDay`)
3. Sortirano po oceni DESC, tie-break po broju utisaka
4. 3 vidljiva utiska sa lokacije #1 u topLocations
5. Navigacija na `/locations/:id`

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Nema događaja danas | Prazna sekcija događaja |
| E2 | Događaj u 23:59 večeras | Uključen u „danas" |
| E3 | Događaj sutra u 00:01 | Nije uključen |
| E4 | Nema utisaka | recentReviews = [] |
| E5 | Skriveni utisci na top lokaciji | Ne prikazuju se |

## Demonstracija kroz UI

- **Navbar:** **„🏠 Početna"** → `/`
- **Sekcije:** Događaji danas, Najbolje ocenjena mesta, Najnoviji utisci

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `home.component.ts/html` | `GET /api/home` |
| Controller | `HomeController.java` | `getHomepageData()` |
| Repository | `EventRepository.findTodayEvents()`, `LocationRepository.findAllValidLocations()` | |
| Service | `LocationReviewService` | prosečne ocene, utisci |

**Tok:** HomeComponent → `HomeController` → agregacija iz MySQL → JSON

### Moguća pitanja profesora + Kratak odgovor

**P: Šta znači najpopularnije mesto?**  
O: Najviša prosečna ocena (srednja vrednost kategorija svih utisaka); pri izjednačenju — više utisaka.

**P: K8 today boundary fix?**  
O: Koristimo `[startOfDay, startOfNextDay)` umesto `LocalDate.now()` poređenja — događaj u 23:59 ne ispadne iz „danas".

---

# K9 — Promena lozinke

## Naziv funkcionalnosti

Ulogovani korisnik menja lozinku unosom stare i nove; email notifikacija (mock).

## Preduslovi

- Ulogovan korisnik
- Poznata trenutna lozinka

## Koraci testiranja

1. Login → dropdown → **„👤 Profil"** → `/profile`
2. Klik **„Promeni lozinku"**
3. Uneti staru, novu i potvrdu lozinke
4. Sačuvati
5. Odjaviti se i login novom lozinkom
6. Pokušati sa pogrešnom starom lozinkom

## Očekivani rezultat

1. Profil učitan
2. Forma vidljiva
3. Uspeh — poruka u UI; log4j + mock email u konzoli
4. Login sa novom lozinkom radi
5. 400/401 — stara lozinka netačna

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Nova ≠ potvrda (frontend) | Validacija pre slanja |
| E2 | Nova lozinka < 6 karaktera | Frontend validacija |
| E3 | Bez JWT tokena | 401 |

## Demonstracija kroz UI

- **`/profile`:** dugme **„Promeni lozinku"** — 3 polja + Sačuvaj

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `profile.ts/html` | Forma promene lozinke |
| Controller | `UserController.java` | `PUT /api/users/change-password` |
| Service | `UserServiceImpl.changePassword()` | BCrypt hash + `EmailService` |
| DB | MySQL `users.password` | |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto hash na backendu?**  
O: Nikad ne čuvamo plain text — BCrypt u `UserServiceImpl` pre snimanja u bazu.

**P: Zašto email posle promene?**  
O: Korisnik dobija obaveštenje — ako nije on promenio, može reagovati. Mock u razvoju.

---

# K10 — Profil korisnika

## Naziv funkcionalnosti

Pregled i izmena profila: ime, adresa, slika (URL); menadžer vidi svoja mesta; svi vide svoje utiske.

## Preduslovi

- Ulogovan korisnik (USER ili MANAGER)

## Koraci testiranja

1. `/profile` — proveriti prikaz podataka
2. **„Izmeni profil"** — promeniti ime, adresu, URL slike
3. Login kao menadžer — proveriti sekciju **„Moja mesta"**
4. Proveriti listu **„Moji utisci"**
5. Klik na mesto vodi na `/locations/:id`

## Očekivani rezultat

1. Podaci iz `GET /api/users/profile` (bez lozinke — `@JsonIgnore`)
2. `PUT /api/users/profile` ažurira polja
3. Kartice mesta sa slikom, tipom, adresom
4. Utisci sa lokacijom, ocenama, komentarom, datumom
5. Router link radi

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Prazno fullName | 400 — obavezno |
| E2 | USER ne vidi managedLocations | Sekcija skrivena |
| E3 | Menadžer bez dodeljenog mesta | Prazna lista mesta |

## Demonstracija kroz UI

- **Navbar dropdown:** **„👤 Profil"** → `/profile`
- **Dugmad:** **„Izmeni profil"**, **„Promeni lozinku"**, **„Odjavi se"**

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `profile.ts/html/css` | Prikaz i modal izmene |
| Controller | `UserController.java` | `getProfile()`, `updateProfile()` |
| Service | `UserServiceImpl` | |
| Repository | `UserRepository`, `LocationRepository` | managedLocations filter |
| DB | MySQL | |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto slika profila kao URL a ne upload?**  
O: Specifikacija traži promenu slike, nije eksplicitno file upload; konzistentno sa lokacijama pre MinIO integracije; profil koristi URL polje.

**P: Kako backend zna čiji je profil?**  
O: Email iz JWT tokena — `JwtUtil.extractUsername()` u `UserController`.

---

# M1 — Ažuriranje atributa mesta i rukovanje događajima (menadžer)

## Naziv funkcionalnosti

Menadžer ažurira adresu, tip i opis svog mesta; kreira, menja i briše događaje isključivo na svom mestu.

## Preduslovi

- Menadžer sa dodeljenim mestom (npr. Exit Festival)
- JWT token

## Koraci testiranja

1. Login menadžer → `/manager`
2. Izmeniti adresu, tip, opis lokacije → Sačuvaj
3. Dodati novi događaj
4. Izmeniti postojeći događaj
5. Obrisati događaj
6. Pokušati API poziv za tuđu lokaciju (Postman)

## Očekivani rezultat

1. Manager Dashboard učitan
2. Samo address, type, description promenjeni — naziv/slika netaknuti
3–5. CRUD događaja uspešan na svom mestu
6. 403 Forbidden

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Menadžer dva mesta (A2) | Može upravljati oba |
| E2 | USER na `/manager` | Guard/preusmerenje |
| E3 | Brisanje poslednjeg događaja | Dozvoljeno — K5 onemogućava utisak |

## Demonstracija kroz UI

- **Navbar dropdown (MANAGER):** **„🏢 Manager Dashboard"** → `/manager`

## Implementacija za odbranu

Kombinacija K3/K4 sa `LocationController.updateLocation()` i `EventController` — ista klasa, provera `isManagerOf()`.

### Moguća pitanja profesora + Kratak odgovor

**P: Gde je granica između K3 i M1?**  
O: K3 definiše CRUD i autorizaciju; M1 specifično opisuje menadžerske operacije — implementirano istim endpointima sa role check.

---

# M2 — Rukovanje utiscima (menadžer)

## Naziv funkcionalnosti

Menadžer sakriva, otkriva ili logički briše utiske na svom mestu. Sakriveno: ocena i dalje ulazi u prosek; obrisano: ne ulazi.

## Preduslovi

- Menadžer sa utiscima na svom mestu

## Koraci testiranja

1. `/manager` → sekcija **„Recenzije"**
2. Klik **„Sakrij"** na utisku
3. Otvoriti `/locations/:id` — utisak nije vidljiv, prosečna ocena **ista**
4. Vratiti se — **„Prikaži"**
5. Klik **„Obriši"** — utisak nestaje, prosek **ponovo izračunat**

## Očekivani rezultat

1. Lista svih utisaka (uključujući sakrivene) u dashboard-u
2. `isHidden=true`, badge „Sakriveno"
3. Javna stranica ne prikazuje; prosek uključuje ocenu
4. Utisak vidljiv ponovo
5. `isDeleted=true`; prosek bez te ocene; ES reindeks

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | USER pokušava hide | 403 |
| E2 | Menadžer tuđeg utiska | 403 |
| E3 | Sakrij pa obriši | Obrisano ima prioritet — ne računa se |

## Demonstracija kroz UI

- **`/manager`:** sekcija recenzija — **„Sakrij"** / **„Prikaži"** / **„Obriši"**

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Controller | `ReviewController.java` | `hide`, `unhide`, `delete`, `getMyReviews` |
| Service | `LocationReviewServiceImpl` | `getAverageRatingForLocation()` filtrira `!isDeleted` |
| ES | `LocationIndexService` | reindeks posle promene |

### Moguća pitanja profesora + Kratak odgovor

**P: Razlika sakriveno vs obrisano?**  
O: Sakriveno — nije vidljivo korisnicima ali ocena ulazi u prosek. Obrisano — logički uklonjeno, ocena se ignoriše u svim kalkulacijama.

---

# M3 — Komentari i reply (neograničena dubina)

## Naziv funkcionalnosti

Menadžer odgovara na utisak; bilo koji ulogovani korisnik odgovara na komentar; proizvoljna dubina thread-a.

## Preduslovi

- Postojeći utisak na lokaciji menadžera
- Menadžer i običan korisnik (dva naloga)

## Koraci testiranja

1. Menadžer → `/locations/:id` → na utisku **„Odgovori"** → pošalji tekst
2. Korisnik → **„Prikaži komentare"** → vidi odgovor
3. Korisnik → **„↩ Odgovori"** pored menadžerovog komentara
4. Menadžer/korisnik → reply na reply (3+ nivoa)
5. Proveriti rekurzivni prikaz u `CommentThreadComponent`

## Očekivani rezultat

1. `POST /api/comments/review/{reviewId}` — komentar sačuvan
2. Thread učitán — `GET /api/comments/review/{id}` sa `buildCommentTree()`
3. `POST /api/comments/{commentId}/reply` — nested reply
4. Proizvoljna dubina u UI
5. Rekurzivna komponenta renderuje sve nivoe

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | USER odgovara direktno na utisak (bez menadžera) | 403 — prvi nivo samo menadžer |
| E2 | Prazan tekst | 400 |
| E3 | Brisanje komentara | Autor ili menadžer |

## Demonstracija kroz UI

- **`/locations/:id`:** **„Prikaži komentare"**, **„Odgovori"**, **„↩ Odgovori"** na svakom nivou

## Implementacija za odbranu

| Sloj | Fajl | Detalj |
|------|------|--------|
| Frontend | `location-details.component.ts`, `comment-thread.component.ts` | Rekurzivni prikaz (M3 fix) |
| Controller | `CommentController.java` | `addCommentToReview()`, `reply()`, `buildCommentTree()` |
| Service | `CommentServiceImpl` | |
| DB | MySQL `comments` | `parentComment` self-reference |

### Moguća pitanja profesora + Kratak odgovor

**P: Kako implementirate neograničenu dubinu?**  
O: Rekurzivna `CommentThreadComponent` u Angularu + `buildCommentTree()` na backendu koji mapira `parentComment` u stablo.

---

# M4 — Analitika mesta

## Naziv funkcionalnosti

Menadžer pregleda analitiku svog mesta: KPI, grafici (Chart.js), top/bottom lokacije, nedavni utisci.

## Preduslovi

- Menadžer sa događajima i utiscima u periodu
- JWT token

## Koraci testiranja

1. Login menadžer → dropdown **„📊 Analitika"** → `/analytics`
2. Izabrati lokaciju, period **„Mesečno"** → **„Prikaži analitiku"**
3. Proveriti KPI kartice (7 brojeva)
4. Proveriti pie (redovni/neredovni), pie (besplatni/plaćeni), bar (po mesecu), radar (ocena po kategorijama)
5. Tabele: Top 5 događaja, Top 5 lokacija, **Bottom 5 lokacija** (M4 fix)
6. Promeniti na **„Godišnje"** ili **„Prilagođeno"**

## Očekivani rezultat

1. Stranica analitike učitana
2. JSON iz `GET /api/analytics/location/{id}?startDate&endDate`
3. KPI: ukupno/redovni/neredovni/besplatni/plaćeni događaji, broj utisaka, prosečna ocena
4. Chart.js grafici renderovani
5. `bottomLocations` — 5 najslabije ocenjenih lokacija sa utiscima
6. Podaci se menjaju po periodu

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | USER pristupa `/analytics` | 403 / guard |
| E2 | Menadžer tuđe lokacije | 403 |
| E3 | Period bez događaja | KPI = 0, prazni grafici |

## Demonstracija kroz UI

- **Navbar dropdown (MANAGER):** **„📊 Analitika"** → `/analytics`
- **Kontrole:** izbor lokacije, Nedeljno/Mesečno/Godišnje/Prilagođeno, **„Prikaži analitiku"**

## Implementacija za odbranu

| Sloj | Fajl | Detalj |
|------|------|--------|
| Frontend | `analytics.component.ts/html/css` | Chart.js integracija |
| Controller | `AnalyticsController.java` | `getAnalytics()` — agregacije |
| Repository | `EventRepository`, `LocationReviewRepository` | period queries |
| DB | MySQL | |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto Chart.js?**  
O: KVA/UES specifikacija zahteva grafičku biblioteku; Chart.js je standardna, podržava pie, bar, radar.

**P: Šta je bottomLocations?**  
O: 5 lokacija sa najnižom prosečnom ocenom (sa bar jednim utiskom) — dopuna topLocations za kompletnu sliku.

---

# A1 — Admin obrađuje zahtev za registraciju

## Naziv funkcionalnosti

Admin pregleda pending zahteve, odobrava (kreira User + email) ili odbija (email, bez User-a).

## Preduslovi

- Admin: `admin@newnow.com` / `admin123`
- Pending AccountRequest u bazi

## Koraci testiranja

1. Registracija novog korisnika (K1)
2. Admin login → `/admin` → tab **„📋 Zahtevi"**
3. Klik **„✅ Odobri"**
4. Proveriti mock email u backend konzoli
5. Novi korisnik se prijavljuje
6. Odbij drugi zahtev — **„❌ Odbij"**

## Očekivani rezultat

1. Zahtev u listi pending
2. Admin panel prikazuje karticu
3. User kreiran sa `enabled=true`, role USER; zahtev APPROVED
4. Log4j email log
5. Login uspešan
6. Status REJECTED; nema User; email odbijanja u konzoli

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | USER pristupa `/admin` | adminGuard → `/login` |
| E2 | Approve bez JWT | 403 |
| E3 | Duplo odobrenje | 400 — nije PENDING |

## Demonstracija kroz UI

- **Admin panel** (`/admin`): tab **„📋 Zahtevi (N)"** — **„✅ Odobri"**, **„❌ Odbij"**

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `admin-dashboard.ts` | `approveRequest()`, `rejectRequest()` + JWT header |
| Controller | `AccountRequestController.java` | `approveRequest()`, `rejectRequest()` |
| Service | `EmailService.java` | mock email |
| Guard | `auth.guard.ts` | `adminGuard` |
| DB | `users`, `account_requests` | |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto enabled=false dok admin ne odobri?**  
O: K1 — korisnik ne sme da se prijavi pre odobrenja; enabled flag kontroliše pristup.

---

# A2 — Upravljanje menadžerima

## Naziv funkcionalnosti

Admin dodeljuje i uklanja menadžere sa mesta; uklonjen menadžer postaje USER (ako nema drugih mesta).

## Preduslovi

- Admin + postojeći USER u bazi
- Lokacija bez ili sa menadžerom

## Koraci testiranja

1. Admin → `/admin` → tab **„👥 Menadžeri"**
2. **„Dodeli menadžera"** / **„Promeni menadžera"** — izabrati korisnika
3. Proveriti da korisnik ima ulogu MANAGER i vidi `/manager`
4. **„Ukloni"** menadžera sa mesta
5. Menadžer sa **dva mesta** — ukloni sa jednog → ostaje MANAGER
6. Ukloni sa oba → postaje USER (multi-location fix)

## Očekivani rezultat

1. Lista lokacija sa trenutnim menadžerom
2. `PUT /api/locations/{id}/assign-manager` — 200
3. Navbar pokazuje Manager Dashboard
4. `DELETE .../remove-manager` — manager=null
5. Role ostaje MANAGER (`countByManagerIdAndIdNot > 0`)
6. Role → USER samo kad nema više mesta

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | USER dodeljuje menadžera | 403 |
| E2 | Dodela nepostojećeg userId | 404/500 |
| E3 | GET `/api/users` bez admin role | 403 (NF1 fix) |

## Demonstracija kroz UI

- **`/admin`:** tab **„👥 Menadžeri"** — **„Dodeli/Promeni menadžera"**, **„Ukloni"**

## Implementacija za odbranu

| Sloj | Fajl | Metoda |
|------|------|--------|
| Frontend | `admin-dashboard.ts` | `assignManager()`, `removeManager()` |
| Controller | `LocationController.java` | `assignManager()`, `removeManager()` |
| Controller | `UserController.getAllUsers()` | Lista za dropdown — samo ADMIN |
| Repository | `LocationRepository.countByManagerIdAndIdNot()` | Multi-location provera |
| Config | `SecurityConfig` | `GET /api/users` → hasRole ADMIN |

### Moguća pitanja profesora + Kratak odgovor

**P: Šta ako menadžer upravlja dva mesta?**  
O: Pri uklanjanju sa jednog mesta proveravamo `countByManagerIdAndIdNot` — role MANAGER ostaje dok ima bar jedno drugo mesto.

---

# S1 — Napredna pretraga mesta (Elasticsearch)

## Naziv funkcionalnosti

UES napredna full-text pretraga mesta preko Elasticsearch indeksa `locations`, sa custom srpskim analizerom, highlight-om, sortiranjem i More Like This.

## Preduslovi

- Docker ES na `:9200`
- Spring Boot pokrenut (automatski reindeks)
- Bucket MinIO sa PDF-ovima (opciono za pdfContent pretragu)

## Koraci testiranja

1. Otvoriti `/search`
2. Osnovna pretraga po nazivu
3. Testirati sve podtipove S1a–S1i (vidi sekciju ispod)
4. Klik **„Poseti mesto"** na rezultatu

## Očekivani rezultat

1. Search stranica učitana
2. Rezultati iz `GET /api/search/locations`
3. Svaki podtip vraća očekivane dokumente
4. Navigacija na `/locations/:id`

## Edge case testovi (Task 3)

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | ES nije pokrenut | Prazna lista / greška u logu |
| E2 | Pretraga bez parametara | Svi dokumenti (match_all) |
| E3 | AND + range | Range uvek MUST (AND sa tekstom) |

## Demonstracija kroz UI

- **Navbar:** **„🔍 Pretraži"** → `/search`
- **Dugmad:** **„🔍 Pretraži"**, **„Resetuj"**, **„🔗 Slična mesta"**, **„📥 Preuzmi PDF"**, **„Poseti mesto"**

## Implementacija za odbranu

| Sloj | Fajl | Detalj |
|------|------|--------|
| Frontend | `search.component.ts/html` | Forma, highlight `[innerHTML]` |
| Controller | `SearchController.java` | `searchLocations()`, `similarLocations()` |
| Service | `LocationSearchService.java` | BoolQuery, buildTextQuery, highlight |
| Index | `LocationIndexService.java`, `LocationDocument.java` | MySQL → ES |
| Init | `ElasticsearchInitializer.java` | `reindexAll()` na startu |
| ES | indeks `locations`, `settings.json` | serbian_analyzer |

**Tok:** SearchComponent → `SearchController` → `LocationSearchService.search()` → `ElasticsearchOperations` → ES indeks

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto ES umesto MySQL LIKE?**  
O: ES ima inverted index, fuzzy, phrase, range, highlight — optimizovan za full-text; LIKE je spor full table scan.

---

# S1a — MatchQuery

## Naziv funkcionalnosti

Obična pretraga po rečima — redosled nije obavezan.

## Preduslovi

- ES indeks sa lokacijama (npr. Exit Festival)

## Koraci testiranja

1. `/search` → Naziv: `festival` → **Pretraži**

## Očekivani rezultat

Exit Festival, Primavera Sound Festival i dr. sa `<mark>festival</mark>` u highlight-u.

## Edge case testovi

- Prazan string — ignoriše se
- Više reči — MatchQuery traži obe reči (AND unutar polja)

## Demonstracija kroz UI

Polje **„🏷️ Naziv mesta"** — unos bez navodnika, bez `*` i `~`.

## Implementacija za odbranu

`LocationSearchService.buildTextQuery()` → default grana → `MatchQuery.of(m -> m.field(field).query(value))`

### Moguća pitanja profesora + Kratak odgovor

**P: MatchQuery vs PhraseQuery?**  
O: Match — reči prisutne, redosled nebitan. Phrase — tačan redosled reči.

---

# S1b — PhraseQuery

## Naziv funkcionalnosti

Pretraga tačne fraze (navodnici).

## Preduslovi

- ES indeks

## Koraci testiranja

1. Naziv: `"Exit Festival"` → Pretraži

## Očekivani rezultat

Samo Exit Festival (tačna fraza u tom redosledu).

## Edge case testovi

- `"Exit"` — jedna reč u navodnicima — phrase od jedne reči
- `Exit Festival` bez navodnika — više rezultata (MatchQuery)

## Demonstracija kroz UI

Help box: **„❓ Pomoć za pretragu"** — objašnjenje navodnika.

## Implementacija za odbranu

`buildTextQuery()`: `value.startsWith("\"") && value.endsWith("\"")` → `MatchPhraseQuery`

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto navodnici u UI?**  
O: Korisnik signalizira backend-u da koristi PhraseQuery umesto MatchQuery.

---

# S1c — PrefixQuery (naziv)

## Naziv funkcionalnosti

Pretraga po prefiksu sa zvezdicom na kraju.

## Preduslovi

- ES indeks

## Koraci testiranja

1. Naziv: `Exit*` → Pretraži

## Očekivani rezultat

Sve lokacije čiji naziv **počinje** sa „Exit" (Exit Festival, Exit Club...).

## Edge case testovi

- `*` samo — greška / prazan prefix
- Case insensitive na `.keyword` polju

## Demonstracija kroz UI

Placeholder: `npr. Exit*`

## Implementacija za odbranu

`value.endsWith("*")` → `PrefixQuery` na `field + ".keyword"`, `caseInsensitive(true)`

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto `.keyword` subfield?**  
O: Prefix radi na neanaliziranom tokenu — keyword polje bez stemovanja.

---

# S1d — FuzzyQuery

## Naziv funkcionalnosti

Tolerancija grešaka u kucanju — prefiks `~`.

## Preduslovi

- ES indeks

## Koraci testiranja

1. Naziv: `~festivall` → Pretraži

## Očekivani rezultat

Exit Festival, Primavera Sound Festival (Levenshtein distanca ≤ 2).

## Edge case testovi

- `~xyzabc` — nema rezultata
- Više grešaka od fuzziness — prazno

## Demonstracija kroz UI

Help: **`~festival`** — fuzzy primer

## Implementacija za odbranu

`value.startsWith("~")` → `FuzzyQuery` sa `fuzziness("2")`

### Moguća pitanja profesora + Kratak odgovor

**P: Šta je Levenshtein distanca?**  
O: Minimalan broj operacija (insert/delete/replace) da se jedna reč pretvori u drugu. `festivall`→`festival` = 1.

---

# S1e — BooleanQuery (AND / OR)

## Naziv funkcionalnosti

Logičko kombinovanje tekstualnih polja (naziv, opis, PDF).

## Preduslovi

- ES indeks

## Koraci testiranja

1. Naziv: `festival`, Opis: `barcelona`, Operator **OR** → Pretraži
2. Isti unos, Operator **AND** → Pretraži

## Očekivani rezultat

1. OR — rezultati koji imaju festival ILI barcelona (min 1 match)
2. AND — presek (obično 0 ako nijedna lokacija nema oba)

## Edge case testovi

- Samo range filteri — range uvek MUST
- OR sa jednim poljem — ekvivalent Match

## Demonstracija kroz UI

Dugmad **AND** / **OR** u operator redu.

## Implementacija za odbranu

`BoolQuery`: AND → `must(textQueries)`; OR → `should` + `minimumShouldMatch("1")`; range uvek `must(rangeQueries)`

### Moguća pitanja profesora + Kratak odgovor

**P: Da li OR važi za range filtere?**  
O: Ne — range upiti su uvek AND (MUST) prema specifikaciji S1.

---

# S1f — RangeQuery (ocena, utisci, kategorije)

## Naziv funkcionalnosti

Filtriranje opsegom: prosečna ocena, broj utisaka, nastup, zvuk, prostor, ukupno.

## Preduslovi

- Lokacije sa utiscima (npr. Exit Festival ocena ~7.5)

## Koraci testiranja

1. **Resetuj** filtere
2. Prosečna ocena od `5` do `10` → Pretraži
3. Minimalan broj utisaka od `1`
4. **Prikaži filtre po kategorijama** → nastup od 8

## Očekivani rezultat

1. Forma očišćena
2. Samo lokacije sa ocenom u opsegu
3. Samo sa ≥1 utiskom
4. Dodatni range MUST filter

## Edge case testovi

- ratingMin > ratingMax — prazno
- Lokacije bez utisaka — ocena 0.0

## Demonstracija kroz UI

Polja **Prosečna ocena od/do**, **Minimalan/Maksimalan broj utisaka**, toggle **„Prikaži filtre po kategorijama ocena"**

## Implementacija za odbranu

`buildRangeQuery()` sa `gte`/`lte` na numeričkim poljima `LocationDocument`

### Moguća pitanja profesora + Kratak odgovor

**P: Odakle ocene u ES?**  
O: `LocationIndexService.buildDocument()` agregira iz MySQL utisaka pri indeksiranju.

---

# S1g — PrefixQuery na opis i PDF (keyword polja)

## Naziv funkcionalnosti

Prefix pretraga radi i na poljima **opis** i **PDF sadržaj** preko `.keyword` subfield-a (isto kao naziv).

## Preduslovi

- Lokacija sa opisom ili PDF-om u ES (`pdfContent`)

## Koraci testiranja

1. Opis: `Barcel*` → Pretraži
2. PDF sadržaj: `Petrovaradin*` (tekst iz PDF-a) → Pretraži

## Očekivani rezultat

Rezultati gde opis/PDF sadržaj počinje datim prefiksom.

## Edge case testovi

- PDF nije uploadovan — pdfContent prazan — nema match
- Prefix na analiziranom polju vs keyword — koristimo keyword za prefix

## Demonstracija kroz UI

Polja **„📝 Opis mesta"** i **„📄 Sadržaj PDF dokumenta"** sa `*` sintaksom.

## Implementacija za odbranu

`LocationDocument` — `@MultiField` sa `keyword` suffix na `description` i `pdfContent`; `buildTextQuery()` koristi `field + ".keyword"` za prefix na svim poljima.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto S1g posebno?**  
O: Prefix mora ići na keyword subfield da ne seče reči analizer — ista logika za name, description i pdfContent.

---

# S1h — Highlighter

## Naziv funkcionalnosti

ES vraća fragmente teksta sa `<mark>` tagovima gde je pronađen termin.

## Preduslovi

- Pretraga koja matchuje (npr. `festival`)

## Koraci testiranja

1. Pretražiti `festival`
2. Posmatrati naslov i opis u rezultatima

## Očekivani rezultat

Žuto označeni termini: `Exit <mark>Festival</mark>`; PDF sadržaj zeleno u UI.

## Edge case testovi

- Sort po polju — highlight i dalje prisutan
- Više polja — highlights map po polju

## Demonstracija kroz UI

Kartice rezultata — `[innerHTML]="getHighlight(r, 'name')"` u `search.component.html`

## Implementacija za odbranu

`HighlightParameters` pre/post `<mark>`; polja name, description, pdfContent; frontend `getHighlight()`

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto innerHTML?**  
O: ES vraća HTML markere; Angular mora renderovati HTML umesto plain text.

---

# S1i — More Like This, relevantnost (score), srpski analizer

## Naziv funkcionalnosti

MLT slična mesta; prikaz score; custom `serbian_analyzer` (ćirilica→latinica, lowercase, asciifolding); sort po relevantnosti.

## Preduslovi

- ES indeks sa više sličnih dokumenata

## Koraci testiranja

1. Pretražiti `festival`, klik **„🔗 Slična mesta"**
2. Proveriti score u rezultatima
3. Sort: relevantnost (`_score` desc)
4. Pretraga `EXIT` vs `exit` vs ćirilica — ista pokrivenost

## Očekivani rezultat

1. Lista sličnih lokacija (max 5, bez samog sebe)
2. Score prikazan — veći = relevantniji (`withTrackScores(true)`)
3. Sort po score radi
4. Case i dijakritici normalizovani

## Edge case testovi

- MLT sa malim brojem dokumenata — `minDocFreq=1`
- Sort po nazivu — score i dalje dostupan zbog trackScores

## Demonstracija kroz UI

**„🔗 Slična mesta"** dugme; sort dropdown; help za analizer

## Implementacija za odbranu

| Komponenta | Fajl |
|------------|------|
| MLT | `LocationSearchService.moreLikeThis()` |
| Analyzer | `src/main/resources/elasticsearch/settings.json` |
| Score | `withTrackScores(true)` u NativeQueryBuilder |
| Reindex | `ElasticsearchInitializer` + `POST /api/search/reindex` |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto minDocFreq=1 za MLT?**  
O: Imamo malo dokumenata (~13 lokacija); default 5 bi blokirao MLT.

**P: Šta radi serbian_analyzer?**  
O: char_filter ćirilica→latinica, standard tokenizer, lowercase + asciifolding — `sume`=`šume`, `EXIT`=`exit`.

---

# NF1 — Autentifikacija i autorizacija (JWT)

## Naziv funkcionalnosti

Autentifikacija email+lozinka; autorizacija JWT tokenom; zaštita osetljivih endpointa.

## Preduslovi

- Spring Security + JWT konfigurisani

## Koraci testiranja

1. Pristup `/api/account-requests/pending` bez tokena → 403
2. Pristup `GET /api/users` kao USER → 403
3. Pristup `GET /api/users` kao ADMIN → 200 (bez password polja)
4. Proveriti da `User` JSON nikad ne sadrži `password` (`@JsonIgnore`)
5. Zaštićeni endpoint sa validnim Bearer tokenom → 200

## Očekivani rezultat

1–2. Odbijeno
3. Lista korisnika bez lozinke
4. Password absent u svim API odgovorima
5. Uspešan pristup

## Edge case testovi

| # | Scenario | Očekivano |
|---|----------|-----------|
| E1 | Istekao token | 401 |
| E2 | Pogrešan Bearer format | 401 |
| E3 | USER na POST /api/locations | 403 u kontroleru |

## Demonstracija kroz UI

- Neulogovan pristup `/admin` → redirect `/login`
- Admin panel radi sa tokenom u headeru

## Implementacija za odbranu

| Fajl | Uloga |
|------|-------|
| `SecurityConfig.java` | URL pravila, hasRole |
| `JwtUtil.java` | Generisanje/validacija |
| `JwtAuthenticationFilter.java` | Filter chain |
| `CustomUserDetailsService.java` | UserDetails |
| `User.java` | `@JsonIgnore` na password |

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto ručna autorizacija u kontrolerima pored SecurityConfig?**  
O: Treba provera „menadžer ovog mesta" — finija granula od same role.

---

# NF2 — Logovanje (log4j API)

## Naziv funkcionalnosti

Beleženje važnih događaja preko log4j2 API-ja.

## Preduslovi

- `spring-boot-starter-log4j2` u `pom.xml`
- `log4j2.xml` konfiguracija

## Koraci testiranja

1. Registracija → proveriti log `K1 — novi zahtev`
2. Login → `K2 — uspešan login`
3. Admin odobrenje → `A1 — zahtev odobren`
4. ES pretraga → `ES pretraga vratila N rezultata`
5. MinIO upload → `Slika uploadovana u MinIO`

## Očekivani rezultat

INFO/WARN/ERROR poruke u backend konzoli sa klasom i timestamp-om.

## Edge case testovi

- Greška 500 — ERROR log sa stack trace
- Mock email — logovan preko EmailService

## Demonstracija kroz UI

Nema UI — demonstrirati backend konzolu tokom login/registracije.

## Implementacija za odbranu

`LogManager.getLogger()` u kontrolerima: `AuthController`, `AccountRequestController`, `LocationController`, `SearchController`, `AnalyticsController`, itd.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto log4j a ne System.out?**  
O: Specifikacija eksplicitno traži log4j API; nivoi (INFO/WARN/ERROR), konfiguracija u log4j2.xml.

---

# NF3 — Build alat (Maven)

## Naziv funkcionalnosti

Projekat se gradi Maven-om (Maven Wrapper).

## Preduslovi

- Java 21
- `mvnw.cmd` u root-u projekta

## Koraci testiranja

1. `.\mvnw.cmd clean compile` — uspeh
2. `.\mvnw.cmd test` — unit testovi
3. `.\mvnw.cmd spring-boot:run` — aplikacija startuje
4. Proveriti `pom.xml` — Spring Boot, Security, JWT, MySQL, ES, MinIO, PDFBox, log4j2

## Očekivani rezultat

BUILD SUCCESS; aplikacija na `:8080`.

## Edge case testovi

- Bez globalnog Maven-a — wrapper radi
- `frontend` — odvojen npm/Angular build

## Demonstracija kroz UI

N/A — demonstrirati u terminalu.

## Implementacija za odbranu

`pom.xml`, `mvnw.cmd`, `mvnw` — standardni Maven lifecycle.

### Moguća pitanja profesora + Kratak odgovor

**P: Zašto Maven Wrapper?**  
O: Reproducibilan build bez globalno instaliranog Maven-a — važno za ocenu i CI.

---

# Elasticsearch testiranje (Task 5)

## Priprema

```powershell
# Provera ES
Invoke-RestMethod http://localhost:9200
# Očekuje: "number": "8.13.0"

# Provera indeksa
Invoke-RestMethod http://localhost:9200/locations/_count
```

## Test matrica po tipu upita

| Tip | UI unos (polje Naziv) | API primer | Očekivano |
|-----|----------------------|------------|-----------|
| MatchQuery | `festival` | `?name=festival` | Više rezultata sa highlight |
| PhraseQuery | `"Exit Festival"` | `?name="Exit Festival"` | Samo Exit Festival |
| PrefixQuery | `Exit*` | `?name=Exit*` | Svi sa prefiksom Exit |
| FuzzyQuery | `~festivall` | `?name=~festivall` | Tolerancija greške |
| AND | festival + barcelona, AND | `?name=festival&description=barcelona&operator=AND` | Presek |
| OR | festival + barcelona, OR | `operator=OR` | Unija |
| Range ocena | ocena 5–10 | `?ratingMin=5&ratingMax=10` | Filtrirane ocene |
| Range utisci | min 1 | `?reviewsMin=1` | Samo sa utiscima |
| Kategorije | perfMin=8 | `?perfMin=8` | Filter nastupa |
| Prefix opis | `Barcel*` u Opis | `?description=Barcel*` | S1g |
| Prefix PDF | tekst iz PDF | `?pdf=Petrovaradin*` | S1g pdfContent |
| Highlight | bilo koji match | — | `<mark>` u UI |
| MLT | dugme Slična mesta | `GET .../locations/{id}/similar` | Do 5 sličnih |
| Sort | po oceni desc | `?sortBy=rating&sortOrder=desc` | Sortirana lista |
| Score | match query | — | Broj score na kartici |
| Reindex | admin POST | `POST /api/search/reindex` | Sve lokacije u ES |

## Sinhronizacija MySQL → ES

| Događaj | Akcija |
|---------|--------|
| Start aplikacije | `ElasticsearchInitializer` → `reindexAll()` |
| Novi utisak | `LocationIndexService.indexLocation()` |
| PDF upload | indexLocation + PDFBox parse |
| Brisanje lokacije | `removeFromIndex()` |
| Ručno | `POST /api/search/reindex` (ADMIN) |

---

# MinIO testiranje (Task 5)

## Priprema

```powershell
# MinIO konzola: http://localhost:9001
# Login: minioadmin / minioadmin
# Bucket: newnow-files
```

## Upload slike (K3/K4)

| Korak | Akcija | Očekivano |
|-------|--------|-----------|
| 1 | Admin → Dodaj lokaciju → **Upload slike** | `POST /api/files/images` |
| 2 | Proveriti MinIO konzolu | Fajl u `newnow-files/images/` |
| 3 | URL u formi | `/api/files/images/{filename}` |
| 4 | Prikaz na `/locations` | Slika učitana preko `resolveMediaUrl()` |

**Autorizacija:** ADMIN ili MANAGER sa JWT.

## Upload PDF (S1 / admin)

| Korak | Akcija | Očekivano |
|-------|--------|-----------|
| 1 | Admin → Uredi lokaciju → **Upload PDF** | `POST /api/search/locations/{id}/pdf` |
| 2 | MinIO | `pdfs/` prefiks u bucket-u |
| 3 | ES | `pdfContent` popunjen (PDFBox) |
| 4 | `/search` pretraga PDF polja | Match na tekst iz PDF-a |

## Download

| Tip | Endpoint | UI |
|-----|----------|-----|
| Slika | `GET /api/files/images/{file}` | Kartice lokacija/događaja |
| PDF | `GET /api/search/locations/{id}/pdf` | **„📄 Preuzmi PDF"** na location-details; **„📄"** u admin listi; **„📥 Preuzmi PDF"** u search rezultatima |

## Edge case-ovi MinIO

- Upload bez auth → 403
- Pogrešan MIME tip slike → 400
- Download nepostojećeg fajla → 404
- Bucket ne postoji → `MinioService.ensureBucketExists()` pri startu

---

# Finalna tabela (Task 6)

| Zahtev | Implementiran | Testiran | Prošao test | Potrebna dorada |
|--------|:-------------:|:--------:|:-----------:|:---------------:|
| **K1** Zahtev za registraciju | ✅ | ✅ | ✅ | — |
| **K2** Prijava i odjava | ✅ | ✅ | ✅ | — |
| **K3** Rukovanje mestima | ✅ | ✅ | ✅ | — |
| **K4** Rukovanje događajima | ✅ | ✅ | ✅ | — |
| **K5** Ostavljanje utiska | ✅ | ✅ | ✅ | — |
| **K6** Pretraga i filtriranje | ✅ | ✅ | ✅ | — |
| **K7** Sortiranje utisaka | ✅ | ✅ | ✅ | — |
| **K8** Početna stranica | ✅ | ✅ | ✅ | — |
| **K9** Promena lozinke | ✅ | ✅ | ✅ | — |
| **K10** Profil korisnika | ✅ | ✅ | ✅ | — |
| **M1** Menadžer — mesto i događaji | ✅ | ✅ | ✅ | — |
| **M2** Menadžer — utisci | ✅ | ✅ | ✅ | — |
| **M3** Komentari (thread) | ✅ | ✅ | ✅ | — |
| **M4** Analitika | ✅ | ✅ | ✅ | — |
| **A1** Admin — zahtevi | ✅ | ✅ | ✅ | — |
| **A2** Admin — menadžeri | ✅ | ✅ | ✅ | — |
| **S1** ES napredna pretraga | ✅ | ✅ | ✅ | — |
| **S1a** MatchQuery | ✅ | ✅ | ✅ | — |
| **S1b** PhraseQuery | ✅ | ✅ | ✅ | — |
| **S1c** PrefixQuery (naziv) | ✅ | ✅ | ✅ | — |
| **S1d** FuzzyQuery | ✅ | ✅ | ✅ | — |
| **S1e** Boolean AND/OR | ✅ | ✅ | ✅ | — |
| **S1f** RangeQuery | ✅ | ✅ | ✅ | — |
| **S1g** Prefix opis/PDF | ✅ | ✅ | ✅ | — |
| **S1h** Highlighter | ✅ | ✅ | ✅ | — |
| **S1i** MLT + score + analizer | ✅ | ✅ | ✅ | — |
| **NF1** JWT auth | ✅ | ✅ | ✅ | — |
| **NF2** log4j | ✅ | ✅ | ✅ | — |
| **NF3** Maven build | ✅ | ✅ | ✅ | — |

---

## Brzi checklist pre odbrane

- [ ] Docker: ES + MinIO pokrenuti
- [ ] MySQL baza `newnow` sa seed podacima
- [ ] Backend `:8080`, Frontend `:4200`
- [ ] Admin login radi
- [ ] Bar jedan menadžer sa događajima i utiscima
- [ ] `/search` vraća rezultate sa highlight-om
- [ ] MinIO konzola prikazuje bucket `newnow-files`
- [ ] Log4j poruke vidljive u konzoli tokom demo toka

---

*Dokument generisan za odbranu projekta NewNow — UES 2026 specifikacija.*
