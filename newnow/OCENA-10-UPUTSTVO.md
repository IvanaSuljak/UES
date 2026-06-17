# OCENA 10 — UES Implementacija

## Šta je implementirano

### Infrastruktura (Docker)
- **`docker-compose.yml`** — pokreće Elasticsearch 8.13 i MinIO u Docker kontejnerima
- Elasticsearch: `http://localhost:9200`
- MinIO (S3-kompatibilni storage): `http://localhost:9000` (API), `http://localhost:9001` (konzola)
- Pokretanje: `docker-compose up -d`

---

## [S1] Pretraga mesta u Elasticsearch-u

### Arhitektura

```
MySQL (originalni podaci) → LocationIndexService → Elasticsearch (indeks "locations")
                                                ↑
                          ElasticsearchInitializer (pri startu Spring Boot-a)
```

### Fajlovi — Backend

| Fajl | Uloga |
|------|-------|
| `elasticsearch/LocationDocument.java` | ES model (indeksna struktura) |
| `elasticsearch/LocationSearchRepository.java` | Spring Data ES repozitorij |
| `elasticsearch/LocationSearchService.java` | Svi query tipovi, Highlighter, MLT |
| `elasticsearch/LocationIndexService.java` | Indeksiranje + PDF parsiranje |
| `elasticsearch/ElasticsearchInitializer.java` | Auto-reindeks pri startu |
| `controller/SearchController.java` | REST endpoints |
| `service/MinioService.java` | Upload/download fajlova u MinIO |
| `resources/elasticsearch/settings.json` | Custom Serbian analyzer |

---

## Custom Serbian Analyzer

**Lokacija:** `src/main/resources/elasticsearch/settings.json`

```json
"serbian_analyzer": {
  "char_filter": ["serbian_cyrillic_to_latin"],  // Ćirilica → Latinica
  "tokenizer": "standard",
  "filter": ["lowercase", "asciifolding"]        // Mala slova + dijakritici
}
```

**Zašto:** Pretraga je nezavisna od:
- Velikih/malih slova (`EXIT` == `exit`)
- Ćirilice/latinice (`Излаз` == `Izlaz`)
- Dijakritika (`đ` == `dj`)

---

## Podržani query tipovi

| Sintaksa | Tip | Primjer |
|----------|-----|---------|
| `exit festival` | MatchQuery | Obična pretraga |
| `"exit festival"` | PhraseQuery | Tačna fraza |
| `Exit*` | PrefixQuery | Pretraga po prefiksu |
| `~exittt` | FuzzyQuery | Sa greškom (distanca 2) |

### BooleanQuery
- **AND** — sva polja moraju odgovarati (default)
- **OR** — dovoljno jedno polje

---

## Pretraživa polja

1. **Naziv mesta** (`name`)
2. **Opis mesta** (`description`)
3. **PDF sadržaj** (`pdfContent`) — tekst se parsira iz PDF-a (PDFBox)
4. **Broj utisaka** — opseg od-do (`reviewsMin`, `reviewsMax`)
5. **Prosječna ocjena** — opseg od-do (`ratingMin`, `ratingMax`)
6. **Ocjena nastupa** — opseg (`perfMin`, `perfMax`)
7. **Ocjena zvuka i svetla** — opseg (`soundMin`, `soundMax`)
8. **Ocjena prostora** — opseg (`spaceMin`, `spaceMax`)
9. **Ukupan utisak** — opseg (`overallMin`, `overallMax`)

---

## Highlighter (dinamički sažetak)

Elasticsearch vraća fragment teksta gdje je pronađen term, označen tagovima `<mark>...</mark>`. Prikazuje se na kartici rezultata:
- Žuti highlight u opisu
- Zeleni highlight za PDF sadržaj

---

## More Like This (MLT)

- Endpoint: `GET /api/search/locations/{id}/similar`
- Traži slična mesta prema poljima: `name`, `description`, `pdfContent`
- `minTermFreq=1`, `maxQueryTerms=12`, `minDocFreq=1`
- Prikazano klikom na "🔗 Slična mjesta"

---

## MinIO — Storage za slike i PDF-ove

**Bucket:** `newnow-files`

| Endpoint | Opis |
|----------|------|
| `POST /api/search/locations/{id}/pdf` | Upload PDF fajla |
| `GET /api/search/locations/{id}/pdf` | Download PDF fajla |

PDF se automatski parsira i sadržaj se indeksira u ES `pdfContent` polje.

---

## Sinhronizacija ES indeksa

- **Automatski** pri startu aplikacije (`ElasticsearchInitializer`) — svi MySQL entiteti se indeksiraju
- **Ručno** (admin): `POST /api/search/reindex`
- Svaki put kada se kreira/mijenja lokacija, treba ručno reindeksirati (ili pozvati reindex)

---

## REST API — Pretraga

```
GET /api/search/locations?name=Exit&sortBy=rating&sortOrder=desc&operator=AND
GET /api/search/locations?reviewsMin=1&ratingMin=7
GET /api/search/locations?name="exit festival"
GET /api/search/locations?name=~exittt
GET /api/search/locations?name=Exit*
GET /api/search/locations/{id}/similar
GET /api/search/locations/{id}/pdf
POST /api/search/locations/{id}/pdf   (multipart/form-data, field: "file")
POST /api/search/reindex
```

---

## Frontend — Angular Komponenta `/search`

**Lokacija:** `frontend/src/app/components/search/`

**Funkcionalnosti:**
- Polja za pretragu: naziv, opis, PDF
- AND/OR operator dugmad
- Opseg broja utisaka (min/max)
- Opseg prosječne ocjene (min/max)
- Napredni filteri po kategorijama (nastup, zvuk, prostor, ukupno)
- Sortiranje po: nazivu, ocjeni, broju utisaka
- Prikaz rezultata sa karticama
- Highlight (žuto označeni termini u opisu)
- "🔗 Slična mjesta" dugme (MLT)
- "📥 Preuzmi PDF" dugme (ako postoji PDF)
- Link "Posjeti mjesto" vodi na `/locations/{id}`

**Navbar link:** "🔍 Pretraži" — vidljiv svim korisnicima

---

## Ostale Grade 10 funkcionalnosti (već implementirano)

| Funkcionalnost | Status |
|----------------|--------|
| K9 — Email nakon promjene lozinke | ✅ `UserServiceImpl.changePassword()` poziva `emailService.sendPasswordChangedEmail()` |
| A1 — Email nakon obrade zahtjeva | ✅ `AccountRequestController` poziva `sendAccountApprovedEmail/RejectedEmail()` |
| K6 — Filter po proizvoljnom datumu | ✅ `GET /api/events/filter/date?date=2025-12-31` |
| M3 — Neograničeni nivo odgovora | ✅ `buildCommentTree()` rekurzivna metoda u `CommentController` |

---

## Pokretanje sistema (kompletan stack)

1. Pokreni Docker Desktop
2. U terminalu: `docker-compose up -d` (u `/newnow` folderu)
3. Pokreni backend: `.\mvnw.cmd spring-boot:run`
4. Pokreni frontend: `ng serve` (u `/frontend` folderu)
5. Otvori `http://localhost:4200/search`

---

## Moguća pitanja na odbrani

**Q: Šta je Elasticsearch i zašto ga koristimo?**
A: Distribuiran search engine zasnovan na Lucene. Koristimo ga jer MySQL LIKE pretraga nije dovoljna — ES podržava full-text pretragu, fuzzy matching, range upite, highlighter i MLT.

**Q: Šta je custom analyzer i čemu služi?**
A: Konfiguracija kako ES tokenizuje i transformiše tekst. Naš `serbian_analyzer` pretvara ćirilicu u latinicu (char_filter), tokenizuje standardno, i normalizuje na mala slova + ASCII.

**Q: Šta je PhraseQuery vs MatchQuery?**
A: MatchQuery pronalazi dokumente koji sadrže bilo koji od traženih termina. PhraseQuery zahtjeva da termini budu u tačnom redoslijedu u istom polju.

**Q: Šta je FuzzyQuery i distanca?**
A: Pronalazi termina sličnih zadatom, sa određenim brojem edita (Levenshtein distanca). Koristimo distancu 2 — dozvoljava 2 razlike.

**Q: Šta je More Like This?**
A: ES query koji pronalazi dokumente slične zadatom dokumentu, na osnovu zajedničkih termina u specificiranim poljima.

**Q: Šta je MinIO?**
A: S3-kompatibilan object storage (kao AWS S3 ali self-hosted). Koristimo ga za čuvanje slika i PDF fajlova umjesto lokalnog filesystema.

**Q: Kako se PDF parsira i indeksira?**
A: Koristimo Apache PDFBox biblioteku. Kada se PDF uploada u MinIO, čita se kao stream, parsira se tekst (`PDFTextStripper`), i taj tekst se čuva u ES `pdfContent` polju.
