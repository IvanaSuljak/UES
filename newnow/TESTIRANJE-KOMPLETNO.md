# Kompletno testiranje — NewNow (UES 2026)

> **Jedan dokument za sve UI testove.** Prođi redom od sekcije 0 do kraja.  
> Poseban fokus: **UES deo** (sekcija 6 — Elasticsearch + MinIO + S1).

---

## 0. Priprema pre testiranja

### 0.1 Pokreni sistem (redosledom)

```powershell
# 1. Docker Desktop — mora biti uključen
cd newnow
docker-compose up -d

# 2. Backend (čekaj "Started NewNowProjectApplication")
.\mvnw.cmd spring-boot:run

# 3. Frontend (novi terminal)
cd frontend
npm start
```

### 0.2 Proveri infrastrukturu

| Šta | URL | Očekuješ |
|-----|-----|----------|
| Backend | http://localhost:8080/api/auth/test | `Auth radi ✅` |
| Frontend | http://localhost:4200 | Početna se učitava |
| Elasticsearch | http://localhost:9200 | JSON sa `"number": "8.13.0"` |
| MinIO konzola | http://localhost:9001 | Login: `minioadmin` / `minioadmin` |
| MinIO bucket | MinIO → Buckets → `newnow-files` | Bucket postoji |

### 0.3 Demo nalozi (provereni)

| Uloga | Email | Lozinka | Napomena |
|-------|-------|---------|----------|
| Admin | `admin@newnow.com` | `admin123` | Admin panel |
| Menadžer | `petar@manager.com` | `petar123` | Exit Festival |
| Menadžer | `luka@test.com` | `luka123` | Exit Club |
| Korisnik | registruj novi ili koristi odobreni nalog | — | vidi test K1 |

### 0.4 Checkbox pre odbrane

- [ ] Docker + ES + MinIO rade
- [ ] Backend i frontend rade
- [ ] Exit Festival ima uploadovan PDF (za S1g PDF test)
- [ ] Admin login radi

---

## 1. K1 — Zahtev za registraciju

**Uloga:** gost (odjavljen)

| # | Gde klikneš / šta uneseš | Očekivani rezultat |
|---|--------------------------|-------------------|
| 1 | Navbar → **Registruj se** → `/register` | Forma: Ime, Email, Lozinka |
| 2 | Unesi: `Test Korisnik`, `novi.test@demo.com`, `test123456` | Polja popunjena |
| 3 | Klik **Registruj se** | Poruka da je zahtev poslat; admin ga vidi |
| 4 | Idi na **Prijavi se**, isti email/lozinka | Greška: zahtev **na čekanju** — nema ulaza |
| 5 | Admin odobri (vidi A1-test 1) | — |
| 6 | Ponovo **Prijavi se** | Uspešan login |

**Dodatno (edge):**
| # | Akcija | Očekuješ |
|---|--------|----------|
| 7 | Admin **❌ Odbij** drugi zahtev | Login: zahtev odbijen |
| 8 | Ponovo **Registruj se** istim emailom posle odbijanja | Novi PENDING zahtev (ne dupli red) |
| 9 | Dupli zahtev istim emailom dok je PENDING | Greška — već poslat |

---

## 2. A1 — Admin obrađuje zahteve

**Uloga:** `admin@newnow.com` / `admin123`

| # | Gde klikneš | Očekivani rezultat |
|---|-------------|-------------------|
| 1 | Dropdown **👤 admin@newnow.com** → **⚙️ Admin panel** | Tab **📋 Zahtevi (N)** |
| 2 | Kod pending zahteva → **✅ Odobri** | Alert *Zahtev odobren ✅*; nestaje sa liste |
| 3 | Registruj novog → **❌ Odbij** | Alert *Zahtev odbijen ❌* |
| 4 | Backend konzola | Log4j poruka o emailu (mock) |

---

## 3. K2 — Prijava i odjava

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | **Prijavi se** → validni kredencijali → **Prijavi se** | Redirect na početnu; navbar **👤 Ime** |
| 2 | Dropdown → **🚪 Odjavi se** | Navbar: **Prijavi se** / **Registruj se** |
| 3 | Login pogrešna lozinka | *Pogrešan email ili lozinka* (bez curenja statusa) |
| 4 | Običan user — nema **⚙️ Admin panel** u meniju | Samo Profil + Odjava |

---

## 4. K8 — Početna stranica

**Uloga:** bilo ko (gost OK)

| # | Gde | Očekuješ |
|---|-----|----------|
| 1 | **🏠 Početna** | Hero + sekcije ispod |
| 2 | **🎉 Događaji danas** | Lista do 6 događaja ILI *Nema događaja za danas* |
| 3 | **⭐ Najbolje ocenjena mesta** | 4 kartice sa ocenom (npr. Exit Festival) |
| 4 | **💬 Najskoriji utisci** | 3 utiska sa najpopularnijeg mesta |
| 5 | **Vidi sve događaje →** | Otvara `/events` |
| 6 | **Vidi detalje** na kartici mesta | Otvara `/locations/:id` |

---

## 5. K3 — Mesta (lista + detalji + admin)

### 5.1 Lista mesta (K6 deo — pretraga mesta)

**Uloga:** gost

| # | `/locations` | Očekuješ |
|---|--------------|----------|
| 1 | Ukucaj `Exit` u **🔍 Pretraži po nazivu ili adresi...** | Filtrirana lista |
| 2 | Dropdown **🎛️ Svi tipovi mesta** → npr. Festival | Samo festivali |
| 3 | **🔄 Resetuj** | Sva mesta, sortirana po oceni |
| 4 | **📄 PDF** na kartici | Preuzima PDF (404 ako nema PDF-a) |
| 5 | **🔍 Vidi detalje** | Detalji mesta |

### 5.2 Detalji mesta

| # | `/locations/1` (Exit Festival) | Očekuješ |
|---|-------------------------------|----------|
| 1 | Stranica | Slika, adresa, opis, prosečna ocena, broj utisaka |
| 2 | **📄 Preuzmi PDF** | Download PDF iz MinIO |
| 3 | **Predstojeći događaji** | Kartice događaja (ako postoje) |

### 5.3 Admin CRUD + MinIO (K3)

**Uloga:** admin

| # | Admin panel → **🏢 Lokacije** | Očekuješ |
|---|-------------------------------|----------|
| 1 | **➕ Dodaj lokaciju** | Modal forma |
| 2 | Popuni naziv, adresu, tip, opis | — |
| 3 | **📷 Upload slike** (MinIO) ILI URL slike | Slika u bucket-u `images/` |
| 4 | **📄 Upload PDF** (MinIO) | PDF u bucket-u `pdfs/`; ES indeksira sadržaj |
| 5 | **💾 Sačuvaj** | Nova kartica na listi |
| 6 | **✏️** uredi postojeću | Promene sačuvane |
| 7 | **📄** na kartici | Download PDF |
| 8 | **🗑️** obriši test lokaciju | Nestaje sa liste i iz ES |

### 5.4 Menadžer — ograničena izmena (K3)

**Uloga:** `petar@manager.com` / `petar123`

| # | Manager Dashboard → **✏️ Izmeni lokaciju** | Očekuješ |
|---|-------------------------------------------|----------|
| 1 | Menjaš adresu, tip, opis → **✅ Sačuvaj** | *Lokacija ažurirana ✅* |
| 2 | Proveri | **Nema** polja za naziv i sliku — samo admin menja |

---

## 6. UES DEo — Elasticsearch + MinIO + S1 ⭐

> **Ovo je najvažnije za ocenu 10.** Prođi svaki podtest redom.

### 6.0 Priprema UES testa

1. Proveri ES: http://localhost:9200
2. Proveri MinIO: http://localhost:9001 → bucket `newnow-files`
3. **Obavezno:** Exit Festival (id=1) mora imati uploadovan PDF  
   - Admin → Lokacije → Exit Festival → upload PDF → Sačuvaj  
   - ILI već postoji iz prethodnog testa
4. Otvori **🔍 Pretraži** → `/search`
5. Klik **❓ Pomoć za pretragu** — pročitaj sintaksu

---

### 6.1 MinIO — upload i download

#### Test M-1: Upload slike (K3)

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Admin → dodaj/uredi lokaciju → **📷 Upload slike** | Alert uspeh |
| 2 | MinIO konzola → `newnow-files/images/` | Novi fajl (UUID) |
| 3 | `/locations` — kartica te lokacije | Slika se prikazuje |

#### Test M-2: Upload PDF (S1 + MinIO)

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Admin → Exit Festival → **📄 Upload PDF** | Alert uspeh |
| 2 | MinIO → `newnow-files/pdfs/` | PDF fajl |
| 3 | Backend konzola | Log: PDF uploadovan + reindeksiran |
| 4 | ES: `GET localhost:9200/locations/_doc/1` | Polje `pdfContent` nije prazno |

#### Test M-3: Download PDF

| # | Gde | Očekuješ |
|---|-----|----------|
| 1 | `/locations` → **📄 PDF** | PDF se preuzima |
| 2 | `/locations/1` → **📄 Preuzmi PDF** | PDF se preuzima |
| 3 | `/search` → rezultat → **📥 Preuzmi PDF** | PDF se preuzima |
| 4 | Admin → kartica lokacije → **📄** | PDF se preuzima |

---

### 6.2 S1 — Napredna pretraga (`/search`)

**Osnova:** za svaki test → popuni polje → **🔍 Pretraži** → proveri rezultate → **✖ Resetuj** pre sledećeg.

---

#### S1a — MatchQuery (obična pretraga)

| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🏷️ **Naziv mesta** | `festival` | Više rezultata (Exit Festival, Primavera...) |
| Highlight | — | Reč `festival` **označena** (žuto `<mark>`) |

---

#### S1b — PhraseQuery (tačna fraza)

| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🏷️ **Naziv mesta** | `"Exit Festival"` | **Samo** Exit Festival (sa navodnicima!) |
| Bez navodnika | `Exit Festival` | Više rezultata (MatchQuery) — uporedi razliku |

---

#### S1c — PrefixQuery (naziv)

| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🏷️ **Naziv mesta** | `Exit*` | Exit Festival + Exit Club (sve što počinje sa Exit) |
| Case | `exit*` | Isto (case insensitive) |

---

#### S1d — FuzzyQuery (greška u kucanju)

| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🏷️ **Naziv mesta** | `~exittt` | Exit Festival (toleriše greške) |
| 🏷️ **Naziv mesta** | `~festival` | Lokacije sa "festival" u nazivu |

---

#### S1e — BooleanQuery (AND / OR)

**Test AND:**
| Polje | Unos | Operator | Očekuješ |
|-------|------|----------|----------|
| Naziv | `Exit` | **AND** | — |
| Opis | `festival` | **AND** | Uži skup (oba uslova) |

**Test OR:**
| Polje | Unos | Operator | Očekuješ |
|-------|------|----------|----------|
| Naziv | `Exit` | **OR** | — |
| Opis | `muzika` | **OR** | Širi skup (dovoljan jedan uslov) |

---

#### S1f — RangeQuery (opsezi)

**Test prosečne ocene:**
| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🌟 Prosečna ocena **od** | `7` | Samo mesta sa ocenom ≥ 7 |
| 🌟 Prosečna ocena **do** | `10` | — |

**Test broja utisaka:**
| Polje | Unos | Očekuješ |
|-------|------|----------|
| ⭐ Minimalan broj utisaka | `3` | Mesta sa ≥ 3 utiska |

**Test kategorija** (klik **▼ Prikaži filtre po kategorijama ocena**):
| Polje | Unos | Očekuješ |
|-------|------|----------|
| 🎤 Nastup od | `8` | Mesta sa visokom ocenom nastupa |
| 🔊 Zvuk i svetlo od | `7` | Filtrirano po kategoriji |

---

#### S1g — PrefixQuery (opis i PDF)

> **Važno:** PDF prefix radi samo ako je PDF uploadovan i indeksiran.

| Polje | Unos | Očekuješ |
|-------|------|----------|
| 📝 **Opis mesta** | `festival*` | Exit Festival (+ drugi sa "festival" u opisu) |
| 📝 **Opis mesta** | `muz*` | Lokacije sa rečju koja počinje sa "muz..." |
| 📝 **Opis mesta** | `najve*` | Exit Festival ("Najveći muzički festival...") |
| 📄 **Sadržaj PDF** | `festival*` | Exit Festival (ako ima PDF sa tim tekstom) |
| 📄 **Sadržaj PDF** | `Exit*` | Exit Festival (ako PDF sadrži "Exit") |

---

#### S1h — Highlighter

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Naziv: `festival` → **Pretraži** | U kartici: `<mark>festival</mark>` u nazivu |
| 2 | Opis: `muzički` → **Pretraži** | Highlight u sekciji **📝 Opis:** |
| 3 | PDF: reč iz PDF-a → **Pretraži** | Highlight u **📄 Iz PDF dokumenta:** |

---

#### S1i — MLT, score, sortiranje, analizer

**More Like This (MLT):**
| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Pretraži `Exit Festival` (bilo koji upit koji ga nađe) | Kartica Exit Festival |
| 2 | Klik **🔗 Slična mesta** | Sekcija **🔗 Slična mesta** ispod — ~5 sličnih |
| 3 | Klik **Poseti** na sličnom mestu | Otvara detalje tog mesta |

**Relevantnost (score):**
| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Match upit koji vraća više rezultata | Rezultati sa različitim score vrednostima |
| 2 | Sortiraj po nazivu vs po oceni | Različit redosled |

**Srpski analizer (case + ćirilica/latinica):**
| # | Unos | Očekuješ |
|---|------|----------|
| 1 | `EXIT` vs `exit` vs `Exit` | Isti rezultati (case insensitive) |
| 2 | Ćirilica naziv mesta (ako postoji u bazi) | Pronalazi latinicom |

**Poseti mesto:**
| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Klik **Poseti mesto** na kartici rezultata | `/locations/:id` |

---

### 6.3 ES reindeksiranje (admin)

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Dodaj novu lokaciju / utisak / PDF | ES se ažurira automatski |
| 2 | (Opciono API) `POST /api/search/reindex` kao admin | Sve lokacije reindeksirane |
| 3 | Pretraga odmah posle | Novi podaci vidljivi |

---

### 6.4 UES checklist (označi ✓ dok testiraš)

- [ ] **M-1** MinIO upload slike
- [ ] **M-2** MinIO upload PDF + ES pdfContent
- [ ] **M-3** PDF download (4 mesta)
- [ ] **S1a** MatchQuery
- [ ] **S1b** PhraseQuery
- [ ] **S1c** PrefixQuery naziv
- [ ] **S1d** FuzzyQuery
- [ ] **S1e** AND / OR
- [ ] **S1f** Range (ocena, utisci, kategorije)
- [ ] **S1g** Prefix opis + PDF
- [ ] **S1h** Highlight `<mark>`
- [ ] **S1i** MLT + Poseti mesto
- [ ] MinIO konzola prikazuje fajlove
- [ ] ES `:9200` radi

---

## 7. K4 — Događaji (menadžer)

**Uloga:** `petar@manager.com` / `petar123`

| # | Manager Dashboard | Očekuješ |
|---|-------------------|----------|
| 1 | **+ Dodaj događaj** | Modal forma |
| 2 | Popuni: naziv, tip, datum (budućnost), slika URL, redovan da/ne, cena `0` = besplatno | — |
| 3 | **✅ Sačuvaj** | *Događaj dodat ✅* |
| 4 | **✏️** izmeni događaj | Promene sačuvane |
| 5 | **🗑️** obriši test događaj | *Događaj obrisan ✅* |
| 6 | `/locations/1` — predstojeći | Novi događaj vidljiv |

---

## 8. K6 — Pretraga i filtriranje događaja

**Uloga:** gost — **🎉 Događaji** → `/events`

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Default: **📅 Samo danas** | Samo današnji ILI *Nema događaja za danas* |
| 2 | **📆 Svi događaji** | Svi događaji u bazi |
| 3 | Search: ukucaj `Techno` | Filtrirano po nazivu |
| 4 | Search: ukucaj deo **adrese** mesta | Filtrirano po adresi |
| 5 | Dropdown tip događaja | Samo izabrani tip |
| 6 | **Datum** (date picker) | Događaji tog datuma |
| 7 | Cena: **Besplatno** | `price` null ili 0 → **💚 BESPLATNO** |
| 8 | Cena: **Plaćeno** | Samo sa cenom > 0 |
| 9 | **✕ Resetuj** | Default filteri |

---

## 9. K5 — Ostavljanje utiska

**Uloga:** običan korisnik (ulogovan)

| # | `/locations/1` → **✍️ Ostavite svoj utisak** | Očekuješ |
|---|---------------------------------------------|----------|
| 1 | **🎪 Izaberite događaj** — prošli redovan | Dropdown sa prošlim događajima |
| 2 | Pomeri klizače ocena (1–10) | Vrednosti 1–10 |
| 3 | Komentar **prazan** (opciono) | Dozvoljeno |
| 4 | **Objavi utisak** | *Utisak uspešno dodat! ✅* |
| 5 | Prosečna ocena mesta | Ažurirana |
| 6 | Ponovi isti događaj | Greška — već postoji utisak |

---

## 10. K7 — Sortiranje utisaka

**Na** `/locations/1` → sekcija **💬 Utisci korisnika**

| # | Sortiraj po | Redosled | Očekuješ |
|---|-------------|----------|----------|
| 1 | **Datumu** | **Opadajuće** | Najnoviji prvi |
| 2 | **Datumu** | **Rastuće** | Najstariji prvi |
| 3 | **Oceni** | **Opadajuće** | Najbolja ocena prva |
| 4 | **Oceni** | **Rastuće** | Najniža ocena prva |

---

## 11. M2 — Menadžer: utisci

**Uloga:** `petar@manager.com` / `petar123` → **🏢 Manager Dashboard**

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | Sekcija recenzija — lista utisaka | Svi utisci za Exit Festival |
| 2 | **👁️ Sakrij** | Oznaka sakriveno; **nije** na javnoj stranici mesta |
| 3 | Proveri prosečnu ocenu mesta | **Ista** (sakriven se i dalje računa) |
| 4 | **👁️ Prikaži** | Utisak se vraća na javnu stranicu |
| 5 | **🗑️ Obriši** | Utisak nestaje; **ocena se preračunava** |

---

## 12. M3 — Komentari (neograničena dubina)

### Na Manager Dashboardu

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | **💬 Odgovori** na utisak | Modal → unesi tekst → **✅ Pošalji odgovor** |
| 2 | **💬 Prikaži komentare** | Thread vidljiv |

### Na stranici mesta (javno)

**Uloga:** uloguj se kao korisnik → `/locations/1`

| # | Akcija | Očekuješ |
|---|--------|----------|
| 1 | **💬 Prikaži komentare** | Vidi menadžerov odgovor |
| 2 | **↩ Odgovori** na komentar | Unesi tekst → **Pošalji** |
| 3 | **↩ Odgovori** na taj odgovor (3. nivo) | Radi rekurzivno |
| 4 | Menadžer odgovori ponovo | 4.+ nivo radi |

---

## 13. M4 — Analitika

**Uloga:** `petar@manager.com` / `petar123`

| # | Dropdown → **📊 Analitika** | Očekuješ |
|---|------------------------------|----------|
| 1 | Lokacija: **Exit Festival** | — |
| 2 | **Nedeljno** → **Prikaži analitiku** | KPI kartice popunjene |
| 3 | **Mesečno** → ponovo | Drugi brojevi |
| 4 | **Godišnje** → ponovo | Drugi brojevi |
| 5 | **Prilagođeno** — unesi datume | Podaci za taj period |
| 6 | Pie: **Redovni vs Neredovni** | Grafikon |
| 7 | Pie: **Besplatni vs Plaćeni** | Grafikon |
| 8 | Bar: **Događaji po mesecu** | Grafikon |
| 9 | Radar: **Prosečne ocene po kategorijama** | Grafikon (ako ima utisaka) |
| 10 | Tabela **🏆 Top događaji** | Top 5 po učestalosti |
| 11 | Tabela **⭐ Top 5 mesta** | Po oceni |
| 12 | Tabela **📉 Top 5 sa najnižom ocenom** | Bottom lokacije |
| 13 | **💬 Najskorija 3 utiska** | 3 kartice utisaka |

---

## 14. A2 — Dodela menadžera

**Uloga:** admin

| # | Admin panel → **👥 Menadžeri** | Očekuješ |
|---|--------------------------------|----------|
| 1 | **Dodeli menadžera** → izaberi usera → potvrdi | *Menadžer dodeljen ✅* |
| 2 | User postaje MANAGER; vidi Manager Dashboard | — |
| 3 | **Promeni menadžera** | Stari skida prava ako nema drugih mesta |
| 4 | **Ukloni** menadžera | *Menadžer uklonjen ✅* |

---

## 15. K9 — Promena lozinke

**Uloga:** bilo koji ulogovan korisnik

| # | **👤 Profil** | Očekuješ |
|---|---------------|----------|
| 1 | **🔐 Promeni lozinku** | Forma se otvara |
| 2 | Trenutna + nova + potvrda → **Potvrdi promenu** | *Lozinka uspešno promenjena ✅* |
| 3 | Odjavi se → login sa **novom** lozinkom | Uspeh |
| 4 | Pogrešna trenutna lozinka | Greška |

---

## 16. K10 — Profil korisnika

| # | **👤 Profil** | Očekuješ |
|---|---------------|----------|
| 1 | Prikaz: ime, email, uloga, adresa | Podaci tačni |
| 2 | **✏️ Izmeni profil** → promeni ime/adresu → **Sačuvaj** | Ažurirano |
| 3 | **💬 Moji utisci** | Lista tvojih utisaka |
| 4 | (Menadžer) **📍 Moja mesta** | Linkovi ka lokacijama |
| 5 | **🚪 Odjavi se** | Odjava |

---

## 17. NF1–NF3 — Nefunkcionalni zahtevi

| Zahtev | Kako testiraš | Očekuješ |
|--------|---------------|----------|
| **NF1** JWT | Običan user ne vidi Admin panel; `/admin` redirect | Zaštita radi |
| **NF1** | Admin vidi listu korisnika bez password polja | Bezbedan API |
| **NF2** Log4j | Backend konzola tokom login/CRUD/pretrage | INFO/ERROR poruke |
| **NF3** Maven | `.\mvnw.cmd compile` | BUILD SUCCESS |

---

## 18. Preporučen redosled za puno testiranje (~45 min)

```
Priprema (0) → UES MinIO (6.1) → UES S1a–S1i (6.2) → UES checklist (6.4)
→ K8 → K6 mesta (5.1) → K6 događaji (8)
→ K1 → A1 → K2
→ K3 detalji (5.2) → K5 → K7
→ K10 → K9 → K2 odjava
→ K4 → M1 → M2 → M3 → M4
→ A2 → K3 admin (5.3)
→ NF1–NF3 (17)
```

---

## 19. Finalna tabela — označi dok testiraš

| Zahtev | Testirao | Prošao | Napomena |
|--------|:--------:|:------:|----------|
| K1 | ☐ | ☐ | |
| K2 | ☐ | ☐ | |
| K3 | ☐ | ☐ | |
| K4 | ☐ | ☐ | |
| K5 | ☐ | ☐ | |
| K6 | ☐ | ☐ | mesta + događaji |
| K7 | ☐ | ☐ | |
| K8 | ☐ | ☐ | |
| K9 | ☐ | ☐ | |
| K10 | ☐ | ☐ | |
| M1 | ☐ | ☐ | |
| M2 | ☐ | ☐ | sakrij vs obriši |
| M3 | ☐ | ☐ | |
| M4 | ☐ | ☐ | + bottom tabela |
| A1 | ☐ | ☐ | odobri + odbij |
| A2 | ☐ | ☐ | |
| **S1 / UES** | ☐ | ☐ | |
| S1a Match | ☐ | ☐ | |
| S1b Phrase | ☐ | ☐ | |
| S1c Prefix naziv | ☐ | ☐ | |
| S1d Fuzzy | ☐ | ☐ | |
| S1e AND/OR | ☐ | ☐ | |
| S1f Range | ☐ | ☐ | |
| S1g Prefix opis/PDF | ☐ | ☐ | PDF mora postojati |
| S1h Highlight | ☐ | ☐ | |
| S1i MLT | ☐ | ☐ | |
| MinIO slike | ☐ | ☐ | |
| MinIO PDF | ☐ | ☐ | |
| NF1 | ☐ | ☐ | |
| NF2 | ☐ | ☐ | |
| NF3 | ☐ | ☐ | |

---

*Fajl: `newnow/TESTIRANJE-KOMPLETNO.md` — koristi uz `OBRANA-TEST-PLAN.md` za odbranu i pitanja profesora.*
