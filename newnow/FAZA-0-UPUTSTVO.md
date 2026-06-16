# Faza 0 — Uputstvo za projekat Novi Sad (UES 2026)

> Ovaj dokument sumira sve što smo uradili i objasnili u **Fazi 0** (priprema projekta).
> Koristi ga za učenje, ponavljanje i pripremu odbrane.

---

## Sadržaj

1. [Šta smo uradili u Fazi 0](#1-šta-smo-uradili-u-fazi-0)
2. [Arhitektura aplikacije](#2-arhitektura-aplikacije)
3. [Struktura projekta](#3-struktura-projekta)
4. [Slojevi backenda (MVC)](#4-slojevi-backenda-mvc)
5. [Primer toka: Login](#5-primer-toka-login)
6. [Konfiguracija](#6-konfiguracija)
7. [Admin nalog](#7-admin-nalog)
8. [Kako pokrenuti projekat](#8-kako-pokrenuti-projekat)
9. [Docker Compose (ES + MinIO)](#9-docker-compose-es--minio)
10. [Šta već radi iz prošlog projekta](#10-šta-već-radi-iz-prošlog-projekta)
11. [Gap lista — šta fali za specifikaciju 2026](#11-gap-lista--šta-fali-za-specifikaciju-2026)
12. [Plan rada po ocenama (pregled)](#12-plan-rada-po-ocenama-pregled)
13. [Pitanja za odbranu — Faza 0](#13-pitanja-za-odbranu--faza-0)
14. [Šta je dodato u kodu u Fazi 0](#14-šta-je-dodato-u-kodu-u-fazi-0)
15. [Sledeći korak: Ocena 6](#15-sledeći-korak-ocena-6)

---

## 1. Šta smo uradili u Fazi 0

### Provera okruženja

| Alat | Status | Napomena |
|------|--------|----------|
| Java 21 | ✅ | OpenJDK Temurin |
| Maven (`mvn` globalno) | ❌ | Nije u PATH-u — koristi se **`mvnw.cmd`** (Maven Wrapper) |
| Node.js 22 | ✅ | Za Angular frontend |
| Docker | ⚠️ | Instaliran, ali **Docker Desktop mora biti pokrenut** pre `docker compose` |
| MySQL | ✅ | Baza `newnow` na `localhost:3306` |

### Pokretanje aplikacije

| Servis | URL | Status |
|--------|-----|--------|
| Backend (Spring Boot) | `http://localhost:8080` | ✅ |
| Frontend (Angular) | `http://localhost:4200` | ✅ |
| Test endpoint | `GET http://localhost:8080/api/auth/test` | Vraća `"Auth radi ✅"` |
| Početna API | `GET http://localhost:8080/api/home` | Vraća top lokacije + današnje događaje |

### Dodato u projekat

- Fajl **`docker-compose.yml`** — priprema za Elasticsearch i MinIO (koristi se na **oceni 10**, ne odmah).

---

## 2. Arhitektura aplikacije

```
┌─────────────────┐         REST/JSON          ┌──────────────────────┐
│  Angular (KVA)  │  ◄──────────────────────►  │  Spring Boot (SVT)   │
│  localhost:4200 │         JWT token          │  localhost:8080      │
└─────────────────┘                            └──────────┬───────────┘
                                                          │
                        ┌─────────────────────────────────┼─────────────────┐
                        ▼                                 ▼                 ▼
                   MySQL (SUBP)              Elasticsearch (UES)      MinIO (UES)
                   localhost:3306            localhost:9200           localhost:9000
                   ✅ radi sada              ⏳ kasnije (ocena 10)    ⏳ kasnije (ocena 10)
```

### Ključna rečenica za odbranu

> Aplikacija je raspoređena na tri sloja: klijent (Angular u browseru), server (Spring Boot), i baza podataka (MySQL). Za UES predmet dodajemo Elasticsearch za pretragu i MinIO za čuvanje slika i PDF dokumenata.

### Nefunkcionalni zahtevi (iz specifikacije)

- **Autentifikacija:** email + lozinka
- **Autorizacija:** JWT token
- **Logovanje:** log4j API (trenutno projekat koristi SLF4J — treba uskladiti)
- **Build:** Maven (ili Maven Wrapper)
- **SUBP:** MySQL ili PostgreSQL

---

## 3. Struktura projekta

```
newnow/
├── pom.xml                         # Maven zavisnosti (Spring Boot, Security, JWT, MySQL...)
├── mvnw.cmd                        # Maven Wrapper — build bez globalnog Maven-a
├── docker-compose.yml              # Elasticsearch + MinIO (Faza 0)
├── FAZA-0-UPUTSTVO.md              # Ovaj fajl
│
├── src/main/java/com/example/newnow/
│   ├── NewNowProjectApplication.java   # Ulazna tačka Spring Boot-a
│   ├── controller/                 # REST API — prima HTTP zahteve
│   ├── service/                    # Interfejsi servisa
│   ├── service_impl/               # Implementacija poslovne logike
│   ├── repository/                 # JPA repozitorijumi — pristup bazi
│   ├── model/                      # Entiteti (User, Location, Event, Review...)
│   └── security/                   # JWT, SecurityConfig, filteri
│
├── src/main/resources/
│   └── application.properties      # MySQL konekcija, JPA podešavanja
│
└── frontend/
    └── src/app/
        ├── components/             # UI stranice (home, login, locations...)
        ├── services/               # HTTP pozivi ka backendu
        ├── app.routes.ts           # Rutiranje (URL → komponenta)
        └── environments/
            └── environment.ts      # apiUrl: http://localhost:8080/api
```

### Entiteti u modelu (ne menjati proizvoljno!)

| Entitet | Namena |
|---------|--------|
| `AccountRequest` | Zahtev za registraciju (pre nego postane User) |
| `User` | Registrovan korisnik (autentifikacija) |
| `Administrator` | Admin uloga |
| `Manages` | Veza menadžer ↔ mesto |
| `Location` | Mesto/manifestacija |
| `Event` | Događaj |
| `LocationReview` | Utisak/recenzija |
| `Comment` | Komentar/odgovor na utisak |
| `Image` | Slika |

---

## 4. Slojevi backenda (MVC)

Kako teče jedan HTTP zahtev:

```
HTTP zahtev (JSON)
       ↓
Controller      → prima zahtev, validira, vraća ResponseEntity
       ↓
Service         → poslovna logika (pravila, validacije)
       ↓
Repository      → SQL upiti preko JPA/Hibernate
       ↓
MySQL baza
```

### Uloga svakog sloja

| Sloj | Odgovornost | Primer klase |
|------|-------------|--------------|
| **Controller** | REST endpoint, mapiranje URL-a | `AuthController`, `LocationController` |
| **Service** | Poslovna logika | `UserServiceImpl`, `LocationReviewServiceImpl` |
| **Repository** | CRUD nad bazom | `UserRepository`, `EventRepository` |
| **Model** | JPA entiteti (@Entity) | `User`, `Location`, `Event` |
| **Security** | JWT, filteri, uloge | `JwtUtil`, `SecurityConfig` |

---

## 5. Primer toka: Login

Korak po korak — **K2 Prijava**:

1. Korisnik unese email i lozinku u Angular komponenti `login.ts`
2. Frontend šalje `POST /api/auth/login` sa JSON telom
3. `AuthController.login()` pronalazi korisnika u bazi (`UserRepository`)
4. `PasswordEncoder.matches()` proverava lozinku (BCrypt hash)
5. Proverava se da li je nalog `enabled` (mora biti odobren od strane admina)
6. `JwtUtil.generateToken(email)` generiše JWT token
7. Backend vraća: `{ token, role, email, fullName }`
8. Frontend čuva token (npr. u `localStorage`)
9. Svaki sledeći zahtev šalje header: `Authorization: Bearer <token>`

### Registracija — K1 + A1

1. Neregistrovan korisnik šalje zahtev → entitet `AccountRequest` (status `PENDING`)
2. Admin prihvata ili odbija → kreira se `User` ili se šalje odbijanje
3. Tek **posle prihvatanja** korisnik može da se prijavi (`enabled = true`)
4. Korisniku se šalje email (trenutno mock — ispis u konzoli)

---

## 6. Konfiguracija

### Backend — `src/main/resources/application.properties`

```properties
spring.application.name=NewNow-project

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/newnow?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=lozinka123

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

**Napomena:** Ako ti je drugačija MySQL lozinka, promeni `spring.datasource.password`.

**`ddl-auto=update`:** Hibernate automatski kreira ili ažurira tabele na osnovu `@Entity` klasa. Ne briše podatke pri restartu.

### Frontend — `frontend/src/environments/environment.ts`

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

Svi HTTP pozivi iz Angular servisa idu na ovaj `apiUrl`.

### Email — mock režim

Klasa: `EmailService.java`

Email se **ne šalje stvarno** — sadržaj se ispisuje u konzoli backend-a:

```
📧 ============== EMAIL POSLAT ==============
📬 TO: korisnik@email.com
📋 SUBJECT: ...
📝 BODY: ...
============================================
```

Na odbrani: lako se zameni pravim SMTP serverom (Spring Mail).

---

## 7. Admin nalog

Kreira se **automatski** pri prvom pokretanju aplikacije.

Klasa: `AuthController.java` → metoda `initAdmin()` (`CommandLineRunner`)

| Polje | Vrednost |
|-------|----------|
| Email | `admin@newnow.com` |
| Lozinka | `admin123` |
| Uloga | `ADMIN` |

---

## 8. Kako pokrenuti projekat

### Terminal 1 — Backend

```powershell
cd "c:\Users\ivana\Desktop\svt-kva-2025-IvanaSuljak-main (2)\svt-kva-2025-IvanaSuljak-main\newnow"
.\mvnw.cmd spring-boot:run
```

Čekaj poruku: `Started NewNowProjectApplication`

### Terminal 2 — Frontend

```powershell
cd "c:\Users\ivana\Desktop\svt-kva-2025-IvanaSuljak-main (2)\svt-kva-2025-IvanaSuljak-main\newnow\frontend"
npm start
```

Otvori browser: **http://localhost:4200**

### Brzi test API-ja (PowerShell)

```powershell
# Da li backend radi
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/test"

# Početna stranica podaci
Invoke-RestMethod -Uri "http://localhost:8080/api/home"
```

### Preduslov: MySQL

- Baza mora postojati: `newnow`
- Server na portu `3306`
- Kredencijali kao u `application.properties`

---

## 9. Docker Compose (ES + MinIO)

Fajl: `newnow/docker-compose.yml`

**Koristi se tek na oceni 10** (UES deo). U Fazi 0 samo priprema.

```powershell
# 1. Pokreni Docker Desktop
# 2. Zatim:
cd ...\newnow
docker compose up -d
```

| Servis | Port | Namena |
|--------|------|--------|
| Elasticsearch | 9200 | Napredna pretraga mesta (S1) |
| MinIO API | 9000 | Upload slika i PDF-ova |
| MinIO konzola | 9001 | Web UI (`minioadmin` / `minioadmin123`) |

---

## 10. Šta već radi iz prošlog projekta

| Funkcionalnost | Spec | Backend | Frontend |
|----------------|------|---------|----------|
| Zahtev za registraciju | K1 | ✅ | ✅ |
| Admin obrađuje zahtev | A1 | ✅ | ✅ |
| Login / logout | K2 | ✅ | ✅ |
| JWT token | NF | ✅ | ✅ |
| CRUD mesta | K3 | ✅ | ✅ |
| Dodela menadžera | A2 | ✅ | ✅ |
| CRUD događaja | K4 | ✅ | ✅ |
| Ostavljanje utiska | K5 | ✅ | ✅ |
| Pretraga mesta/događaja | K6 | ⚠️ delimično | ⚠️ |
| Sortiranje utisaka | K7 | ⚠️ backend ne obrađuje sort | ⚠️ UI postoji |
| Početna stranica | K8 | ⚠️ fale 3 utiska | ⚠️ |
| Promena lozinke | K9 | ✅ | ✅ |
| Profil | K10 | ⚠️ slika profila | ⚠️ |
| Sakrivanje/brisanje utisaka | M2 | ✅ | ✅ |
| Reply na utisak | M3 | ⚠️ ne persistira | ⚠️ |
| Analitika + grafici | M4 | ❌ | ❌ |
| Elasticsearch pretraga | S1 / UES | ❌ | ❌ |
| MinIO + PDF | UES | ❌ | ❌ |

---

## 11. Gap lista — šta fali za specifikaciju 2026

Prioritetni problemi koje rešavamo u narednim fazama:

1. **Security** — sve API rute su trenutno `permitAll`; treba role-based zaštita (ADMIN, MANAGER, USER)
2. **Sortiranje utisaka (K7)** — frontend šalje `?sortBy=&order=`, backend endpoint to ne obrađuje
3. **Reply na utisak (M3)** — `ReviewController.reply` ne čuva komentar u bazi (stub)
4. **Homepage (K8)** — fale 3 najskorija utiska sa najpopularnijeg mesta
5. **Autorizacija CRUD-a** — admin samo mesta, menadžer samo događaje na svom mestu
6. **Event adresa (K4)** — specifikacija traži adresu događaja
7. **Manages entitet** — postoji u modelu, ali se koristi `manager_id` na Location
8. **Skala ocena 1–10** — proveriti UI (ne prikazivati /5)
9. **Log4j** — spec traži log4j, projekat koristi SLF4J
10. **UES (ocena 10)** — Elasticsearch, MinIO, PDF parsiranje, S1 napredna pretraga, M4 analitika sa Chart.js

---

## 12. Plan rada po ocenama (pregled)

```
Faza 0: Setup i test                    ← OVDE SMO
    ↓
Ocena 6:  K1, K2, A1 — registracija, login, JWT
    ↓
Ocena 7:  K3, K4, K6, A2 — mesta, događaji, pretraga
    ↓
Ocena 8:  K5, K7, K8, M1, M2 — utisci, sort, homepage
    ↓
Ocena 9:  K9, K10, M3 — profil, lozinka, thread komentara
    ↓
Ocena 10: M4 analitika + UES (MinIO, ES, PDF, S1 pretraga)
```

**Mapiranje boja u PDF specifikaciji:**

| Boja | Ocena |
|------|-------|
| Žuta | 6 |
| Crvena | 7 |
| Zelena | 8 |
| Roze | 9 |
| Plava | 10 |

---

## 13. Pitanja za odbranu — Faza 0

### Šta je Spring Boot?

Framework koji olakšava pravljenje Java web aplikacije: ugrađeni Tomcat, automatska konfiguracija, dependency injection. Ne mora posebno integrisati Tomcat — Spring Boot ga pokreće automatski.

### Šta je JWT i zašto ga koristite?

JSON Web Token — **stateless** autentifikacija. Server ne čuva sesiju u memoriji; korisnik šalje token u headeru svakog zahteva. Specifikacija traži autentifikaciju (email + lozinka) i autorizaciju (token).

### Šta radi `ddl-auto=update`?

Hibernate automatski kreira ili ažurira tabele u MySQL-u na osnovu JPA entiteta. Pri restartu aplikacije podaci ostaju.

### Zašto mock email?

`EmailService` ispisuje sadržaj u konzolu umesto slanja pravog mejla — dovoljno za razvoj i demonstraciju. Lako se zameni Spring Mail + SMTP.

### Šta je Maven Wrapper (`mvnw.cmd`)?

Omogućava build i pokretanje projekta bez globalno instaliranog Maven-a. Maven se preuzima automatski za taj projekat.

### Šta je REST?

Arhitektura gde klijent i server komuniciraju preko HTTP metoda (GET, POST, PUT, DELETE) i JSON formata. Frontend (Angular) poziva backend URL-ove poput `/api/locations`.

### Šta je JPA / Hibernate?

Java Persistence API — mapira Java klase (`@Entity`) na tabele u bazi. Hibernate je implementacija; `Repository` interfejsi nasleđuju `JpaRepository` i dobijaju CRUD metode besplatno.

### Šta je BCrypt?

Algoritam za hashovanje lozinki. Lozinke se **nikad** ne čuvaju u plain text-u. `PasswordEncoder.matches()` upoređuje unetu lozinku sa hash-om iz baze.

### Angular rute — gde su definisane?

Fajl: `frontend/src/app/app.routes.ts`

Primer: `{ path: 'login', component: LoginComponent }` → URL `/login` prikazuje login stranicu.

---

## 14. Šta je dodato u kodu u Fazi 0

| Fajl | Akcija | Razlog |
|------|--------|--------|
| `docker-compose.yml` | **Dodato** | Priprema Elasticsearch + MinIO za ocenu 10 |
| `FAZA-0-UPUTSTVO.md` | **Dodato** | Dokumentacija za učenje i odbranu |

**Nije menjan** postojeći Java/Angular kod — Faza 0 = provera + priprema.

---

## 15. Sledeći korak: Ocena 6

Kada kreneš sa Ocenom 6, radimo:

1. **End-to-end test u browseru:** registracija → admin approve → login → logout
2. **JWT tok:** gde se token čuva, kako se šalje u headeru
3. **Email mock:** gde se vidi u konzoli backend-a
4. **Log4j:** uskladiti sa specifikacijom (ako striktno traže log4j API)
5. **Security:** postepeno zatvaranje ruta po ulogama

### Test stranice u browseru

| URL | Šta testiraš |
|-----|--------------|
| http://localhost:4200 | Početna (K8) |
| http://localhost:4200/register | Registracija (K1) |
| http://localhost:4200/login | Login admin/korisnik (K2) |
| http://localhost:4200/admin | Admin dashboard (A1) |
| http://localhost:4200/locations | Lista mesta (K3/K6) |
| http://localhost:4200/events | Događaji (K4/K6) |
| http://localhost:4200/profile | Profil (K9/K10) |

---

## Korisni endpointi (API referenca — skraćeno)

| Metoda | URL | Namena |
|--------|-----|--------|
| GET | `/api/auth/test` | Health check |
| POST | `/api/auth/login` | Prijava |
| POST | `/api/account-requests` | Zahtev za registraciju |
| GET | `/api/account-requests/pending` | Pending zahtevi (admin) |
| GET | `/api/home` | Početna stranica podaci |
| GET | `/api/locations` | Sva mesta |
| GET | `/api/locations/{id}/details` | Detalji mesta + događaji + ocena |
| POST | `/api/locations/{id}/reviews` | Ostavi utisak |
| GET | `/api/events/today` | Današnji događaji |
| GET | `/api/users/profile` | Profil ulogovanog korisnika |
| PUT | `/api/users/change-password` | Promena lozinke |

---

*Poslednje ažuriranje: Faza 0 — jun 2026.*
