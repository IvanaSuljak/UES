# OCENA 10 — UES Implementacija (Elasticsearch + MinIO)

## Pokretanje kompletnog sistema

```
1. Pokreni Docker Desktop
2. cd newnow
3. docker-compose up -d          ← pokreće ES 8.13 i MinIO
4. .\mvnw.cmd spring-boot:run    ← Spring Boot (automatski reindeksira)
5. cd frontend && ng serve       ← Angular na :4200
```

### Provjera infrastrukture

| URL | Šta prikazuje |
|-----|---------------|
| `http://localhost:9200` | Elasticsearch JSON — `"number": "8.13.0"` |
| `http://localhost:9001` | MinIO konzola — login: `minioadmin / minioadmin` |
| `http://localhost:4200/search` | Search stranica |

---

## Arhitektura

```
MySQL (Location entiteti)
        │
        ▼  pri startu aplikacije (ElasticsearchInitializer)
LocationIndexService
        │  - računa averageRating, avgPerformance, avgSoundLight, avgSpace, avgOverall
        │  - parsira PDF fajl iz MinIO (Apache PDFBox) → pdfContent polje
        │
        ▼
Elasticsearch indeks "locations"
        │
        ▼  GET /api/search/locations?name=...
LocationSearchService → NativeQuery → ElasticsearchOperations
        │
        ▼
Angular SearchComponent (/search)
```

---

## [S1] Elasticsearch — Podržani query tipovi

### Sintaksa u polju pretrage

| Unos | Tip upita | Primer | Šta vraća |
|------|-----------|--------|-----------|
| `exit festival` | **MatchQuery** | Obična pretraga | Dokumenti koji sadrže te reči |
| `"exit festival"` | **PhraseQuery** | Tačna fraza (sa navodnicima) | Samo tačna ta fraza u tom redosledu |
| `Exit*` | **PrefixQuery** | Pretraga po prefiksu (sa `*`) | Sve što počinje sa "Exit" |
| `~exittt` | **FuzzyQuery** | Sa `~` na početku | Toleriše 2 greške u kucanju |

### BooleanQuery (AND/OR)
- **AND** (default) — sva polja moraju da se poklapaju
- **OR** — dovoljno je da se poklopi jedno polje

### Range upiti (filteri opsegom)
Rade nezavisno od tekstualnog polja. Kombinuju se uvek sa AND:
- `ratingMin` / `ratingMax` — prosečna ocena (0–10)
- `reviewsMin` / `reviewsMax` — broj utisaka
- `perfMin/Max`, `soundMin/Max`, `spaceMin/Max`, `overallMin/Max` — po kategorijama

---

## Pretraživa polja

| Polje | Parametar | Opis |
|-------|-----------|------|
| Naziv mesta | `name` | Ime lokacije |
| Opis mesta | `description` | Tekstualni opis |
| Sadržaj PDF-a | `pdf` | Tekst izvučen iz PDF brošure lokacije |
| Prosečna ocena | `ratingMin/Max` | Ukupna prosečna ocena |
| Nastup | `perfMin/Max` | Ocena nastupa |
| Zvuk i svetlo | `soundMin/Max` | Ocena tehničke opreme |
| Prostor | `spaceMin/Max` | Ocena prostora |
| Ukupan utisak | `overallMin/Max` | Ukupan utisak korisnika |
| Broj utisaka | `reviewsMin/Max` | Broj ocenjivanja |

---

## Custom Serbian Analyzer

**Lokacija:** `src/main/resources/elasticsearch/settings.json`

```json
"serbian_analyzer": {
  "char_filter": ["serbian_cyrillic_to_latin"],
  "tokenizer": "standard",
  "filter": ["lowercase", "asciifolding"]
}
```

**Šta omogućava:**
- `EXIT` == `exit` == `Exit` — case insensitive
- `Излаз` == `Izlaz` — ćirilica → latinica (char_filter sa mapiranjem svakog ćiriličnog slova)
- `sume` == `šume` — ASCII folding (dijakritici se ignorišu)

**Zašto char_filter a ne filter?**  
Jer `mapping` tip transformacije radi na nivou karaktera **pre** tokenizacije. `filter` radi na tokenima (rečima) — prekasno za konverziju ćirilice.

---

## Highlighter

ES automatski vraća fragment teksta gde je pronađen term, označen sa `<mark>...</mark>` tagovima.

```java
HighlightParameters.builder()
    .withPreTags("<mark>")
    .withPostTags("</mark>")
    .build();
```

Frontend prikazuje highlight umesto originalnog teksta:
```html
<h3 [innerHTML]="getHighlight(r, 'name') || r.name"></h3>
```

**Primer:** Pretraga `festival` → vrača `Exit <mark>Festival</mark>`

---

## Relevantnost (score)

Svaki rezultat ima `score` — broj koji pokazuje koliko dokument odgovara upitu.
- Veći score = relevantniji rezultat
- Kada se sortira po polju (npr. po nazivu), ES po defaultu ne računa score → dodali smo `withTrackScores(true)` da uvek bude dostupan
- Sortiranje po relevantnosti: `sortBy=_score&sortOrder=desc`

---

## More Like This (MLT)

- **Endpoint:** `GET /api/search/locations/{id}/similar`
- Traži dokumente slične zadatom, prema poljima: `name`, `description`, `pdfContent`
- Parametri: `minTermFreq=1`, `maxQueryTerms=12`, `minDocFreq=1`
- Dugme **"🔗 Slična mesta"** u rezultatima pretrage

**Zašto minDocFreq=1?** — Jer imamo mali broj dokumenata (13 lokacija). Sa defaultnom vrednošću (5), MLT ne bi radio jer nijedan term ne bi bio u 5+ dokumenata.

---

## MinIO — Object Storage

**Bucket:** `newnow-files`

MinIO je S3-kompatibilan object storage (kao AWS S3 ali self-hosted u Docker kontejneru).

### Endpoints

| Endpoint | Metod | Ko može | Opis |
|----------|-------|---------|------|
| `/api/search/locations/{id}/pdf` | POST | Prijavljeni korisnik | Upload PDF fajla za lokaciju |
| `/api/search/locations/{id}/pdf` | GET | Svi | Download PDF fajla |
| `/api/search/reindex` | POST | Svi | Ručno reindeksiranje svih lokacija |

### Kako radi upload i indeksiranje PDF-a

```
Korisnik uploada PDF
      │
      ▼ MinioService.uploadFile()
MinIO bucket "newnow-files"
      │
      ▼ location.setPdfFileName(objectName)
      ▼ locationRepository.save(location)
      │
      ▼ LocationIndexService.indexLocation(location)
          → MinioService.downloadFile(pdfFileName)
          → PDFBox: Loader.loadPDF(bytes) → PDFTextStripper.getText()
          → pdfContent polje u ES indeksu
```

**Napomena:** Sadržaj PDF-a se čuva u ES pod `pdfContent` poljem — pretraživanje po PDF sadržaju radi čak i ako opis lokacije ne sadrži tu reč.

---

## Sinhronizacija MySQL → Elasticsearch

| Kada | Kako |
|------|------|
| Svaki start aplikacije | `ElasticsearchInitializer` → `reindexAll()` — svi MySQL entiteti se automatski indeksiraju |
| Ručno | `POST /api/search/reindex` |
| Nakon PDF uploada | `LocationIndexService.indexLocation(location)` se poziva automatski |

---

## REST API — svi endpoints

```
GET  /api/search/locations                          ← osnovna pretraga (bez parametara = sve)
GET  /api/search/locations?name=Exit*               ← prefix pretraga
GET  /api/search/locations?name="Exit Festival"     ← phrase pretraga
GET  /api/search/locations?name=~exittt             ← fuzzy pretraga
GET  /api/search/locations?ratingMin=5&ratingMax=10 ← range po oceni
GET  /api/search/locations?name=festival&operator=OR&description=barcelona ← OR logika
GET  /api/search/locations?sortBy=rating&sortOrder=desc ← sortiranje
GET  /api/search/locations/{id}/similar             ← More Like This
GET  /api/search/locations/{id}/pdf                 ← download PDF
POST /api/search/locations/{id}/pdf                 ← upload PDF (multipart, field: "file")
POST /api/search/reindex                            ← ručno reindeksiranje
```

---

## Frontend — Angular komponenta `/search`

**Lokacija:** `frontend/src/app/components/search/`

### Funkcionalnosti

- Polja za pretragu: naziv, opis, PDF sadržaj
- AND/OR operator dugmad
- Opseg broja utisaka (min/max)
- Opseg prosečne ocene (min/max)
- Napredni filteri po kategorijama (nastup, zvuk, prostor, ukupno) — klik na "Prikaži filtre"
- Sortiranje po: nazivu, oceni, broju utisaka
- Prikaz rezultata sa karticama + slika
- Highlight (žuto označeni termini u polju naziva i opisa)
- Zeleni highlight za PDF sadržaj
- **"🔗 Slična mesta"** dugme (More Like This)
- **"📥 Preuzmi PDF"** dugme (vidljivo samo ako lokacija ima PDF)
- **"Poseti mesto"** link → `/locations/{id}`

**Navbar link:** `🔍 Pretraži` — vidljiv svim korisnicima, ne zahteva prijavu

---

## Testiranje u browseru (potvrđeni scenariji)

### Test 1 — Match pretraga
- Naziv: `festival` → Pretraži
- **Očekuješ:** Exit Festival + Primavera Sound Festival, oba sa `<mark>Festival</mark>` u naslovu

### Test 2 — Prefix pretraga
- Naziv: `Exit*` → Pretraži
- **Očekuješ:** Exit Festival + Exit Club (svi koji počinju sa "Exit")

### Test 3 — Fuzzy pretraga
- Naziv: `~festivall` → Pretraži
- **Očekuješ:** Exit Festival + Primavera Sound Festival (tolerancija 2 greške)

### Test 4 — Phrase pretraga
- Naziv: `"Exit Festival"` (sa navodnicima) → Pretraži
- **Očekuješ:** Samo Exit Festival (tačna fraza)

### Test 5 — Range po oceni
- Klikni **Resetuj**, zatim: Prosečna ocena od: `5`, do: `10` → Pretraži
- **Očekuješ:** Samo Exit Festival (jedina lokacija sa ocenom ~7.58; ostale imaju 0.0 jer nemaju utiske)

### Test 6 — OR logika
- Naziv: `festival`, Opis: `barcelona`, Operator: **OR** → Pretraži
- **Očekuješ:** 3 rezultata (Exit Festival, Primavera Sound, Camp Nou — jer svaki ima ILI festival ILI barcelona)

### Test 7 — AND logika
- Naziv: `festival`, Opis: `barcelona`, Operator: **AND** → Pretraži
- **Očekuješ:** 0 rezultata (nijedna lokacija nema i "festival" u nazivu I "barcelona" u opisu istovremeno)

### Test 8 — Slična mesta (MLT)
- Pretraži `festival`, na kartici klikni **"🔗 Slična mesta"**
- **Očekuješ:** pojavljuje se lista sličnih lokacija ispod

---

## Ostale Grade 10 funkcionalnosti

| Funkcionalnost | Implementacija |
|----------------|----------------|
| K9 — Email pri promeni lozinke | `UserServiceImpl.changePassword()` → `emailService.sendPasswordChangedEmail()` |
| A1 — Email pri obradi zahteva | `AccountRequestController` → `sendAccountApprovedEmail()` / `sendAccountRejectedEmail()` |
| K6 — Filter po datumu | `GET /api/events/filter/date?date=2025-12-31` |
| M3 — Neograničeni nivoi komentara | `buildCommentTree()` — rekurzivna metoda u `CommentController` |

**Napomena:** Email je MOCK — ne šalje se stvarno, samo se loguje u konzoli. Ovo je konfigurisano u `application.properties` sa `spring.mail.properties.mail.smtp.auth=false`.

---

## Moguća pitanja na odbrani

**Q: Šta je Elasticsearch i zašto ga koristimo umesto MySQL LIKE?**
A: ES je distribuiran search engine na bazi Lucene. MySQL `LIKE '%exit%'` je spor (full table scan, nema indeksa), ne podržava fuzzy, phrase, range i highlight. ES ima inverted index koji je optimizovan za full-text pretragu.

**Q: Objasni inverted index.**
A: Klasičan indeks: dokument → reči. Inverted index: reč → lista dokumenata. Pretraga reči je O(1) umesto O(n).

**Q: Šta je custom analyzer i čemu služi?**
A: Konfiguracija pipeline-a za obradu teksta pri indeksiranju i pretrazi. Naš `serbian_analyzer`: char_filter (ćirilica→latinica), tokenizer (standard — deli na reči), filter (lowercase + asciifolding).

**Q: Razlika između PhraseQuery i MatchQuery?**
A: MatchQuery — reči moraju biti prisutne, redosled nije bitan. PhraseQuery — reči moraju biti u istom redosledu, jedna do druge.

**Q: Šta je Levenshtein distanca u FuzzyQuery?**
A: Broj operacija (dodaj, obriši, zameni karakter) potrebnih da se jedna reč pretvori u drugu. `festivall` → `festival` = 1 operacija (brisanje jednog 'l'). Mi koristimo distancu 2 — dozvoljava 2 takve operacije.

**Q: Šta je More Like This query?**
A: ES upit koji pronalazi dokumente slične zadatom dokumentu na osnovu zajedničkih termina u specificiranim poljima. Interno gradi BoolQuery sa should klauzama za najvažnije termine.

**Q: Šta je MinIO?**
A: S3-kompatibilan object storage koji se self-hostuje u Docker kontejneru. Koristimo ga umesto lokalnog filesystema jer je skalabilan, ima REST API, i može se zameniti pravim AWS S3 bez promene koda.

**Q: Kako se PDF parsira i indeksira?**
A: `MinioService.downloadFile()` → bajt niz → `Loader.loadPDF(bytes)` (Apache PDFBox 3.x) → `PDFTextStripper.getText()` → tekst se čuva u `pdfContent` polju ES dokumenta.

**Q: Zašto koristimo withTrackScores(true)?**
A: Kada ES sortira po nekom polju (npr. po nazivu), po defaultu ne računa relevantnost (score) jer je nepotrebna za sortiranje. Sa `withTrackScores(true)` prisiljavamo ES da uvek računa score, pa ga možemo prikazati korisniku.

**Q: Šta se dešava pri startu aplikacije?**
A: `ElasticsearchInitializer` (Spring `@EventListener(ApplicationReadyEvent.class)`) poziva `minioService.ensureBucketExists()` da kreira bucket ako ne postoji, zatim `locationIndexService.reindexAll()` koji prolazi kroz sve MySQL lokacije i indeksira ih u ES.
