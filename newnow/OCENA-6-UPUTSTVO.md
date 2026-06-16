# Ocena 6 — Registracija, prijava, odjava, admin odobrenje

> Zahtevi: **K1**, **K2**, **A1** + JWT autentifikacija/autorizacija + log4j logovanje.

---

## Sadržaj

1. [Šta ocena 6 pokriva](#1-šta-ocena-6-pokriva)
2. [Tok registracije (K1 + A1)](#2-tok-registracije-k1--a1)
3. [Tok prijave i odjave (K2)](#3-tok-prijave-i-odjave-k2)
4. [JWT — kako radi](#4-jwt--kako-radi)
5. [Spring Security — ko sme šta](#5-spring-security--ko-sme-šta)
6. [Izmenjeni fajlovi — pregled](#6-izmenjeni-fajlovi--pregled)
7. [Detalj po fajlu — logika i odbrana](#7-detalj-po-fajlu--logika-i-odbrana)
8. [Frontend — šta je urađeno](#8-frontend--šta-je-urađeno)
9. [Log4j2](#9-log4j2)
10. [Kako testirati u browseru](#10-kako-testirati-u-browseru)
11. [Pitanja za odbranu](#11-pitanja-za-odbranu)

---

## 1. Šta ocena 6 pokriva

| Zahtev | Opis | Status |
|--------|------|--------|
| **K1** | Zahtev za registraciju (ne direktna registracija) | ✅ |
| **K2** | Prijava i odjava | ✅ |
| **A1** | Admin prihvata/odbija zahtev + email | ✅ |
| **NF** | Autentifikacija email+lozinka, autorizacija tokenom | ✅ |
| **NF** | Logovanje važnih događaja (log4j) | ✅ |

---

## 2. Tok registracije (K1 + A1)

```
Korisnik (/register)
    │
    ▼ POST /api/account-requests
AccountRequestController.createRequest()
    │  - provera duplog emaila
    │  - lozinka se hešira (BCrypt)
    │  - status = PENDING
    ▼
Tabela: account_requests
    │
    ▼ Admin (/admin) — mora biti ulogovan kao ADMIN
AccountRequestController.approve() ili reject()
    │
    ├── APPROVE → kreira User (enabled=true) + email odobrenja
    └── REJECT  → samo menja status + email odbijanja
```

**Važno za odbranu:**
- Korisnik **ne može** da se prijavi dok admin ne odobri zahtev.
- Uklonjen je stari endpoint `POST /api/auth/register` koji je zaobilazio K1.
- Lozinka u `AccountRequest` se ne vraća u JSON odgovoru (`@JsonProperty(access = WRITE_ONLY)` — prima se pri registraciji, ne šalje nazad).

---

## 3. Tok prijave i odjave (K2)

### Prijava

```
LoginComponent → POST /api/auth/login
    │
    ▼ AuthController.login()
    1. Pronađi User po emailu
    2. Proveri lozinku (BCrypt)
    3. Proveri enabled == true
    4. Generiši JWT token
    5. Vrati { token, role, email, fullName }
    │
    ▼ Frontend
    localStorage.setItem('token', ...)
    AuthService.login() → navbar prikazuje korisnika
```

### Specijalne poruke pri loginu

| Situacija | Poruka |
|-----------|--------|
| Pogrešna lozinka | „Pogrešan email ili lozinka." |
| Zahtev PENDING | „Vaš zahtev je na čekanju..." |
| Zahtev REJECTED | „Vaš zahtev je odbijen..." |
| Nalog disabled | „Nalog nije aktiviran..." |

### Odjava

JWT je **stateless** — server ne čuva sesiju.

```
Navbar → AuthService.logout()
    1. POST /api/auth/logout (log u konzoli — log4j)
    2. Briše token iz localStorage
    3. Preusmerava na /login
```

---

## 4. JWT — kako radi

### Klase

| Klasa | Uloga |
|-------|-------|
| `JwtUtil` | Generiše i validira token (potpis HS256, trajanje 10h) |
| `JwtAuthenticationFilter` | Čita `Authorization: Bearer ...` header |
| `CustomUserDetailsService` | Učitava korisnika iz baze za Spring Security |

### Tok zaštićenog zahteva (npr. admin pending lista)

```
1. Frontend šalje: Authorization: Bearer eyJhbG...
2. JwtAuthenticationFilter izvlači email iz tokena
3. CustomUserDetailsService učitava UserDetails
4. SecurityContext dobija ulogu (ADMIN, USER, MANAGER)
5. SecurityConfig proverava hasRole("ADMIN")
6. Controller se izvršava
```

---

## 5. Spring Security — ko sme šta

Fajl: `SecurityConfig.java`

| Endpoint | Ko sme |
|----------|--------|
| `POST /api/account-requests` | Svi (K1) |
| `GET/POST /api/account-requests/**` | Samo **ADMIN** (A1) |
| `POST /api/auth/login`, `/logout`, `/test` | Svi |
| Ostali `/api/**` | Javno (za sada — zatvaramo u oceni 7) |

---

## 6. Izmenjeni fajlovi — pregled

### Backend (Java)

| Fajl | Šta urađeno |
|------|-------------|
| `AuthController.java` | Uklonjena direktna registracija; poboljšan login; dodat logout; log4j |
| `AccountRequestController.java` | Validacije, log4j, provera PENDING statusa |
| `AccountRequest.java` | `@JsonProperty(WRITE_ONLY)` na lozinci — prima se u POST, ne izlazi u GET |
| `AccountRequestRepository.java` | `findByEmail`, `existsByEmailAndStatus` |
| `SecurityConfig.java` | Zaštita admin endpointa; `@EnableMethodSecurity` |
| `JwtAuthenticationFilter.java` | Ispravljen — JWT se sada parsira za admin rute |
| `CustomUserDetailsService.java` | `disabled(!enabled)` |
| `EmailService.java` | Log4j umesto System.out |
| `pom.xml` | Dodat `spring-boot-starter-log4j2` |
| `log4j2.xml` | Konfiguracija logovanja |

### Frontend (Angular)

| Fajl | Šta urađeno |
|------|-------------|
| `auth.service.ts` | `getAuthHeaders()`, `isAdmin()`, logout API poziv |
| `guards/auth.guard.ts` | **adminGuard** i **authGuard** |
| `app.routes.ts` | `/admin` zaštićen adminGuard-om |
| `admin-dashboard.ts` | JWT header na approve/reject/pending |
| `login.ts` | Prikaz server poruke greške |

---

## 7. Detalj po fajlu — logika i odbrana

### `AccountRequestController.java`

**`createRequest()` — K1**
- Validira obavezna polja
- Odbija ako email već postoji u `users`
- Odbija ako već postoji PENDING zahtev za isti email
- Hešira lozinku pre čuvanja

**`approveRequest()` — A1**
- Samo PENDING zahtevi
- Kreira `User` sa `enabled=true`, uloga `USER`
- Lozinka se prenosi već heširana iz zahteva
- Šalje email (mock)
- Loguje događaj

**`rejectRequest()` — A1**
- Menja status u REJECTED
- Ne kreira korisnika
- Šalje email odbijanja

### `AuthController.java`

**`initAdmin()`**
- `CommandLineRunner` — izvršava se pri startu aplikacije
- Kreira admin ako ne postoji (predefinisani korisnik iz specifikacije)

**`login()` — K2**
- Ne oslanja se na Spring Security form login — ručna provera
- JWT se izdaje samo za `enabled` korisnike

**`logout()` — K2**
- Stateless: token se ne invalidira na serveru
- Loguje ko se odjavio (za demonstraciju log4j)

### Zašto smo uklonili `/auth/register`?

Specifikacija kaže: *„Korisnik može da se prijavi tek kada je zahtev prihvaćen."*

Stari endpoint je odmah kreirao korisnika sa `enabled=true` — to **krši K1**.

---

## 8. Frontend — šta je urađeno

### `adminGuard`

```typescript
// Samo ulogovan ADMIN može na /admin
if (authService.isLoggedIn() && authService.isAdmin()) return true;
router.navigate(['/login']);
```

### `getAuthHeaders()`

```typescript
return new HttpHeaders({ Authorization: `Bearer ${token}` });
```

Admin dashboard šalje ovaj header na:
- `GET /account-requests/pending`
- `POST /account-requests/{id}/approve`
- `POST /account-requests/{id}/reject`

Bez tokena → backend vraća **403 Forbidden**.

---

## 9. Log4j2

### Zašto?

Specifikacija: *„Za beleženje poruka koristiti log4j API."*

### Šta smo uradili?

1. U `pom.xml` isključili default Logback (`spring-boot-starter-logging`)
2. Dodali `spring-boot-starter-log4j2`
3. Kreirali `log4j2.xml`
4. U kontrolerima: `LogManager.getLogger(...)` umesto SLF4J

### Primer loga

```
2026-06-15 20:30:00 INFO  [http-nio-8080-exec-1] AccountRequestController - K1 — novi zahtev za registraciju: ivana@test.com (Ivana Suljak)
2026-06-15 20:31:00 INFO  [http-nio-8080-exec-2] AccountRequestController - A1 — zahtev odobren: ivana@test.com → kreiran korisnik
2026-06-15 20:32:00 INFO  [http-nio-8080-exec-3] AuthController - K2 — uspešan login: ivana@test.com (USER)
```

---

## 10. Kako testirati u browseru

### Test 1 — Registracija (K1)

1. Otvori `http://localhost:4200/register`
2. Unesi ime, email, lozinku → Pošalji
3. Vidi poruku: „Zahtev poslat..."
4. Pokušaj login → treba poruka „na čekanju"

### Test 2 — Admin odobrenje (A1)

1. Login: `admin@newnow.com` / `admin123`
2. Idi na Admin panel (dropdown → ⚙️ Admin panel)
3. Vidi pending zahtev → Odobri
4. U backend konzoli vidi log4j email poruku

### Test 3 — Login posle odobrenja (K2)

1. Odjavi se
2. Login sa novim nalogom
3. Vidi početnu stranicu, ime u navbar-u

### Test 4 — Odjava (K2)

1. Klikni „Odjavi se"
2. Token obrisan → vidiš Login/Registracija dugmad

### Test 5 — Zaštita admin rute

1. Odjavi se
2. Ručno otvori `http://localhost:4200/admin`
3. Preusmerava te na `/login` (adminGuard)

---

## 11. Pitanja za odbranu

**P: Zašto AccountRequest a ne odmah User?**  
O: K1 zahteva da admin prvo obradi zahtev. AccountRequest je privremeni entitet sa statusom PENDING/APPROVED/REJECTED.

**P: Šta je `enabled` polje na User?**  
O: Označava da li je nalog aktivan. Samo admin može aktivirati nalog odobravanjem zahteva.

**P: Zašto JWT a ne sesija?**  
O: Specifikacija traži token autorizaciju. JWT je stateless — server ne drži sesiju u memoriji.

**P: Ko može da vidi pending zahteve?**  
O: Samo ADMIN. SecurityConfig + JWT filter + adminGuard na frontendu.

**P: Gde se čuva lozinka?**  
O: U bazi kao BCrypt hash. Nikad plain text.

**P: Šta ako neko pošalje approve bez tokena?**  
O: Spring Security vraća 403 jer `/api/account-requests/**` zahteva ROLE_ADMIN.

**P: Da li se token briše sa servera pri logout?**  
O: Ne — JWT je stateless. Klijent briše token iz localStorage. To je standardan pristup.

**P: Zašto mock email?**  
O: Za razvoj je dovoljno. EmailService loguje sadržaj preko log4j. Lako se zameni pravim SMTP-om.

---

## Admin nalog (predefinisani)

| Email | Lozinka |
|-------|---------|
| admin@newnow.com | admin123 |

Kreira se automatski u `AuthController.initAdmin()`.

---

*Sledeći korak: **Ocena 7** — mesta (K3), događaji (K4), pretraga (K6), menadžeri (A2).*
https://github.com/IvanaSuljak/UES.git