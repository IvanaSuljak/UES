# NewNow — Kompletno objašnjenje projekta za odbranu

> **Za koga:** student koji sutra brani projekat  
> **Projekat:** Discover Barcelona / NewNow — platforma za mesta, događaje, utiske i naprednu pretragu  
> **Stack:** Spring Boot (Java) + Angular + MySQL + Elasticsearch + MinIO + JWT  
> **Dublji UES vodič:** vidi `ODBRANA-UES-VODIC.md` (1150+ linija samo za pretragu)


---

## 1. Kako koristiti ovaj dokument

Svaki zahtev ima isti format:

| Deo | Šta znači |
|-----|-----------|
| **Šta** | Šta specifikacija traži |
| **Zašto** | Zašto smo tako uradili (logika dizajna) |
| **Gde u kodu** | Tačni fajlovi i klase |
| **Kako demo** | Šta otvoriš u browseru sutra |
| **Ako pita profesor** | Kratki gotov odgovor |

**UES (S1)** je najdetaljniji deo — tu profesor najčešće pita. Ostalo je pokriveno dovoljno za odbranu, bez suvišnih detalja.

---

## 2. Uvod — šta pričati u prve 2 minute

### Elevator pitch (zapamti ovo)

> *„NewNow je web aplikacija za istraživanje mesta i događaja. Korisnici pregledaju lokacije, ostavljaju utiske, filtriraju događaje. Admin upravlja sistemom, menadžer svojim mestom. Tehnički: Spring Boot REST API, Angular frontend, MySQL kao glavna baza, Elasticsearch za naprednu pretragu (UES), MinIO za slike i PDF fajlove, JWT za autentifikaciju."*

### Tri uloge u sistemu

| Uloga | Ko je | Šta radi |
|-------|-------|----------|
| **USER** | Običan korisnik | Utisci, komentari, profil, pretraga |
| **MANAGER** | Menadžer mesta | Ažurira svoje mesto, događaje, analitiku, utiske |
| **ADMIN** | Administrator | Sve + zahtevi za registraciju, dodela menadžera, CRUD svih mesta |

### Zašto tri baze/skladišta?

| Tehnologija | Uloga | Zašto ne samo MySQL? |
|-------------|-------|----------------------|
| **MySQL** | Izvor istine — korisnici, mesta, utisci, transakcije | Relacioni model, integritet |
| **Elasticsearch** | Napredna pretraga (UES S1) | Inverted index, fuzzy, phrase, highlight, scoring |
| **MinIO** | Binarni fajlovi (slike, PDF) | S3-kompatibilan storage; PDF tekst ide u ES |

---

## 3. Arhitektura celog sistema

```
┌─────────────┐     HTTP/JWT      ┌──────────────────┐
│   Angular   │ ◄──────────────► │  Spring Boot     │
│   :4200     │                   │  REST :8080      │
└─────────────┘                   └────────┬─────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
              ┌──────────┐          ┌────────────┐          ┌──────────┐
              │  MySQL   │          │ Elasticsearch│        │  MinIO   │
              │  :3306   │          │  :9200       │        │  :9000   │
              └──────────┘          └────────────┘          └──────────┘
```

### Tok podataka (zapamti za odbranu)

1. **Korisnik** → Angular forma → HTTP zahtev sa JWT u headeru (`Authorization: Bearer ...`)
2. **Spring Security** (`SecurityConfig` + `JwtAuthenticationFilter`) proverava token i ulogu
3. **Controller** prima zahtev → **Service** poslovna logika → **Repository** → MySQL
4. **ES reindeks** — posle promene lokacije/utiska/PDF-a → `LocationIndexService.indexLocation()`
5. **MinIO** — upload slike/PDF → čuva binarno; PDF tekst se čita PDFBox-om pri indeksiranju



## 4. Pokretanje i nalozi

### Redosled pokretanja

```
1. Docker Desktop
2. cd newnow → docker-compose up -d        (ES + MinIO)
3. Sačekaj ~30s
4. .\mvnw.cmd spring-boot:run              (backend + reindeks ES)
5. cd frontend → npm start                 (Angular :4200)
```

### Demo nalozi

| Uloga | Email | Lozinka |
|-------|-------|---------|
| Admin | `admin@newnow.com` | `admin123` |
| Menadžer (Exit Festival) | `petar@manager.com` | `petar123` |
| MinIO konzola | `minioadmin` | `minioadmin` |

**JWT traje ~10 sati.** Ako admin panel kaže „Sesija istekla" — ponovo login.

---

## 5. Struktura odbrane — preporučeni red

| Minut | Tema | Akcija |
|-------|------|--------|
| 0–2 | Uvod + arhitektura | Govor, dijagram |
| 2–6 | **UES (S1)** | Otvori `/search`, pokaži Match, Phrase, Prefix, Fuzzy, Range, Highlight, MLT |
| 6–7 | Srpski analyzer | Objasni pipeline, pokaži `EXIT` = `exit` |
| 7–8 | Reindeks + PDF/MinIO | Objasni zašto ES ≠ MySQL |
| 8–9 | K1/K2 + NF1 | Registracija → odobrenje → login JWT |
| 9–10 | K5 utisci + M4 analitika | Brzi demo |
| 10+ | Pitanja | Koristi sekciju 14 |


## 6. UES — napredna pretraga (GLAVNI FOKUS)

### Šta specifikacija traži

Full-text pretraga mesta preko **Elasticsearch** sa:
- S1a MatchQuery, S1b PhraseQuery, S1c PrefixQuery, S1d FuzzyQuery
- S1e Boolean AND/OR, S1f RangeQuery
- S1g Prefix na opis i PDF, S1h Highlight, S1i MLT + score
- Custom **serbian_analyzer**
- PDF sadržaj iz MinIO indeksiran u ES

### Zašto Elasticsearch?

MySQL `LIKE` radi full table scan, nema relevance score, nema fuzzy/phrase u jednom upitu. ES ima **inverted index** — mapa `termin → [dokumenti]` — brza pretraga sa scoring-om.

koristi inverted index — odmah nađe dokumente po reči i rangira ih po score-u, sa fuzzy, phrase i filterima u jednom upitu

### Gde u kodu

| Sloj | Fajl | Uloga |
|------|------|-------|
| UI | `frontend/.../search/search.component.ts/html` | Forma, sintaksa, highlight, MLT dugme |
| API | `SearchController.java` | `GET /api/search/locations`, `/similar`, PDF, reindex |
| Logika | `LocationSearchService.java` | `search()`, `buildTextQuery()`, `moreLikeThis()` |
| Indeks | `LocationIndexService.java` | `buildDocument()`, `reindexAll()` |
| Model | `LocationDocument.java` | ES dokument + `@Setting` analizer |
| Startup | `ElasticsearchInitializer.java` | Reindeks pri startu |
| Analyzer | `resources/elasticsearch/settings.json` | `serbian_analyzer` |

---

### 6.1 ES dokument (LocationDocument)

**Indeks:** `locations`

| Polje | Tip | Analyzer | Za šta |
|-------|-----|----------|--------|
| `name` | text + `.keyword` | serbian_analyzer | Match, fuzzy, phrase; prefix na `.keyword` |
| `description` | text + `.keyword` | serbian_analyzer | Isto + prefix po tokenima |
| `pdfContent` | text + `.keyword` | serbian_analyzer | Pretraga PDF teksta |
| `totalReviews`, `averageRating` | broj | — | S1f range |
| `avgPerformance`, `avgSoundLight`, `avgSpace`, `avgOverall` | broj | — | S1f kategorije |

**Zašto MultiField (text + keyword)?**
- `name` — analizirano na tokene → Match, fuzzy
- `name.keyword` — ceo string → PrefixQuery (`Exit*`)

**Ocene u ES:** Denormalizovane iz MySQL utisaka u `buildDocument()` — brz RangeQuery bez JOIN-a.

U Elasticsearch-u svako mesto je dokument sa poljima name, description i pdfContent (tekst za pretragu, sa serbian_analyzer-om) i brojevima totalReviews, averageRating i ostalim prosečnim ocenama (za filtere tipa „ocena od 5" — S1f). Naziv i opis se čuvaju na dva načina: kao text (podeljeno na reči — za Match, Phrase, Fuzzy) i kao keyword (ceo string — za Exit*). Ocene ne računamo u trenutku pretrage, već ih pri reindeksu izračunamo iz utisaka u MySQL i upišemo u ES, da pretraga bude brza bez spajanja tabela.

### 6.2 Srpski analyzer — obavezno znati

**Fajl:** `src/main/resources/elasticsearch/settings.json`

**Pipeline:**
1. `char_filter` `serbian_cyrillic_to_latin` — ćirilica → latinica
2. `tokenizer` `standard` — deli na reči
3. `filter` `lowercase` + `asciifolding` — mala slova, č→c, š→s

**Primeri:**
- `EXIT` = `exit` = `Exit`
- `Излаз` = `Izlaz`
- `sume` = `šume`

**Za odbranu:** *„Custom serbian_analyzer normalizuje pismo i dijakritike. Nije ugrađeni ES stemmer — fokus je na konzistentnoj pretrazi srpskog teksta."*

**Zašto char_filter a ne filter?** Ćirilica se mora konvertovati **pre** tokenizacije.

---

### 6.3 Sintaksa pretrage (buildTextQuery)

Metoda `LocationSearchService.buildTextQuery()` prepoznaje sintaksu:

| Unos korisnika | ES upit | Zahtev |
|----------------|---------|--------|
| `exit festival` | MatchQuery | S1a |
| `"Exit Festival"` | MatchPhraseQuery | S1b |
| `Exit*` | PrefixQuery na `name.keyword` | S1c |
| `~Exittt` | MatchQuery + fuzziness AUTO | S1d |
| `festival*` (opis/PDF) | MatchBoolPrefixQuery | S1g |

**S1e AND/OR:**
```java
if (useAnd) boolBuilder.must(textQueries);      // AND
else { boolBuilder.should(textQueries); boolBuilder.minimumShouldMatch("1"); }  // OR
boolBuilder.must(rangeQueries);                 // Range UVEK AND
```

**Demo AND:** Naziv=`Exit`, Opis=`muzički` → Exit Festival  
**Demo OR:** Naziv=`Exit`, Opis=`muzika` → više rezultata  
**Demo 0 (ispravno):** Naziv=`Exit`, Opis=`muzika` sa AND → 0 (u opisu je „muzički")

**S1f Range:** `ratingMin`, `reviewsMin`, `perfMin`, `soundMin`… → `buildRangeQuery()` sa gte/lte

**S1h Highlight:**
```java
.withPreTags("<mark>").withPostTags("</mark>")
```
Frontend: `[innerHTML]="getHighlight(r, 'name')"`

**S1i MLT:** `GET /api/search/locations/{id}/similar` — `MoreLikeThisQuery` na name, description, pdfContent. `minDocFreq=1` jer imamo malo dokumenata (~13 lokacija).

**Score:** `withTrackScores(true)` — TF-IDF relevantnost na kartici.

---

### 6.4 Reindeksiranje — često pitanje

**Zašto?** ES nije automatski sinhronizovan sa MySQL.

| Kada | Gde |
|------|-----|
| Start aplikacije | `ElasticsearchInitializer.reindexAll()` |
| CRUD lokacije | `LocationController` → `indexLocation()` / `removeFromIndex()` |
| Utisak | `ReviewController` → `indexLocation()` |
| PDF upload | `SearchController` → `indexLocation()` |
| Ručno | `POST /api/search/reindex` (admin) |

**buildDocument() radi:**
1. Kopira polja lokacije iz MySQL
2. Agregira ocene iz utisaka
3. Ako ima PDF → MinIO download → PDFBox → `pdfContent`
4. `searchRepository.save(doc)`

**Za odbranu:** *„Reindeks održava ES usklađenim sa MySQL — posebno ocene i PDF tekst."*
Elasticsearch nije automatski vezan za MySQL — zato posle svake važne promene ponovo kopiramo podatke o mestu u ES (reindeks): pri startu aplikacije, kad admin/menadžer doda, izmeni ili obriše lokaciju, kad neko ostavi utisak, ili kad se uploaduje PDF (admin može i ručno preko POST /api/search/reindex). U tom trenutku buildDocument() uzme podatke iz MySQL, izračuna prosečne ocene iz utisaka, po potrebi pročita tekst PDF-a iz MinIO-a, i sačuva sve u ES — da pretraga uvek vidi iste podatke kao baza, posebno ocene i sadržaj PDF-a.
---

### 6.5 PDF i MinIO u UES

```
Upload PDF → MinIO (pdfs/uuid.pdf) → MySQL pdfFileName
    → indexLocation() → PDFBox → pdfContent u ES
    → pretraga polja „Sadržaj PDF" u /search
```

- Download: `GET /api/search/locations/{id}/pdf`
- MinIO konzola: http://localhost:9001, bucket `newnow-files`

PDF se ne čuva u MySQL-u kao tekst — fajl ide u MinIO (bucket newnow-files), a u bazi ostaje samo ime fajla (pdfFileName). Posle uploada pozove se reindeks: aplikacija preuzme PDF iz MinIO-a, PDFBox izvuče tekst i upiše ga u Elasticsearch u polje pdfContent, pa korisnik na /search može pretraživati polje „Sadržaj PDF" čak i kad te reči nisu u opisu mesta. 

### 6.6 UES — brza test matrica za sutra

| Test | Unos | Očekuješ |
|------|------|----------|
| Match | `exit festival` | Više rezultata + score |
| Phrase | `"Exit Festival"` | Samo Exit Festival |
| Prefix | `Exit*` | Exit Festival, Exit Club |
| Fuzzy | `~Exittt` | Exit Festival |
| AND | Exit + muzički | Exit Festival |
| Range | ocena≥5, utisci≥1 | Exit Festival |
| Range 0 | nastup≥8 | 0 rezultata (OK!) |
| Highlight | bilo koji match | `<mark>` |
| MLT | Slična mesta | do 5 sličnih |
| Analyzer | EXIT vs exit | isto |

> Detalji, curl komande, ceo JSON analizera → **`ODBRANA-UES-VODIC.md`**

---

## 7. NF1 — bezbednost (JWT)

### Šta

Autentifikacija email+lozinka; autorizacija JWT tokenom; zaštita endpointa po ulogama.

### Zašto JWT stateless?

Server ne čuva sesiju — token nosi identitet i ulogu. Angular čuva u `localStorage`, šalje u svakom zahtevu.

### Gde u kodu

| Fajl | Uloga |
|------|-------|
| `SecurityConfig.java` | Ko sme na koji URL (`hasRole`, `permitAll`) |
| `JwtAuthenticationFilter.java` | Čita token, postavlja SecurityContext; 401 ako istekao |
| `JwtUtil.java` | Generiše i validira token |
| `AuthController.java` | Login, logout |
| `auth.service.ts` | Frontend — čuva token |
| `auth.guard.ts` | `authGuard`, `adminGuard` na rutama |

### Ključna pravila iz SecurityConfig

| Endpoint | Ko |
|----------|-----|
| `POST /api/account-requests` | Svi (K1) |
| `GET /api/search/**` | Svi (S1 javna pretraga) |
| `GET /api/locations/**`, `/api/events/**` | Svi (čitanje) |
| `GET /api/users` | Samo ADMIN |
| `POST /api/search/reindex` | ADMIN |
| `/api/users/profile`, `change-password` | Ulogovani |

### Ako pita profesor

**P: Kako radi JWT?**  
O: Login proverava BCrypt lozinku → JwtUtil generiše token sa emailom i rolom → filter na svakom zahtevu validira potpis i rok.

**P: Zašto GET pretraga javna?**  
O: Specifikacija — pretraga mesta dostupna i neregistrovanim korisnicima.

**P: Zašto @JsonIgnore na password?**  
O: NF1 — lozinka se nikad ne vraća u JSON odgovoru.

---

## 8. K1–K10 — funkcionalnosti korisnika

### K1 — Zahtev za registraciju

| | |
|---|---|
| **Šta** | Korisnik ne kreira nalog direktno — šalje zahtev adminu |
| **Zašto** | Specifikacija — admin kontroliše ko ulazi u sistem |
| **Gde** | `register.ts` → `AccountRequestController.createRequest()` → tabela `account_requests` (PENDING/APPROVED/REJECTED) |
| **Demo** | `/register` → poruka uspeha → login pre odobrenja ne radi |
| **Profesor** | *„Zašto AccountRequest?"* → Privremeni entitet pre nego postane User sa enabled=true. REJECTED email može ponovo poslati zahtev — ažurira se postojeći red. |

---

### K2 — Prijava i odjava

| | |
|---|---|
| **Šta** | Login email+lozinka → JWT; logout briše token |
| **Zašto** | Stateless auth; PENDING/REJECTED poruke **tek posle** provere lozinke (bez curenja statusa) |
| **Gde** | `login.ts`, `AuthController.login()`, `JwtUtil` |
| **Demo** | Login admin → navbar prikazuje ime → Odjavi se |
| **Profesor** | *„Pogrešan email?"* → Uvek ista generička poruka za nepostojeći email/pogrešnu lozinku. PENDING tek ako je lozinka tačna. |

---

### K3 — Rukovanje mestima

| | |
|---|---|
| **Šta** | CRUD lokacija; admin sve; menadžer samo adresa/tip/opis |
| **Zašto** | Različite permisije po ulozi; slika u MinIO |
| **Gde** | `LocationController.java`, `admin-dashboard`, `manager-dashboard`, `LocationServiceImpl` |
| **Demo** | Admin `/admin` → Dodaj lokaciju + slika; menadžer menja opis, ne naziv |
| **Profesor** | *„Brisanje mesta?"* → MySQL delete + `removeFromIndex()` u ES. |

---

### K4 — Rukovanje događajima

| | |
|---|---|
| **Šta** | CRUD događaja na lokaciji; menadžer samo svoje; admin sve (+ tab Događaji u admin panelu) |
| **Zašto** | Događaj pripada lokaciji; autorizacija po managerId |
| **Gde** | `EventController.java`, `EventServiceImpl.java`, `events-page.component.ts` |
| **Demo** | `/events` filter po tipu/datumu; menadžer dodaje događaj na Exit Festival |
| **Profesor** | *„Besplatno događaj?"* → Cena NULL ili 0 = besplatno u filteru. Tip `Concert` normalizovan (`EventTypeUtil`). |

---

### K5 — Ostavljanje utiska

| | |
|---|---|
| **Šta** | Ocene 1–10 po kategorijama; opcioni komentar; jednom po mestu |
| **Zašto** | Utisci hrane ocene u ES (reindeks); validacija opsega |
| **Gde** | `ReviewController.java`, `location-details.component.ts` |
| **Demo** | Detalji mesta → oceni → prosečna ocena se ažurira |
| **Profesor** | *„Zašto reindeks posle utiska?"* → ES ima denormalizovane ocene za S1f RangeQuery. |

---

### K6 — Pretraga i filtriranje mesta i događaja

| | |
|---|---|
| **Šta** | Filteri na `/locations` i `/events` (tip, datum, cena…) |
| **Zašto** | K6 = MySQL filteri; UES = ES napredna pretraga — **različite stvari** |
| **Gde** | `locations.ts`, `events-page.component.ts`, `LocationRepository`, `EventRepository` |
| **Demo** | `/events` → filter tip Koncert, datum od/do |
| **Profesor** | *„Razlika K6 i S1?"* → K6 obični filteri u MySQL; S1 Elasticsearch full-text sa fuzzy, phrase, highlight. |

---

### K7 — Sortiranje utisaka

| | |
|---|---|
| **Šta** | Sort po datumu ili oceni na stranici mesta |
| **Gde** | `location-details.component.ts`, `ReviewController` |
| **Demo** | Detalji mesta → sortiraj utiske |

---

### K8 — Početna stranica

| | |
|---|---|
| **Šta** | Home sa istaknutim mestima/događajima |
| **Gde** | `HomeController.java`, `home.component.ts` |
| **Demo** | `/` — prikaz sadržaja |

---

### K9 — Promena lozinke

| | |
|---|---|
| **Šta** | Ulogovani korisnik menja lozinku (stara + nova) |
| **Gde** | `UserController.changePassword()`, `profile.ts` |
| **Demo** | Profil → promena lozinke |

---

### K10 — Profil korisnika

| | |
|---|---|
| **Šta** | Pregled i izmena imena, emaila |
| **Gde** | `UserController` profile endpoints, `profile.ts` |
| **Demo** | `/profile` — izmeni ime |

---

## 9. M1–M4 — menadžer

### M1 — Ažuriranje mesta i događaji

| | |
|---|---|
| **Šta** | Menadžer menja adresu/tip/opis; CRUD događaja na **svom** mestu |
| **Gde** | `manager-dashboard.ts`, `LocationController`, `EventController.isManagerOf()` |
| **Demo** | Login petar@manager.com → `/manager` |
| **Profesor** | *„K3 vs M1?"* → Isti backend, M1 opisuje menadžersku perspektivu. |

---

### M2 — Rukovanje utiscima (menadžer)

| | |
|---|---|
| **Šta** | Sakrivanje/brisanje utisaka na svom mestu |
| **Gde** | `ReviewController` — provera da je menadžer lokacije |
| **Demo** | Manager dashboard → utisci → sakrij |

---

### M3 — Komentari (rekurzivni reply)

| | |
|---|---|
| **Šta** | Komentari na utiscima; reply na reply — neograničena dubina |
| **Zašto** | `parent_id` self-reference u bazi; rekurzivno renderovanje u UI |
| **Gde** | `CommentController.java`, `comment-thread.component.ts` |
| **Demo** | Utisak → komentar → odgovori na komentar |
| **Profesor** | *„Kako neograničena dubina?"* → Svaki komentar ima optional parentId; frontend rekurzivno prikazuje thread. |

---

### M4 — Analitika mesta

| | |
|---|---|
| **Šta** | Chart.js grafici — događaji po periodu, prosečne ocene, bottom locations |
| **Gde** | `AnalyticsController.java`, `analytics.component.ts` |
| **Demo** | `/analytics` → izaberi **Godišnje** ako je mesečni prazan |
| **Profesor** | *„Odakle podaci?"* → SQL upiti u AnalyticsController, agregacija po datumu/kategoriji. |

---

## 10. A1–A2 — admin

### A1 — Obrađivanje zahteva za registraciju

| | |
|---|---|
| **Šta** | Admin odobrava/odbija PENDING zahteve → kreira User |
| **Gde** | `admin-dashboard.ts` tab Zahtevi, `AccountRequestController.approve/reject()` |
| **Demo** | Register novog korisnika → admin odobri → login radi |
| **Profesor** | *„Šta pri odobrenju?"* → Kreira se User sa BCrypt lozinkom iz zahteva, enabled=true. |

---

### A2 — Upravljanje menadžerima

| | |
|---|---|
| **Šta** | Admin dodeljuje/uklanja menadžera sa lokacije |
| **Gde** | `admin-dashboard.ts`, `LocationController.assignManager/removeManager()` |
| **Demo** | Admin → lokacija → dodeli menadžera |
| **Profesor** | *„Uklanjanje menadžera?"* → Ako nema više mesta, role se može vratiti na USER. |

---

## 11. NF2–NF3 — logovanje i build

### NF2 — Logovanje

| | |
|---|---|
| **Šta** | Log4j2 u backendu — info/warn/error u konzoli i fajlu |
| **Gde** | `log4j2.xml`, `Logger` u svim controllerima |
| **Profesor** | *„Gde vidite logove?"* → Konzola pri `spring-boot:run`; npr. „ES pretraga vratila X rezultata". |

### NF3 — Build alat

| | |
|---|---|
| **Šta** | Maven (`pom.xml`, `mvnw.cmd`) |
| **Profesor** | *„Kako build?"* → `./mvnw.cmd clean package` → JAR u `target/`. |

---

## 12. Mapa fajlova — gde je šta u kodu

### Backend (Spring Boot)

```
controller/
  AuthController.java           K2 login, init admin
  AccountRequestController.java K1, A1
  UserController.java           K9, K10, NF1 GET /users
  LocationController.java       K3, A2, ES indexLocation
  EventController.java          K4, M1
  ReviewController.java         K5, M2, ES reindeks
  CommentController.java        M3
  SearchController.java         UES S1, PDF, reindex
  AnalyticsController.java      M4
  FileController.java           MinIO slike
  HomeController.java           K8
  ManagerController.java        Manager specifično

elasticsearch/
  LocationSearchService.java    S1a–i upiti
  LocationIndexService.java     buildDocument, reindeks
  LocationDocument.java         ES model + analyzer
  ElasticsearchInitializer.java startup reindeks

security/
  SecurityConfig.java           NF1 autorizacija
  JwtAuthenticationFilter.java  JWT filter
  JwtUtil.java                  token

resources/elasticsearch/
  settings.json                 serbian_analyzer

service/
  MinioService.java             MinIO upload/download
```

### Frontend (Angular)

```
components/
  search/              UES S1
  admin/               A1, A2, K3 admin, K4 događaji
  manager/             M1, M2
  analytics/           M4
  location-details/    K5, M3
  comment-thread/      M3 rekurzija
  locations/           K6 mesta
  events-page/         K6 događaji
  home/                K8
  login/, register/    K2, K1
  profile/             K9, K10
  navbar/              navigacija

services/
  auth.service.ts      JWT
guards/
  auth.guard.ts        authGuard, adminGuard
utils/
  media-url.ts         MinIO URL resolve
```

---

## 13. Demo script (5–10 minuta)

### Varijanta A — fokus UES (profesor traži pretragu)

1. Otvori `/search` — objasni ES indeks `locations`
2. `"Exit Festival"` — PhraseQuery
3. `Exit*` — PrefixQuery
4. `~Exittt` — Fuzzy
5. `exit festival` — Match + score + highlight
6. Range: ocena≥5 → Exit Festival; nastup≥8 → 0 (objasni zašto)
7. AND vs OR demo
8. Slična mesta (MLT)
9. Objasni serbian_analyzer i reindeks
10. (Opciono) Pretraga PDF sadržaja

### Varijanta B — cela aplikacija

1. Uvod arhitektura (30s)
2. UES demo (3 min) — varijanta A
3. `/register` → admin odobri → login (K1/A1/K2)
4. `/locations/:id` → utisak (K5)
5. Menadžer `/manager` + `/analytics` (M1/M4)
6. Admin `/admin` — lokacija + PDF upload
7. Pitanja

---

## 14. Pitanja profesora — master tabela

### Opšte / arhitektura

| Pitanje | Odgovor |
|---------|---------|
| Šta je projekat? | Platforma za mesta, događaje, utiske; Spring+Angular+MySQL+ES+MinIO |
| Zašto tri skladišta? | MySQL transakcije, ES pretraga, MinIO binarni fajlovi |
| Koje uloge? | USER, MANAGER, ADMIN — JWT u SecurityConfig |

### UES / Elasticsearch

| Pitanje | Odgovor |
|---------|---------|
| Zašto ES? | Inverted index, scoring, fuzzy, phrase, highlight — ne može MySQL LIKE |
| text vs keyword? | text analiziran; keyword ceo string za prefix |
| Šta radi serbian_analyzer? | ćirilica→latinica, lowercase, asciifolding |
| Zašto ocene u ES? | Denormalizacija za RangeQuery bez JOIN |
| Kada reindeks? | Start, CRUD lokacije, utisak, PDF, POST /reindex |
| Odakle pdfContent? | MinIO → PDFBox → string u ES |
| Zašto 0 na range? | Filter previsok — ispravno |
| Range i AND/OR? | Range uvek MUST (AND) |
| Zašto Match+fuzziness? | Bolje na analiziranim poljima od FuzzyQuery |
| minDocFreq=1 MLT? | Malo dokumenata; default 5 ne bi radilo |
| Razlika K6 i S1? | K6 MySQL filteri; S1 ES napredna pretraga |

### Auth / K1-K2

| Pitanje | Odgovor |
|---------|---------|
| Zašto AccountRequest? | K1 — admin mora odobriti pre User naloga |
| JWT gde se čuva? | localStorage na klijentu; stateless server |
| PENDING login? | Poruka tek posle provere lozinke |

### Ostalo

| Pitanje | Odgovor |
|---------|---------|
| K3 vs M1? | Isti backend; M1 menadžerska perspektiva |
| M3 dubina komentara? | parentId rekurzija u bazi i UI |
| Gde slike? | MinIO bucket newnow-files/images/ |
| Ko vidi GET /api/users? | Samo ADMIN; password @JsonIgnore |

---

## Brzi checklist pre odbrane

- [ ] Docker + backend + frontend rade
- [ ] `/search` — bar Match, Phrase, Prefix, Fuzzy
- [ ] Znaš gde je `buildTextQuery` i `settings.json`
- [ ] Znaš zašto reindeks postoji
- [ ] Demo nalozi rade (admin, petar)
- [ ] Pročitao si sekciju 6 ovog fajla + po potrebi `ODBRANA-UES-VODIC.md`

---

*Poslednje ažuriranje: jun 2026 — kompletan vodič za odbranu; UES detaljno, ostali zahtevi umereno.*
