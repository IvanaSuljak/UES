# NewNow — Vodič za odbranu (UES fokus) + Pokretanje projekta

> **Projekat:** Discover Barcelona / NewNow  
> **Stack:** Spring Boot + Angular + MySQL + Elasticsearch + MinIO  
> **Autor vodiča:** priprema za usmenu odbranu

---

## Sadržaj

0. [**Uči ovako — pročitaj ovo prvo**](#uči-ovako--pročitaj-ovo-prvo)
1. [Pokretanje projekta](#1-pokretanje-projekta)
2. [Nalozi za testiranje](#2-nalozi-za-testiranje)
3. [Arhitektura UES](#3-arhitektura-ues)
4. [Tok procesa](#4-tok-procesa)
5. [Fajlovi u kodu](#5-fajlovi-u-kodu)
6. [Model dokumenta (LocationDocument)](#6-model-dokumenta-locationdocument)
7. [Srpski analizer (detaljno)](#7-srpski-analizer-detaljno)
8. [S1 — Napredna pretraga](#8-s1--napredna-pretraga)
9. [S1a — MatchQuery](#9-s1a--matchquery)
10. [S1b — PhraseQuery](#10-s1b--phrasequery)
11. [S1c — PrefixQuery (naziv)](#11-s1c--prefixquery-naziv)
12. [S1d — FuzzyQuery](#12-s1d--fuzzyquery)
13. [S1e — BooleanQuery (AND/OR)](#13-s1e--booleanquery-andor)
14. [S1f — RangeQuery](#14-s1f--rangequery)
15. [S1g — Prefix na opis i PDF](#15-s1g--prefix-na-opis-i-pdf)
16. [S1h — Highlight](#16-s1h--highlight)
17. [S1i — MLT, score, analizer](#17-s1i--mlt-score-analizer)
18. [Reindeksiranje (ES)](#18-reindeksiranje-es)
19. [PDF i MinIO u UES kontekstu](#19-pdf-i-minio-u-ues-kontekstu)
20. [Demo script za odbranu (2–3 min)](#20-demo-script-za-odbranu-23-min)
21. [Pitanja profesora — gotovi odgovori](#21-pitanja-profesora--gotovi-odgovori)
22. [Test matrica UES](#22-test-matrica-ues)
23. [REST API — svi parametri](#23-rest-api--svi-parametri)
24. [Ostali zahtevi (kratko)](#24-ostali-zahtevi-kratko)

> **Još jednostavnije (ceo projekat):** vidi `ODBRANA-JEDNOSTAVNO.md`  
> **Ispod ove sekcije** ide detaljna verzija — kod, API, curl, Q&A.

---

## Uči ovako — pročitaj ovo prvo

Ovo je **kratka verzija samo za UES**. Nauči ovo, pa onda skroluj dole za detalje ako profesor pita dublje.

### Šta je UES? (jedna rečenica)

> *„Napredna pretraga mesta na stranici `/search` — korisnik unese uslove, Elasticsearch nađe mesta i pokaže rezultate sa obeleženim rečima."*

### Zašto Elasticsearch, a ne samo MySQL?

MySQL `LIKE` prolazi kroz **celu tabelu** i ne ume fuzzy, fraze, ocene i score u jednom upitu.  
Elasticsearch ima **indeks reči** — nađe odmah gde se reč pojavljuje i rangira rezultate.

### Kako radi (3 koraka)

```
1. Uneseš u formu na /search
2. Backend pošalje upit u Elasticsearch
3. Vrati se lista mesta (+ žuto obeležavanje, score)
```

Podaci su u **MySQL**, ali za pretragu se **kopiraju** u ES — to se zove **reindeks** (pri startu, posle utiska, PDF-a, izmene lokacije).

---

### Svi UES zahtevi — tabela za učenje

Otvori **http://localhost:4200/search** i probaj svaki red:

| Unos | Zahtev | Šta radi | Probaj / očekuj |
|------|--------|----------|-----------------|
| `exit festival` | **S1a Match** | Reči bilo gde | Exit Festival, Exit Club |
| `"Exit Festival"` | **S1b Phrase** | Tačna fraza (navodnici!) | Samo Exit Festival |
| `Exit*` | **S1c Prefix** | Naziv počinje sa Exit | Exit Festival + Exit Club |
| `~Exittt` | **S1d Fuzzy** | Greška u kucanju | Nađe Exit |
| AND / OR | **S1e Boolean** | Oba uslova vs bar jedan | Exit + muzički (AND) ✓ |
| Ocena od 5… | **S1f Range** | Filter po ocenama | Exit Festival |
| `festival*` u Opis/PDF | **S1g Prefix** | Prefiks reči u tekstu | Exit Festival |
| (automatski) | **S1h Highlight** | Žuto `<mark>` | Na kartici rezultata |
| Slična mesta | **S1i MLT** | Slična mesta | do 5 rezultata |
| broj na kartici | **Score** | Koliko odgovara upitu | Veći = relevantnije |
| `фестивал` | **Analyzer** | Ćirilica = latinica | Isto kao `festival` |
| PDF upload | **MinIO + pdfContent** | Pretraga teksta iz PDF-a | Polje „Sadržaj PDF" |

**Važno:** `Exit*` u **Naziv** = S1c. `festival*` u **Opis** = S1g. Nije isto!

---

### Tri primera koja moraš znati

**AND:** Naziv `Exit` + Opis `muzički` → Exit Festival ✓  
**AND (0 rezultata — OK!):** Naziv `Exit` + Opis `muzika` → 0 (u opisu piše „muzički")  
**OR:** Naziv `Exit` + Opis `muzika` → više mesta ✓

**Range:** Ocena od 5 → Exit Festival ✓  
**Range (0 — OK!):** Nastup od 8 → 0 (Exit ima ~6.6)

**Ćirilica:** Naziv `фестивал` → ista mesta kao `festival` ✓

---

### Analyzer — reci ovako profesoru

Tekst se pre pretrage **sredi**: ćirilica → latinica, mala slova, bez kvaka (š→s).  
Fajl: `settings.json`, ime: `serbian_analyzer`.

> *„Analyzer sredi tekst da `EXIT`, `exit` i `фестивал` nađu ista mesta."*

---

### Reindeks — reci ovako

> *„Elasticsearch nije automatski vezan za MySQL — posle promene kopiramo podatke u ES, uključujući ocene iz utisaka i tekst iz PDF-a."*

---

### Demo za odbranu (3 min — redom)

1. `/search` → `"Exit Festival"` (Phrase)
2. `Exit*` (Prefix)
3. `~Exittt` (Fuzzy)
4. `exit festival` (Match + highlight + score)
5. Ocena od 5 (Range)
6. **Slična mesta** (MLT)
7. `фестивал` (Analyzer — ista mesta kao `festival`)
8. Jedna rečenica o reindeksu

---

### Gde je kod (4 fajla)

| Fajl | Za šta |
|------|--------|
| `search.component.ts` | Forma u browseru |
| `SearchController.java` | API |
| `LocationSearchService.java` | Match, Phrase, Fuzzy… |
| `settings.json` | Srpski analyzer |

Detalji → sekcije **5–23** ispod.

---

## 1. Pokretanje projekta

### Preduslovi

- **Java 17+** (projekat koristi Java 21)
- **Node.js** + npm (Angular)
- **Docker Desktop** (Elasticsearch + MinIO)
- **MySQL** baza (podešena u `application.properties`)

### Korak 1 — Docker (Elasticsearch + MinIO)

Otvori terminal u folderu `newnow/`:

```powershell
cd "putanja\do\newnow"
docker-compose up -d
```

Provera:

| Servis | URL | Očekivano |
|--------|-----|-----------|
| Elasticsearch | http://localhost:9200 | JSON sa `"tagline" : "You Know, for Search"` |
| MinIO API | http://localhost:9000 | health OK |
| MinIO konzola | http://localhost:9001 | login ekran |

**Zaustavljanje Docker-a:**

```powershell
cd "putanja\do\newnow"
docker-compose down
```

---

### Korak 2 — Backend (Spring Boot)

```powershell
cd "putanja\do\newnow"
.\mvnw.cmd spring-boot:run
```

Provera: http://localhost:8080/api/auth/test → `Auth radi ✅`

Pri startu se automatski:
- kreira admin nalog (ako ne postoji)
- pokreće **reindeks svih lokacija u Elasticsearch** (`ElasticsearchInitializer`)
- normalizuju tipovi događaja (concert/Koncert → Concert)

---

### Korak 3 — Frontend (Angular)

Novi terminal:

```powershell
cd "putanja\do\newnow\frontend"
npm install
npm start
```

Provera: http://localhost:4200

---

### Redosled pokretanja (uvek ovako)

```
1. Docker Desktop (mora biti uključen)
2. docker-compose up -d
3. Sačekaj ~30s da ES ustane
4. mvnw spring-boot:run
5. npm start (frontend)
```

### Zaustavljanje svega

```powershell
# Ctrl+C u terminalima gde rade backend i frontend

# Docker:
cd newnow
docker-compose down
```

---

### Portovi — pregled

| Servis | Port |
|--------|------|
| Frontend | 4200 |
| Backend API | 8080 |
| MySQL | 3306 (iz application.properties) |
| Elasticsearch | 9200 |
| MinIO API | 9000 |
| MinIO konzola | 9001 |

---

## 2. Nalozi za testiranje

| Uloga | Email | Lozinka |
|-------|-------|---------|
| Admin | `admin@newnow.com` | `admin123` |
| Menadžer (Exit Festival) | `petar@manager.com` | `petar123` |
| MinIO konzola | `minioadmin` | `minioadmin` |

**Napomena:** JWT token traje ~10 sati. Ako admin akcije padaju sa „Sesija je istekla" — ponovo se uloguj.

---

## 3. Arhitektura UES

**UES** = napredna pretraga mesta preko **Elasticsearch** indeksa `locations`.

```
MySQL (izvor istine)          Elasticsearch (pretraga)
├── locations                 ├── LocationDocument
├── location_reviews    ──►    │   ├── name, description (+ serbian_analyzer)
└── pdfFileName (MinIO)       │   ├── pdfContent (PDFBox tekst)
                              │   ├── averageRating, totalReviews
                              │   └── avgPerformance, avgSound...
         MinIO (binarno)              ▲
         ├── pdfs/                   │
         └── images/          reindeks pri startu / CRUD / utisak / PDF
```

| Skladište | Uloga |
|-----------|-------|
| **MySQL** | CRUD, utisci, autorizacija, transakcije — **izvor istine** |
| **Elasticsearch** | Full-text pretraga (S1), range filteri, highlight, MLT, scoring |
| **MinIO** | Binarni PDF/slike; PDF tekst se čita pri indeksiranju u ES |

### Zašto ne MySQL LIKE?

| MySQL LIKE | Elasticsearch |
|------------|---------------|
| Full table scan | Inverted index — brz lookup |
| Nema relevance score | TF-IDF scoring |
| Nema fuzzy/phrase/range u jednom upitu | Match, Phrase, Prefix, Fuzzy, Range, Bool, MLT |
| Sporo na većem broju redova | Skalabilno za pretragu |

### Šta je inverted index?

Za svaki **termin** (token posle analizera) ES čuva listu dokumenata u kojima se pojavljuje.

```
"exit"    → [doc1, doc3, doc7]
"festival" → [doc1, doc5]
```

Pretraga = lookup termina u mapi → O(1) umesto skeniranja cele tabele.

---

## 4. Tok procesa

### A) Startup — punjenje ES

1. Spring Boot start
2. `ElasticsearchInitializer` (`ApplicationReadyEvent`) — čeka 2s
3. `LocationIndexService.reindexAll()`
4. Za svaku lokaciju: utisci → ocene, PDF → pdfContent → save u ES

### B) Korisnik pretražuje

1. Angular `/search` → klik **Pretraži**
2. `GET /api/search/locations?...`
3. `SearchController` → `LocationSearchService.search()`
4. Gradi BoolQuery + highlight + sort
5. `ElasticsearchOperations.search()` → JSON rezultat
6. Frontend prikazuje kartice + `<mark>` highlight

### C) Kada se ES ažurira posle starta

| Događaj | Fajl | Metoda |
|---------|------|--------|
| CRUD lokacije | `LocationController` | `indexLocation()` / `removeFromIndex()` |
| Utisak add/hide/delete | `ReviewController` | `indexLocation()` |
| PDF upload | `SearchController` | `indexLocation()` |
| Ručno (admin) | `POST /api/search/reindex` | `reindexAll()` |

---

## 5. Fajlovi u kodu

### Frontend

```
frontend/src/app/components/search/
  search.component.ts      → forma, HTTP poziv, highlight, MLT, operator AND/OR
  search.component.html    → UI polja, help box sa sintaksom, kartice rezultata
  search.component.css     → stilovi, help box, highlight mark
```

### Backend — pretraga i indeks

```
src/main/java/com/example/newnow/
  controller/SearchController.java           → REST /api/search/*
  elasticsearch/LocationSearchService.java   → ES upiti S1a–i, buildTextQuery()
  elasticsearch/LocationIndexService.java    → MySQL + MinIO → ES dokument
  elasticsearch/LocationDocument.java        → ES model (@Document, @MultiField, @Setting)
  elasticsearch/LocationSearchRepository.java → Spring Data ES repository
  elasticsearch/ElasticsearchInitializer.java → startup reindeks + MinIO bucket
  service/MinioService.java                  → upload/download PDF i slika
```

### Konfiguracija analizera

```
src/main/resources/elasticsearch/
  settings.json              → serbian_analyzer (char_filter + tokenizer + filter)
```

### Infrastruktura

```
newnow/docker-compose.yml    → Elasticsearch :9200, MinIO :9000/:9001
src/main/resources/application.properties → MySQL, ES URI, MinIO credentials
```

### Ključne metode za odbranu

| Metoda | Fajl | Uloga |
|--------|------|-------|
| `search()` | LocationSearchService | Glavna S1 pretraga |
| `buildTextQuery()` | LocationSearchService | Detekcija ", ~, * sintakse |
| `buildRangeQuery()` | LocationSearchService | S1f numerički filteri |
| `moreLikeThis()` | LocationSearchService | S1i MLT |
| `buildDocument()` | LocationIndexService | MySQL+PDF → ES doc |
| `reindexAll()` | LocationIndexService | Pun reindeks |
| `initializeOnStartup()` | ElasticsearchInitializer | Start reindeks |

---

## 6. Model dokumenta (LocationDocument)

**Fajl:** `src/main/java/com/example/newnow/elasticsearch/LocationDocument.java`

```java
@Document(indexName = "locations")
@Setting(settingPath = "elasticsearch/settings.json")
public class LocationDocument {
```

Analizer se učitava iz `settings.json` preko `@Setting` — Spring Data Elasticsearch kreira indeks sa custom analizom pri prvom indeksiranju.

### Tabela polja

| Polje | ES tip | Analyzer | Upotreba |
|-------|--------|----------|----------|
| `id` | Keyword (Id) | — | ID lokacije iz MySQL |
| `name` | Text + `.keyword` | `serbian_analyzer` | S1a–d match/fuzzy/phrase; S1c prefix na `.keyword` |
| `description` | Text + `.keyword` | `serbian_analyzer` | S1a–d, S1g prefix po tokenima |
| `pdfContent` | Text + `.keyword` | `serbian_analyzer` | S1g pretraga PDF teksta |
| `address` | Keyword | — | Prikaz u rezultatu (nije u pretrazi) |
| `type` | Keyword | — | Tip lokacije (Festival, Klub…) |
| `imageUrl` | Keyword | — | URL slike iz MinIO |
| `pdfFileName` | Keyword | — | Putanja PDF-a u MinIO (nije pretraživo) |
| `totalReviews` | Integer | — | S1f range — broj utisaka |
| `averageRating` | Double | — | S1f range — prosečna ocena |
| `avgPerformance` | Double | — | S1f — ocena nastupa |
| `avgSoundLight` | Double | — | S1f — zvuk i svetlo |
| `avgSpace` | Double | — | S1f — prostor |
| `avgOverall` | Double | — | S1f — ukupni utisak |

### Primer `@MultiField` u kodu

```java
@MultiField(
    mainField = @Field(type = FieldType.Text, analyzer = "serbian_analyzer"),
    otherFields = {
        @InnerField(suffix = "keyword", type = FieldType.Keyword)
    }
)
private String name;
```

**Zašto MultiField (text + keyword)?**
- **text** (`name`) — analiziran `serbian_analyzer`-om → tokeni → MatchQuery, fuzzy, phrase, MatchBoolPrefix
- **keyword** (`name.keyword`) — ceo string bez analize → PrefixQuery na nazivu (`Exit*`)

**Primer u ES-u:** naziv `"Exit Festival"` se indeksira kao:
- `name`: tokeni `exit`, `festival` (analizirano)
- `name.keyword`: `"Exit Festival"` (cela vrednost)

### Odakle dolaze ocene?

Ocene se **agregiraju iz utisaka u MySQL** u `LocationIndexService.buildDocument()`, ne live pri pretrazi:

```java
List<LocationReview> reviews = reviewRepository.findByLocationId(loc.getId())
    .stream().filter(r -> !r.getIsDeleted()).toList();
doc.setTotalReviews(reviews.size());
// avgPerformance, avgSoundLight, avgSpace, avgOverall, averageRating
```

**Za odbranu:** *„Denormalizovali smo ocene u ES dokument da RangeQuery radi bez JOIN-a sa MySQL pri svakoj pretrazi."*

---

## 7. Srpski analizer (detaljno)

### 7.1 Šta je custom analyzer?

**Custom analyzer** u Elasticsearch-u je pipeline koji transformiše tekst **pri indeksiranju** i **pri pretrazi** (analizator se primenjuje i na upit). Naš se zove `serbian_analyzer` i prilagođen je srpskom tekstu (ćirilica/latinica, dijakritici).

**Napomena:** Ovo **nije** ugrađeni Elasticsearch `serbian` stemmer (koji skraćuje reči tipa „pevač" → „peva"). Naš analyzer radi **normalizaciju pisma i dijakritika**, što je dovoljno za UES zahtev za custom analyzer.

### 7.2 Gde je definisan?

| Šta | Putanja |
|-----|---------|
| JSON konfiguracija | `src/main/resources/elasticsearch/settings.json` |
| Povezivanje sa indeksom | `@Setting(settingPath = "elasticsearch/settings.json")` u `LocationDocument.java` |
| Polja koja ga koriste | `name`, `description`, `pdfContent` |

### 7.3 Pipeline (redosled obrade)

```
Ulazni tekst (npr. "Излаз FESTIVAL")
        │
        ▼
┌─────────────────────────────────────┐
│ 1. char_filter: serbian_cyrillic_to_latin │
│    ćirilica → latinica (karakter po karakter) │
└─────────────────────────────────────┘
        │  "Izlaz FESTIVAL"
        ▼
┌─────────────────────────────────────┐
│ 2. tokenizer: standard              │
│    deli na reči (tokene)              │
└─────────────────────────────────────┘
        │  ["Izlaz", "FESTIVAL"]
        ▼
┌─────────────────────────────────────┐
│ 3. filter: lowercase                │
│    mala slova                       │
└─────────────────────────────────────┘
        │  ["izlaz", "festival"]
        ▼
┌─────────────────────────────────────┐
│ 4. filter: asciifolding             │
│    č→c, š→s, đ→d, ž→z…              │
└─────────────────────────────────────┘
        │  ["izlaz", "festival"]
        ▼
   Tokeni u inverted index-u
```

### 7.4 Kompletan kod — `settings.json`

**Fajl:** `src/main/resources/elasticsearch/settings.json`

```json
{
  "analysis": {
    "char_filter": {
      "serbian_cyrillic_to_latin": {
        "type": "mapping",
        "mappings": [
          "А => A", "Б => B", "В => V", "Г => G", "Д => D",
          "Ђ => Dj", "Е => E", "Ж => Z", "З => Z", "И => I",
          "Ј => J", "К => K", "Л => L", "Љ => Lj", "М => M",
          "Н => N", "Њ => Nj", "О => O", "П => P", "Р => R",
          "С => S", "Т => T", "Ћ => C", "У => U", "Ф => F",
          "Х => H", "Ц => C", "Ч => C", "Џ => Dz", "Ш => S",
          "а => a", "б => b", "в => v", "г => g", "д => d",
          "ђ => dj", "е => e", "ж => z", "з => z", "и => i",
          "ј => j", "к => k", "л => l", "љ => lj", "м => m",
          "н => n", "њ => nj", "о => o", "п => p", "р => r",
          "с => s", "т => t", "ћ => c", "у => u", "ф => f",
          "х => h", "ц => c", "ч => c", "џ => dz", "ш => s"
        ]
      }
    },
    "analyzer": {
      "serbian_analyzer": {
        "type": "custom",
        "char_filter": ["serbian_cyrillic_to_latin"],
        "tokenizer": "standard",
        "filter": ["lowercase", "asciifolding"]
      }
    }
  }
}
```

### 7.5 Korišćenje u Java modelu

```java
@Document(indexName = "locations")
@Setting(settingPath = "elasticsearch/settings.json")
public class LocationDocument {

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "serbian_analyzer"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String name;

    // isto za description i pdfContent
}
```

Analyzer se primenjuje samo na **text** podpolja. **`.keyword`** podpolja nemaju analyzer — čuvaju originalni string.

### 7.6 Šta analyzer omogućava — konkretni primeri

| Upit korisnika | Indeksirani token | Rezultat |
|----------------|-------------------|----------|
| `EXIT` | `exit` | ✅ Match (lowercase) |
| `exit` | `exit` | ✅ Match |
| `Exit` | `exit` | ✅ Match |
| `Излаз` (ćirilica) | `izlaz` | ✅ Match sa `Izlaz` |
| `sume` | `sume` | ✅ Match sa `šume` (asciifolding) |
| `festival` | `festival` | ✅ Match sa `Festival`, `FESTIVAL` |

### 7.7 Zašto char_filter, a ne filter?

| Tip | Kada radi | Primer |
|-----|-----------|--------|
| **char_filter** | Na **karakterima**, pre tokenizacije | `Ђ` → `Dj` pre nego što se tekst podeli na reči |
| **filter** | Na **tokenima** (rečima), posle tokenizacije | `lowercase`, `asciifolding` |

Konverzija ćirilice mora biti **char_filter** jer `mapping` tip radi karakter-po-karakter. Da stavimo konverziju u `filter`, bilo bi prekasno — tokenizer bi već podelio ćirilički tekst na pogrešne tokene.

### 7.8 Testiranje analizera (curl)

Kada je ES pokrenut (`http://localhost:9200`):

```powershell
# Test 1 — case insensitive
Invoke-RestMethod -Uri "http://localhost:9200/locations/_analyze" -Method Post -ContentType "application/json" -Body '{"analyzer":"serbian_analyzer","text":"EXIT FESTIVAL"}'

# Test 2 — ćirilica
Invoke-RestMethod -Uri "http://localhost:9200/locations/_analyze" -Method Post -ContentType "application/json" -Body '{"analyzer":"serbian_analyzer","text":"Излаз"}'

# Test 3 — dijakritici
Invoke-RestMethod -Uri "http://localhost:9200/locations/_analyze" -Method Post -ContentType "application/json" -Body '{"analyzer":"serbian_analyzer","text":"šume"}'
```

**Očekivani tokeni:**
- `EXIT FESTIVAL` → `exit`, `festival`
- `Излаз` → `izlaz`
- `šume` → `sume`

### 7.9 Provera da analyzer postoji u indeksu

```powershell
Invoke-RestMethod -Uri "http://localhost:9200/locations/_settings?filter_path=**.serbian_analyzer"
```

Treba da vrati definiciju `serbian_analyzer` sa `char_filter`, `tokenizer` i `filter`.

### 7.10 Šta ako promeniš settings.json?

**Analyzer se ne može promeniti na postojećem indeksu.** Moraš:

1. Obrisati indeks `locations` (ili `docker-compose down -v` za čist ES)
2. Restartovati backend → `ElasticsearchInitializer.reindexAll()` kreira indeks ponovo
3. Ili pozvati `POST /api/search/reindex` posle brisanja indeksa

### 7.11 Za odbranu — gotove rečenice

- *„Implementirali smo custom `serbian_analyzer` u `settings.json` koji se učitava preko `@Setting` anotacije na LocationDocument."*
- *„Pipeline: char_filter ćirilica→latinica, standard tokenizer, lowercase i asciifolding — normalizacija teksta pri indeksiranju i pretrazi."*
- *„Koristi se na poljima name, description i pdfContent — sva tekstualna polja za full-text pretragu."*
- *„Nije ugrađeni ES serbian stemmer — naš je fokusiran na pismo i dijakritike, što pokriva srpski tekst u praksi."*

---

## 8. S1 — Napredna pretraga

### Osnovno

| Stavka | Vrednost |
|--------|----------|
| **UI** | Navbar → **Pretraži** → `/search` |
| **API** | `GET /api/search/locations` |
| **Controller** | `SearchController.searchLocations()` |
| **Servis** | `LocationSearchService.search()` |
| **Auth** | Javno — `SecurityConfig` → `permitAll` za `GET /api/search/**` |
| **Max rezultata** | 50 (`PageRequest.of(0, 50)`) |
| **Prazna pretraga** | `MatchAllQuery` — vraća sve lokacije |

### Sintaksa u UI (help box)

| Sintaksa | Tip upita | Primer |
|----------|-----------|--------|
| `exit festival` | MatchQuery (S1a) | obična pretraga po rečima |
| `"Exit Festival"` | PhraseQuery (S1b) | tačna fraza u navodnicima |
| `Exit*` | PrefixQuery (S1c) | prefiks na nazivu |
| `festival*` | MatchBoolPrefixQuery (S1g) | prefiks u opisu/PDF |
| `~Exittt` | MatchQuery + fuzziness (S1d) | typo tolerancija |

### Parametri forme (frontend → backend)

| Polje UI | Query param | ES polje |
|----------|-------------|----------|
| Naziv mesta | `name` | `name` |
| Opis mesta | `description` | `description` |
| Sadržaj PDF | `pdf` | `pdfContent` |
| Min/max utisci | `reviewsMin`, `reviewsMax` | `totalReviews` |
| Prosečna ocena | `ratingMin`, `ratingMax` | `averageRating` |
| Nastup | `perfMin`, `perfMax` | `avgPerformance` |
| Zvuk i svetlo | `soundMin`, `soundMax` | `avgSoundLight` |
| Prostor | `spaceMin`, `spaceMax` | `avgSpace` |
| Ukupni utisak | `overallMin`, `overallMax` | `avgOverall` |
| Operator | `operator` | `AND` (default) ili `OR` |
| Sortiranje | `sortBy`, `sortOrder` | `name`, `rating`, `reviews` |

### Tok u kodu

```
search.component.ts → HTTP GET /api/search/locations?...
        ↓
SearchController → LocationSearchService.search()
        ↓
buildTextQuery() / buildRangeQuery() → BoolQuery
        ↓
NativeQuery + Highlight + Sort + withTrackScores(true)
        ↓
ElasticsearchOperations.search() → JSON lista
        ↓
Frontend kartice + [innerHTML] highlight + score
```

---

## 9. S1a — MatchQuery

**Unos:** `exit festival` (bez navodnika, zvezdice, tildе)

**API primer:**
```
GET /api/search/locations?name=exit festival
```

**Kod** (`buildTextQuery` — default grana):
```java
MatchQuery.of(m -> m.field(field).query(value))
```

**Kako radi:** ES traži dokumente gde polje sadrži **bilo koji** od tokena (`exit` OR `festival`), sa relevance scoring-om (TF-IDF). Analyzer normalizuje upit isto kao indeksirani tekst.

**Zašto MatchQuery?** Standardna full-text pretraga po tokenima — osnova S1 pretrage.

**Demo:** Naziv = `exit festival` → Exit Festival, Exit Club (oba sadrže „exit").

**Očekivano:** Više rezultata, highlight `<mark>` oko pronađenih reči, različit `score`.

---

## 10. S1b — PhraseQuery

**Unos:** `"Exit Festival"` (navodnici obavezni)

**API primer:**
```
GET /api/search/locations?name="Exit Festival"
```

**Kod** (`buildTextQuery` — detekcija navodnika):
```java
if (value.startsWith("\"") && value.endsWith("\"")) {
    String phrase = value.substring(1, value.length() - 1);
    return MatchPhraseQuery.of(m -> m.field(field).query(phrase))._toQuery();
}
```

**Zašto:** Match traži reči bilo gde u polju; **Phrase** traži **tačan redosled** reči jednu pored druge.

**Demo:** `"Exit Festival"` → samo Exit Festival, **ne** Exit Club.

**Pitanje profesora:** *Razlika Match vs Phrase?*  
**Odgovor:** Match = OR reči, bilo gde; Phrase = reči u tom redosledu, uzastopno.

---

## 11. S1c — PrefixQuery (naziv)

**Unos:** `Exit*` (zvezdica na kraju)

**API primer:**
```
GET /api/search/locations?name=Exit*
```

**Kod** (`buildTextQuery` — samo za polje `name`):
```java
PrefixQuery.of(p -> p.field(field + ".keyword").value(prefix).caseInsensitive(true))
```

**Zašto `.keyword`?** Prefix radi na **celom stringu** naziva, ne na pojedinačnom tokenu iz analize. `name.keyword` = `"Exit Festival"` kao jedna vrednost.

**Zašto ne na `name` (text)?** Text polje je podeljeno na tokene — prefix na tokenu `exit` ne bi našao `"Exit Festival"` kao celinu na isti način.

**Demo:** `Exit*` → Exit Festival, Exit Club.

**Napomena:** `caseInsensitive(true)` — `exit*` i `Exit*` daju isto.

---

## 12. S1d — FuzzyQuery

**Unos:** `~Exittt` (tilda na početku)

**API primer:**
```
GET /api/search/locations?name=~Exittt
```

**Kod** (`buildTextQuery`):
```java
if (value.startsWith("~")) {
    String term = value.substring(1);
    return MatchQuery.of(m -> m.field(field).query(term).fuzziness("AUTO"))._toQuery();
}
```

**Zašto Match+fuzziness, ne FuzzyQuery?** Klasični `FuzzyQuery` loše radi na **analiziranim text** poljima (tokeni su već normalizovani). `MatchQuery` sa `fuzziness("AUTO")` bolje toleriše typo — edit distance se računa na upitu pre match-a.

**fuzziness AUTO:** ES bira dozvoljenu edit distance na osnovu dužine termina (kraći termini = manja tolerancija).

**Demo:** `~Exittt` → Exit Festival, Exit Club.

**Napomena:** Help box piše „FuzzyQuery" — semantički jeste fuzzy pretraga; tehnička implementacija je `MatchQuery + fuzziness`.

---

## 13. S1e — BooleanQuery (AND/OR)

**UI:** Dugmad **AND** / **OR** između tekstualnih polja (Naziv, Opis, PDF).

**API primer:**
```
GET /api/search/locations?name=Exit&description=muzicki&operator=AND
GET /api/search/locations?name=Exit&description=muzika&operator=OR
```

**Kod** (`LocationSearchService.search()`):

```java
boolean useAnd = !"OR".equalsIgnoreCase(operator);

// Tekstualna polja (name, description, pdfContent):
if (useAnd) {
    boolBuilder.must(textQueries);           // AND — svi uslovi moraju da važe
} else {
    boolBuilder.should(textQueries);         // OR — bar jedan uslov
    boolBuilder.minimumShouldMatch("1");
}

// Range filteri — UVEK:
boolBuilder.must(rangeQueries);              // AND uvek, nezavisno od operatora
```

**Zašto range uvek AND?** Numerički filteri su **ograničenja** — moraju važiti uvek, bez obzira da li je tekstualni operator AND ili OR.

**Demo AND (radi):** Naziv=`Exit`, Opis=`muzički` → Exit Festival.  
**Demo AND (0 rezultata — ispravno):** Naziv=`Exit`, Opis=`muzika` → 0 (opis ima „muzički", analyzer ne matchuje „muzika").  
**Demo OR:** Naziv=`Exit`, Opis=`muzika` → Exit Club + Exit Festival (bar jedno polje matchuje).

**Za odbranu:** *„BoolQuery kombinuje text upite sa MUST (AND) ili SHOULD (OR), dok range filteri uvek idu u MUST."*

---

## 14. S1f — RangeQuery

**UI polja:** Minimalan/maksimalan broj utisaka, prosečna ocena, Nastup, Zvuk i svetlo, Prostor, Ukupni utisak.

**API primer:**
```
GET /api/search/locations?ratingMin=5&reviewsMin=1
GET /api/search/locations?perfMin=6&soundMin=7
GET /api/search/locations?perfMin=8&soundMin=7
```

**ES polja:**

| UI filter | ES polje | Tip |
|-----------|----------|-----|
| Broj utisaka | `totalReviews` | Integer |
| Prosečna ocena | `averageRating` | Double |
| Nastup | `avgPerformance` | Double |
| Zvuk i svetlo | `avgSoundLight` | Double |
| Prostor | `avgSpace` | Double |
| Ukupni utisak | `avgOverall` | Double |

**Kod:**
```java
buildRangeQuery(field, min, max)  // gte (>=) / lte (<=)
```

**Odakle vrednosti?** Agregacija utisaka u `LocationIndexService.buildDocument()` — proseci iz `LocationReview` entiteta u MySQL.

**Demo koji radi:**
- Prosečna ocena od **5**, utisci min **1** → Exit Festival
- Nastup od **6**, Zvuk od **7** → Exit Festival

**Demo 0 rezultata (ispravno ponašanje):**
- Nastup od **8**, Zvuk od **7** → **0** (Exit Festival ima nastup ~6.6 u bazi)

**Za odbranu:** *„0 rezultata ne znači bug — filter je previsok za trenutne podatke u bazi."*

---

## 15. S1g — Prefix na opis i PDF

**Unos:** `festival*` u polje Opis ili Sadržaj PDF (zvezdica na kraju)

**API primer:**
```
GET /api/search/locations?description=festival*
GET /api/search/locations?pdf=petrovaradin*
```

**Kod** (za `description` i `pdfContent`):
```java
if ("description".equals(field) || "pdfContent".equals(field)) {
    return MatchBoolPrefixQuery.of(m -> m.field(field).query(prefix))._toQuery();
}
```

**Kod** (za `name` — drugačije, vidi S1c):
```java
PrefixQuery.of(p -> p.field(field + ".keyword").value(prefix).caseInsensitive(true))
```

**Zašto MatchBoolPrefixQuery za opis/PDF?** Duga tekstualna polja — prefix traži reči koje **počinju** sa datim prefiksom **unutar analiziranog teksta** (po tokenima), ne ceo string.

**Demo:** Opis = `festival*` → Exit Festival.  
**Demo PDF:** Reč iz uploadovanog PDF-a → match na `pdfContent` polje u ES.

**Napomena:** PDF mora biti uploadovan i reindeksiran da `pdfContent` postoji u ES.

---

## 16. S1h — Highlight

**Šta radi:** ES vraća fragment teksta gde je pronađen termin, obeležen HTML tagovima.

**Kod (backend):**
```java
HighlightParameters highlightParams = HighlightParameters.builder()
    .withPreTags("<mark>")
    .withPostTags("</mark>")
    .build();
// polja: name, description, pdfContent
```

**Kod (frontend):**
```html
<h3 [innerHTML]="getHighlight(r, 'name') || r.name"></h3>
<p [innerHTML]="getHighlight(r, 'description') || r.description"></p>
```

**Primer rezultata:** Pretraga `festival` → `Exit <mark>Festival</mark>`

**Zašto `[innerHTML]`?** Angular renderuje HTML tagove iz ES odgovora — običan `{{ }}` bi prikazao `<mark>` kao tekst.

**Demo:** Bilo koji match upit — žuto obeležavanje pronađene reči na kartici rezultata.

**Bezbednost:** Highlight dolazi iz našeg ES backend-a, ne od korisničkog unosa direktno — prihvatljivo za demo.

---

## 17. S1i — MLT, score, analizer

### Score (relevantnost)

Svaki rezultat ima numerički **`score`** — koliko dokument odgovara upitu (TF-IDF model).

**Kod:**
```java
.withTrackScores(true)
r.put("score", hit.getScore());
```

**Zašto `withTrackScores(true)`?** Kada sortiraš po polju (`name`, `rating`), ES po defaultu ne računa score. Eksplicitno tražimo score da bude uvek dostupan u JSON odgovoru.

**UI:** Score se prikazuje na kartici rezultata. Veći score = relevantniji dokument.

**Napomena:** Kod sortiranja po nazivu/oceni redosled ne mora da prati score — score je informativan.

### More Like This (MLT)

Traži dokumente **slične** zadatom mestu na osnovu zajedničkih termina.

| Stavka | Vrednost |
|--------|----------|
| **API** | `GET /api/search/locations/{id}/similar` |
| **UI** | Dugme **„Slična mesta"** / **„🔗 Slična mesta"** na kartici |
| **Max rezultata** | 5 (isključuje sam dokument) |
| **Polja** | `name`, `description`, `pdfContent` |

**Kod:**
```java
MoreLikeThisQuery.of(m -> m
    .fields(List.of("name", "description", "pdfContent"))
    .like(l -> l.document(d -> d.index("locations").id(locationId)))
    .minTermFreq(1)
    .maxQueryTerms(12)
    .minDocFreq(1)
)
```

**Parametri objašnjeni:**

| Parametar | Vrednost | Zašto |
|-----------|----------|-------|
| `minTermFreq` | 1 | Term mora da se pojavi bar 1x u izvornom dokumentu |
| `maxQueryTerms` | 12 | Max broj termina iz izvornog doc-a u MLT upitu |
| `minDocFreq` | 1 | Term mora biti u bar 1 dokumentu (default ES=5 ne bi radilo!) |

**Zašto minDocFreq=1?** Imamo **mali broj dokumenata** (~13 lokacija). Sa defaultnom vrednošću (5), nijedan term ne bi bio u 5+ dokumenata → MLT bi vraćao 0 rezultata.

**Demo:** Klik na „Slična mesta" kod Exit Festival → klubovi/festivali sa sličnim opisom.

### Srpski analizer u S1i kontekstu

MLT koristi ista analizirana polja (`name`, `description`, `pdfContent`) sa `serbian_analyzer`. Sličnost se računa na **normalizovanim tokenima** — ćirilica/latinica i dijakritici ne kvare MLT.

Detalji analizera → **sekcija 7**.

---

## 18. Reindeksiranje (ES)

### Šta je?
Ponovno punjenje ES indeksa `locations` iz MySQL (+ PDF tekst iz MinIO). Svaki poziv `indexLocation()` radi **upsert** — ažurira postojeći ili kreira novi dokument.

### Zašto?
Elasticsearch **nije** automatski sinhronizovan sa MySQL (nema binlog replikacije). Eksplicitno pozivamo `indexLocation()` posle promena.

### Kada se reindeksira?

| Kada | Gde u kodu | Metoda |
|------|------------|--------|
| Start aplikacije | `ElasticsearchInitializer` | `reindexAll()` (posle 2s pauze) |
| CRUD lokacije | `LocationController` | `indexLocation()` / `removeFromIndex()` |
| Promena utiska | `ReviewController` | `indexLocation()` |
| Upload PDF | `SearchController.uploadPdf()` | `indexLocation()` |
| Admin ručno | `POST /api/search/reindex` | `reindexAll()` |

### Šta radi `buildDocument()` — korak po korak

```
1. Kopira osnovna polja lokacije (name, description, address, type, imageUrl)
2. Učita utiske iz MySQL → filtrira obrisane (isDeleted=false)
3. Računa totalReviews, averageRating, avgPerformance, avgSoundLight, avgSpace, avgOverall
4. Ako postoji pdfFileName:
   a. MinioService.downloadFile(pdfFileName)
   b. PDFBox: Loader.loadPDF() → PDFTextStripper.getText()
   c. pdfContent → string u ES dokumentu
5. searchRepository.save(doc) — upsert u indeks "locations"
```

**Kod (PDF deo):**
```java
InputStream pdfStream = minioService.downloadFile(loc.getPdfFileName());
byte[] pdfBytes = pdfStream.readAllBytes();
PDDocument pdDoc = Loader.loadPDF(pdfBytes);
PDFTextStripper stripper = new PDFTextStripper();
String text = stripper.getText(pdDoc);
doc.setPdfContent(text);
```

### Brisanje iz indeksa

Kada se lokacija obriše iz MySQL → `removeFromIndex(locationId)` briše dokument iz ES.

### Ručno reindeksiranje

```powershell
# Kao ulogovan korisnik (JWT u headeru):
Invoke-RestMethod -Uri "http://localhost:8080/api/search/reindex" -Method Post -Headers @{Authorization="Bearer TOKEN"}
```

### Za odbranu (1 rečenica)
*„Reindeksiranje održava Elasticsearch usklađenim sa MySQL — posebno ocene iz utisaka i tekst iz PDF-a. Poziva se automatski pri startu i posle svake relevantne promene."*

---

## 19. PDF i MinIO u UES kontekstu

### MinIO — šta je?

S3-kompatibilan **object storage** u Docker kontejneru. Čuva binarne fajlove (PDF, slike) — ne u MySQL, ne u ES direktno.

| Servis | URL | Nalog |
|--------|-----|-------|
| MinIO API | http://localhost:9000 | — |
| MinIO konzola | http://localhost:9001 | `minioadmin` / `minioadmin` |
| Bucket | `newnow-files` | folderi `pdfs/`, `images/` |

### Tok upload-a i indeksiranja PDF-a

```
Korisnik uploada PDF (admin/manager UI ili API)
        │
        ▼ MinioService.uploadFile()
MinIO bucket "newnow-files/pdfs/uuid.pdf"
        │
        ▼ location.setPdfFileName(objectName)
        ▼ locationRepository.save(location)   ← MySQL
        │
        ▼ LocationIndexService.indexLocation(location)
        ├── MinioService.downloadFile(pdfFileName)
        ├── PDFBox: Loader.loadPDF() → PDFTextStripper.getText()
        └── pdfContent → ES dokument (serbian_analyzer pri indeksiranju)
```

### Endpointi (SearchController)

| Endpoint | Metod | Ko | Opis |
|----------|-------|-----|------|
| `/api/search/locations/{id}/pdf` | POST | Prijavljeni | Upload PDF za lokaciju |
| `/api/search/locations/{id}/pdf` | GET | Svi | Download PDF (404 ako nema) |
| `/api/search/reindex` | POST | Prijavljeni | Ručno reindeksiranje svih lokacija |

### Pretraga PDF sadržaja

- Polje **„Sadržaj PDF"** u formi → query param `pdf` → ES polje `pdfContent`
- `buildTextQuery("pdfContent", ...)` — ista sintaksa kao za naziv/opis
- Pretraga radi čak i ako **opis lokacije** ne sadrži tu reč — tekst je izvučen iz PDF-a

### Download PDF u UI

Dugme **PDF** prikazuje se samo ako lokacija ima `pdfFileName` u bazi. Bez upload-a → 404 (očekivano).

### Za odbranu

*„PDF se čuva u MinIO, metadata u MySQL, a tekstualni sadržaj se pri reindeksiranju izvlači PDFBox-om u ES polje pdfContent za full-text pretragu."*

---

## 20. Demo script za odbranu (2–3 min)

1. *„UES pretraga ide preko Elasticsearch indeksa `locations`. Podaci se reindeksiraju iz MySQL pri startu i posle promena."*

2. **Analyzer** — pokaži da `EXIT` = `exit` daje iste rezultate; objasni pipeline (sekcija 7)

3. **Phrase** `"Exit Festival"` → MatchPhraseQuery — samo ta lokacija

4. **Prefix** `Exit*` → PrefixQuery na `name.keyword`

5. **Fuzzy** `~Exittt` → MatchQuery + fuzziness AUTO

6. **Match** `exit festival` → MatchQuery, više rezultata + score

7. **Range S1f** — ocena od 5, utisci min 1 → Exit Festival; pokaži i 0 rezultata (nastup≥8)

8. **AND/OR** — Exit + muzički (AND) vs Exit + muzika (OR)

9. **Highlight** — `<mark>` na rezultatu

10. **MLT** — Slična mesta kod Exit Festival

11. **PDF** — pretraga reči iz PDF sadržaja (polje Sadržaj PDF)

12. **Reindeks** — objasni posle novog utiska/PDF uploada

---

## 21. Pitanja profesora — gotovi odgovori

| Pitanje | Odgovor |
|---------|---------|
| Zašto ES + MySQL? | MySQL = transakcije i relacije; ES = optimizovana pretraga |
| Šta je inverted index? | Mapa term → dokumenti; O(1) lookup za pretragu |
| Zašto ocene u ES? | Denormalizacija za brz RangeQuery bez JOIN-a pri pretrazi |
| text vs keyword? | text = analiziran tokeni; keyword = ceo string |
| Zašto MatchBoolPrefixQuery za opis? | Duga polja — prefix po tokenima u analiziranom tekstu |
| Prazna pretraga? | MatchAllQuery — svi dokumenti (max 50) |
| Ko reindeksira? | Automatski pri startu + posle CRUD/utisak/PDF + admin POST /api/search/reindex |
| Odakle pdfContent? | MinIO → PDFBox → string u ES pri indexLocation() |
| Zašto 0 na S1f? | Filter previsok za trenutne podatke — ispravno ponašanje |
| Range i AND/OR? | Range uvek MUST (AND), nezavisno od tekstualnog operatora |
| **Šta radi serbian_analyzer?** | char_filter ćirilica→latinica, standard tokenizer, lowercase + asciifolding — `sume`=`šume`, `EXIT`=`exit`, `Излаз`=`Izlaz` |
| **Gde je definisan analyzer?** | `src/main/resources/elasticsearch/settings.json`, učitava se `@Setting` na LocationDocument |
| **Zašto char_filter a ne filter?** | Konverzija ćirilice mora pre tokenizacije — filter radi na rečima, prekasno je |
| **Custom vs ugrađeni serbian?** | Naš je custom za normalizaciju pisma/dijakritika; nije ES stemmer |
| **Zašto minDocFreq=1 za MLT?** | Malo dokumenata (~13); default 5 bi blokirao MLT |
| **Zašto Match+fuzziness umesto FuzzyQuery?** | Bolje radi na analiziranim text poljima |
| **Šta ako promeniš analyzer?** | Mora brisanje indeksa + reindeks — analyzer se ne menja na postojećem indeksu |

---

## 22. Test matrica UES

| Test | UI unos | Očekivano |
|------|---------|-----------|
| **Analyzer case** | `EXIT` vs `exit` vs `Exit` | Isti rezultati |
| **Analyzer ćirilica** | ćirilički naziv (ako postoji) | Pronalazi latinicom |
| **Analyzer dijakritici** | `sume` vs `šume` | Isti rezultati |
| S1a Match | `exit festival` | Više rezultata + score |
| S1b Phrase | `"Exit Festival"` | Samo Exit Festival |
| S1c Prefix | `Exit*` | Exit Festival, Exit Club |
| S1d Fuzzy | `~Exittt` | Exit Festival (+ Club) |
| S1e AND | Exit + muzički | Exit Festival |
| S1e AND (0) | Exit + muzika | 0 (ispravno) |
| S1e OR | Exit + muzika | 2+ rezultata |
| S1f Range | ocena≥5, utisci≥1 | Exit Festival |
| S1f Range (0) | nastup≥8, zvuk≥7 | 0 (ispravno) |
| S1g Prefix opis | `festival*` | Exit Festival |
| S1g PDF | reč iz PDF-a | lokacija sa PDF-om |
| S1h Highlight | bilo koji match | `<mark>` u UI |
| S1i MLT | Slična mesta | do 5 sličnih |
| S1i Score | match sa više rezultata | različiti score |
| Reindeks | start app / novi utisak / PDF | ocene/PDF ažurirani u pretrazi |
| MinIO PDF | upload + pretraga pdfContent | match na reč iz PDF-a |
| ES health | http://localhost:9200 | JSON odgovor |

### Checklist (označi ✓ dok testiraš)

- [ ] **S1a** MatchQuery
- [ ] **S1b** PhraseQuery
- [ ] **S1c** PrefixQuery naziv
- [ ] **S1d** FuzzyQuery
- [ ] **S1e** AND / OR
- [ ] **S1f** Range (ocena, utisci, kategorije)
- [ ] **S1g** Prefix opis + PDF
- [ ] **S1h** Highlight `<mark>`
- [ ] **S1i** MLT + score
- [ ] **Analyzer** case + ćirilica/latinica
- [ ] **MinIO** PDF upload + ES pdfContent
- [ ] **Reindeks** posle promene

---

## 23. REST API — svi parametri

### Pretraga

```
GET /api/search/locations
```

| Parametar | Tip | Default | Opis |
|-----------|-----|---------|------|
| `name` | String | — | Naziv (Match/Phrase/Prefix/Fuzzy sintaksa) |
| `description` | String | — | Opis |
| `pdf` | String | — | PDF sadržaj |
| `reviewsMin` | Integer | — | Min broj utisaka |
| `reviewsMax` | Integer | — | Max broj utisaka |
| `ratingMin` | Double | — | Min prosečna ocena |
| `ratingMax` | Double | — | Max prosečna ocena |
| `perfMin` / `perfMax` | Double | — | Nastup |
| `soundMin` / `soundMax` | Double | — | Zvuk i svetlo |
| `spaceMin` / `spaceMax` | Double | — | Prostor |
| `overallMin` / `overallMax` | Double | — | Ukupni utisak |
| `operator` | String | `AND` | `AND` ili `OR` (samo tekstualna polja) |
| `sortBy` | String | `name` | `name`, `rating`, `reviews` |
| `sortOrder` | String | `asc` | `asc` ili `desc` |

### Primeri URL-ova

```
GET /api/search/locations
GET /api/search/locations?name=Exit*
GET /api/search/locations?name="Exit Festival"
GET /api/search/locations?name=~exittt
GET /api/search/locations?ratingMin=5&reviewsMin=1
GET /api/search/locations?name=Exit&description=barcelona&operator=OR
GET /api/search/locations?description=festival*&pdf=petrovaradin*
GET /api/search/locations/{id}/similar
POST /api/search/reindex
POST /api/search/locations/{id}/pdf
GET  /api/search/locations/{id}/pdf
```

### Odgovor pretrage (JSON primer)

```json
[
  {
    "id": "1",
    "name": "Exit Festival",
    "description": "...",
    "averageRating": 8.5,
    "totalReviews": 3,
    "score": 4.2,
    "highlights": {
      "name": ["Exit <mark>Festival</mark>"]
    }
  }
]
```

---

## 24. Ostali zahtevi (kratko)

| Kod | Suština |
|-----|---------|
| **K1** | Registracija preko AccountRequest (admin odobrava) |
| **K2** | Login JWT; PENDING/REJECTED tek posle provere lozinke |
| **K3** | Admin CRUD lokacija + MinIO slika/PDF |
| **K4** | Menadžer CRUD događaja na svom mestu; admin na svim |
| **K5–K10** | Utisci 1–10, opcioni komentar, profil, filteri |
| **M1–M4** | Manager panel, komentari, analitika (Chart.js) |
| **A1–A2** | Admin zahtevi, dodela/uklanjanje menadžera |
| **NF1** | @JsonIgnore password; GET /api/users samo admin |
| **MinIO** | Slike (K3/K4) + PDF (UES pdfContent) |

---

*Poslednje ažuriranje: jun 2026 — prošireno: srpski analizer (kompletan kod), REST API, test matrica, Q&A.*
