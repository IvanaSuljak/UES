# NewNow — Jednostavan vodič za učenje (odbrana)

> **Cilj:** da razumeš projekat i da znaš šta da kažeš profesoru.  
> Pišem jednostavno — bez komplikovanja.

---

## 1. Šta je projekat? (zapamti ovo)

**NewNow** je sajt gde:
- korisnici vide **mesta** i **događaje**
- ostavljaju **utiske** (ocene 1–10)
- **pretražuju mesta napredno** (to je UES — glavna stvar za ocenu 10)

**Tehnologije:**
| Deo | Šta radi |
|-----|----------|
| **Angular** (localhost:4200) | Ono što vidiš u browseru |
| **Spring Boot** (localhost:8080) | Backend — logika, baza, bezbednost |
| **MySQL** | Glavna baza — korisnici, mesta, utisci |
| **Elasticsearch** | Samo za **naprednu pretragu** (UES) |
| **MinIO** | Čuva **slike** i **PDF** fajlove |

**Zašto dve baze (MySQL + Elasticsearch)?**
- MySQL = normalan rad aplikacije (dodaj, izmeni, obriši)
- Elasticsearch = brza pametna pretraga (greške u kucanju, fraze, ocene…)

MySQL to ne ume dobro. Zato imamo ES.

---

## 2. Tri tipa korisnika

| Ko | Email za demo | Šta radi |
|----|---------------|----------|
| **Admin** | admin@newnow.com / admin123 | Sve — odobrava registracije, pravi mesta, dodeljuje menadžere |
| **Menadžer** | petar@manager.com / petar123 | Upravlja **svojim** mestom (Exit Festival) |
| **Običan korisnik** | posle registracije | Utisci, komentari, profil |

---

## 3. Kako pokrenuti (pre odbrane)

```
1. Uključi Docker Desktop
2. U folderu newnow: docker-compose up -d
3. Sačekaj 30 sekundi
4. .\mvnw.cmd spring-boot:run
5. U folderu frontend: npm start
6. Otvori http://localhost:4200
```

---

## 4. UES — NAJVAŽNIJI DEO (nauči ovo prvo)

### 4.1 Šta je UES jednom rečenicom?

> *„Napredna pretraga mesta preko Elasticsearch-a — korisnik unese uslove, aplikacija nađe mesta i pokaže rezultate sa obeleženim rečima."*

**Gde u aplikaciji:** Navbar → **Pretraži** → `/search`

---

### 4.2 Kako to radi (3 koraka — lako)

```
1. TI uneseš u formu (npr. Exit*, ocena od 5…)
        ↓
2. Backend pošalje upit u Elasticsearch
        ↓
3. Rezultati se vrate — sa žutim obeležavanjem reči
```

**Važno:** Podaci o mestima su u MySQL. Ali za pretragu se **kopiraju** u Elasticsearch (zove se **reindeks**). Kad neko ostavi utisak ili uploaduje PDF — podaci se ponovo kopiraju u ES.

**Jedna rečenica za profesora:**
> *„Elasticsearch nije automatski vezan za MySQL — mi eksplicitno reindeksiramo posle promena."*

---

### 4.3 Gde je kod (samo 4 fajla — zapamti)

| Fajl | Za šta |
|------|--------|
| `search.component.ts` | Forma u browseru |
| `SearchController.java` | API `/api/search/locations` |
| `LocationSearchService.java` | Pravi ES upite (Match, Phrase, Fuzzy…) |
| `settings.json` | Srpski analyzer |

---

### 4.4 Sintaksa pretrage — ŠTA UNESI i ŠTA DOBIJEŠ

Ovo je **najbitnije za odbranu**. Otvori `/search` i probaj:

| Unos u polje | Naziv u specifikaciji | Šta radi | Primer |
|--------------|----------------------|----------|--------|
| `exit festival` | **S1a Match** | Traži reči bilo gde | Nađe Exit Festival, Exit Club |
| `"Exit Festival"` | **S1b Phrase** | Tačna fraza (navodnici!) | Samo Exit Festival |
| `Exit*` | **S1c Prefix** | Sve što **počinje** sa Exit | Exit Festival, Exit Club |
| `~Exittt` | **S1d Fuzzy** | Toleriše grešku u kucanju | Nađe Exit uprkos typo-u |
| `festival*` u **Opis** ili **PDF** | **S1g Prefix opis** | Reči koje počinju sa festival | Exit Festival |
| AND / OR dugmad | **S1e Boolean** | AND = oba uslova; OR = bar jedan | Vidi primer ispod |
| Ocena od 5, utisci od 1… | **S1f Range** | Filtrira po brojevima | Exit Festival |
| (automatski) | **S1h Highlight** | Žuto obeležava reč | `<mark>Festival</mark>` |
| Dugme Slična mesta | **S1i MLT** | Mesta slična ovom | 5 sličnih |
| (automatski) | **Score** | Broj — koliko odgovara upitu | Veći = relevantnije |

**Razlika `Exit*` i `festival*`:**
- `Exit*` u polju **Naziv** → prefiks celog naziva (S1c)
- `festival*` u polju **Opis** → prefiks reči unutar teksta (S1g)

---

### 4.5 Primeri koje moraš znati

**AND (oba moraju da važe):**
- Naziv: `Exit`, Opis: `muzički` → **Exit Festival** ✓
- Naziv: `Exit`, Opis: `muzika` → **0 rezultata** ✓ (u opisu piše „muzički", ne „muzika" — to nije greška!)

**OR (dovoljan je jedan):**
- Naziv: `Exit`, Opis: `muzika` → **više rezultata** ✓

**Range (filter po ocenama):**
- Ocena od 5, utisci min 1 → **Exit Festival** ✓
- Nastup od 8 → **0 rezultata** ✓ (Exit ima nastup ~6.6 — filter je previsok)

**Prazna pretraga (ništa ne uneseš):**
- Vraća **sva mesta** (do 50)

---

### 4.6 Srpski analyzer — objasni ovako

**Problem:** Korisnik može ukucati `EXIT`, `exit`, `Излаз`, `šume`…  
**Rešenje:** Pre nego što ES sačuva ili traži reč, tekst se **normalizuje**.

**Tri koraka:**
1. Ćirilica → latinica (`Излаз` → `Izlaz`)
2. Sve mala slova (`EXIT` → `exit`)
3. Skini dijakritike (`šume` → `sume`)

**Gde je:** fajl `settings.json`, ime analizera: `serbian_analyzer`

**Jedna rečenica za profesora:**
> *„Custom analyzer normalizuje ćirilicu i dijakritike da pretraga uvek nađe isto, bez obzira kako je korisnik ukucao."*

---

### 4.7 PDF i MinIO (kratko)

1. Admin/menadžer **uploaduje PDF** za mesto
2. PDF ide u **MinIO** (kao fajl u cloud-u)
3. Pri reindeksu, aplikacija **pročita tekst iz PDF-a** i stavi ga u Elasticsearch (`pdfContent`)
4. Možeš pretraživati polje **„Sadržaj PDF"** na `/search`

---

### 4.8 Demo za profesora (3 minute — uradi redom)

1. Otvori `/search`
2. Unesi `"Exit Festival"` → pokaži da je samo jedan rezultat (**Phrase**)
3. Unesi `Exit*` → dva rezultata (**Prefix**)
4. Unesi `~Exittt` → nađe Exit (**Fuzzy**)
5. Unesi `exit festival` → više rezultata + **score** + **žuto obeležavanje**
6. Stavi ocenu od 5 → Exit Festival (**Range**)
7. Klikni **Slična mesta** (**MLT**)
8. Reci: *„Podaci idu iz MySQL u ES pri startu i posle svake promene — reindeks."*

---

## 5. Ostatak aplikacije (kraće — ali znaš šta je)

### Registracija i login (K1, K2)

**K1:** Ne praviš nalog odmah. Popuniš formu → admin **odobri** → tek onda možeš login.  
**K2:** Login daje **JWT token** (kao propusnica). Bez tokena ne možeš na zaštićene stranice.

**Za profesora:** *„Registracija ide preko zahteva — admin kontroliše ko ulazi."*

---

### Mesta i događaji (K3, K4)

- **Admin** pravi/briše sva mesta, uploaduje slike
- **Menadžer** menja samo opis, adresu, tip — **ne** naziv
- **Događaji** pripadaju mestu (koncert, festival…)

**Gde:** `/locations`, `/events`, `/admin`, `/manager`

---

### Utisci (K5)

Korisnik na stranici mesta oceni 1–10 (nastup, zvuk, prostor…).  
Posle toga se **ponovo indeksira** mesto u ES (da range filteri vide nove ocene).

---

### Komentari (M3)

Ispod utiska možeš komentarisati. Možeš odgovoriti na komentar — **beskonačno duboko** (reply na reply).

---

### Analitika (M4)

Menadžer na `/analytics` vidi grafike. Ako je prazno — izaberi **Godišnje** period.

---

### Admin (A1, A2)

- **A1:** Tab „Zahtevi" — odobri ili odbij registraciju
- **A2:** Dodeli ili ukloni menadžera sa mesta

---

### Bezbednost (NF1)

- Lozinka se **nikad** ne vraća u JSON-u
- Ko sme šta → fajl `SecurityConfig.java`
- Pretraga `/search` je **javna** (ne treba login)

---

## 6. Pitanja profesora — kratki odgovori

| Pita | Odgovori |
|------|----------|
| Šta je projekat? | Platforma za mesta, događaje, utiske + napredna pretraga |
| Zašto Elasticsearch? | Brza pretraga sa greškama, frazama, ocenama — MySQL to ne ume |
| Šta je reindeks? | Kopiranje podataka iz MySQL u ES posle promene |
| text vs keyword? | text = reči (pretraga); keyword = ceo naziv (Exit*) |
| Šta radi analyzer? | Ćirilica, mala slova, bez dijakritika |
| Match vs Phrase? | Match = reči bilo gde; Phrase = tačan redosled u navodnicima |
| Zašto 0 rezultata na filteru? | Filter previsok — nije bug |
| AND vs OR? | AND = svi uslovi; OR = bar jedan. Range uvek mora da važi |

---

## 7. Šta učiti redom (večeras)

**Sat 1 — UES**
- Pročitaj sekciju 4 ovog fajla
- Pokreni app, probaj svaki red iz tabele 4.4
- Vežbaj demo iz 4.8

**Sat 2 — Ostatak**
- Pročitaj sekciju 5
- Uloguj se kao admin i menadžer — prođi kroz admin panel i manager dashboard
- Pročitaj sekciju 6 (pitanja)

**Ako hoćeš više detalja o UES:**
- `ODBRANA-UES-VODIC.md` — sve o pretrazi
- `ODBRANA-KOMPLETNO.md` — ceo projekat + fajlovi u kodu

---

## 8. Jedna rečenica za početak odbrane

> *„Implementirala sam web aplikaciju NewNow sa Spring Boot backendom i Angular frontendom. Glavna funkcionalnost za ocenu 10 je napredna pretraga mesta preko Elasticsearch-a sa custom srpskim analizerom, svim tipovima upita iz specifikacije, highlight-om i More Like This pretragom. Podaci se sinhronizuju iz MySQL-a reindeksiranjem, a PDF sadržaj se čita iz MinIO storage-a."*

---

*Srećno na odbrani!*
