# Ocena 7 — Implementacija i Objašnjenje

## Šta smo implementirali

Za ocenu 7 implementirali smo sledeće funkcionalne zahteve:

- **K3** — Rukovanje mestima: obavezna polja, slika, autorizacija (admin kreira/briše, menadžer ažurira)
- **K4** — Rukovanje događajima: obavezna polja, slika, autorizacija (samo menadžer svog mesta)
- **K6** — Pretraga i filtriranje mesta i događaja (unified search, filter po datumu)
- **A2** — Upravljanje menadžerima: admin dodeljuje/uklanja menadžere (sa autorizacijom)

---

## Fajlovi koji su se menjali

### 1. `LocationController.java` — Kompletan rewrite sa autorizacijom

**Pre:** Svako (čak i anonimni korisnik) mogao je kreirati/menjati/brisati mesta bez ikakve provere.

**Posle:**

| Endpoint | Ko može | Šta se proverava |
|---|---|---|
| `GET /api/locations` | Svi | — |
| `GET /api/locations?name=&address=&type=` | Svi | K6 unified search |
| `POST /api/locations` | Admin | JWT token + ADMIN rola |
| `PUT /api/locations/{id}` | Admin ili menadžer mesta | Admin = sve menja; menadžer = samo address, type, description |
| `DELETE /api/locations/{id}` | Admin | JWT token + ADMIN rola |
| `PUT /api/locations/{id}/assign-manager` | Admin | JWT token + ADMIN rola |
| `DELETE /api/locations/{id}/remove-manager` | Admin | JWT token + ADMIN rola |

**Ključna logika (PUT — menadžer vs admin):**
```java
if (isAdmin) {
    // može da menja name i imageUrl
    location.setName(...);
    location.setImageUrl(...);
}
// i admin i menadžer mogu da menjaju ova tri:
location.setAddress(...);
location.setType(...);
location.setDescription(...);
```

Ova razlika je direktan zahtev iz specifikacije: *"menadžer mesta može da ažurira atribute mesta, poput adrese, tipa mesta i opisa"* — naziv i slika su isključeni.

**Validacija kod kreiranja mesta:**
```java
if (isBlank(nameVal) || isBlank(addressVal) || isBlank(typeVal)
        || isBlank(descVal) || isBlank(imageUrl)) {
    return ResponseEntity.badRequest().body(Map.of(
        "error", "Naziv, adresa, tip mesta, opis i slika su obavezni."
    ));
}
```

**Lista mesta uvek vraća averageRating:**  
`GET /api/locations` sada vraća `List<Map>` umesto `List<Location>` — svaki element sadrži i `averageRating` i `totalReviews` koji se računaju iz tabele recenzija u realnom vremenu.

---

### 2. `EventController.java` — Kompletan rewrite sa autorizacijom

**Pre:** Svako mogao da kreira/menja/briše događaje.

**Posle:**

| Endpoint | Ko može |
|---|---|
| `GET /api/events` i sve GET varijante | Svi |
| `GET /api/events/filter/date?date=YYYY-MM-DD` | Svi — **novi endpoint za K6** |
| `POST /api/events` | Menadžer (svog mesta) |
| `PUT /api/events/{id}` | Menadžer (svog mesta) ili admin |
| `DELETE /api/events/{id}` | Menadžer (svog mesta) ili admin |

**Provera "sopstveno mesto":**
```java
private boolean isManagerOf(User user, Location location) {
    return user.getRole() == Role.MANAGER
            && location.getManager() != null
            && location.getManager().getId().equals(user.getId());
}
```
Menadžer ne može da dodaje događaje na tuđe mesto — proverava se da li je `location.manager.id == currentUser.id`.

**Validacija obaveznih polja:**
```java
if (isBlank(title) || isBlank(type) || isBlank(dateTimeStr)
        || isRegularObj == null || isBlank(imageUrl)) {
    return ResponseEntity.badRequest().body(Map.of(
        "error", "Naziv, tip, datum, redovnost i slika su obavezni."
    ));
}
```

---

### 3. `LocationRepository.java` — Unified search query

Dodali smo novi query koji prima sve tri pretrage odjednom (name, address, type) i filtrira samo one koji odgovaraju:

```java
@Query("SELECT l FROM Location l WHERE " +
       "(:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
       "(:address IS NULL OR LOWER(l.address) LIKE LOWER(CONCAT('%', :address, '%'))) AND " +
       "(:type IS NULL OR LOWER(l.type) LIKE LOWER(CONCAT('%', :type, '%')))")
List<Location> searchLocations(@Param("name") String name,
                                @Param("address") String address,
                                @Param("type") String type);
```

**Logika:** ako je parametar `null`, taj uslov se ignoriše (`:name IS NULL OR ...`). Tako jedan endpoint pokriva sve kombinacije pretrage.

**Koristi se u GET `/api/locations?name=X&type=Y`:**  
Ako je bar jedan filter prosleđen → poziva se `searchLocations()`.  
Ako nema filtera → vraća se `findAllValid()`.

---

### 4. `EventRepository.java` — Filter po datumu

```java
@Query("SELECT e FROM Event e WHERE CAST(e.dateTime AS date) = :date ORDER BY e.dateTime ASC")
List<Event> findByDate(@Param("date") java.time.LocalDate date);
```

`CAST(e.dateTime AS date)` pretvara `LocalDateTime` u `LocalDate` pa poredi samo datum (bez vremena). Ovo omogućava filtriranje događaja koji se dešavaju na **bilo koji datum** — prošlost ili budućnost, što je direktan zahtev K6.

---

### 5. `LocationService.java` + `LocationServiceImpl.java`

Dodat novi metod `searchLocations(name, address, type)` koji delegira na novi repository query.

### 6. `EventService.java` + `EventServiceImpl.java`

Dodat novi metod `findByDate(LocalDate date)` koji delegira na novi repository query.

---

## Logika autorizacije — Helper metoda

U oba kontrollera dodata je helper metoda:

```java
private User extractUser(String authHeader) {
    String email = jwtUtil.extractUsername(authHeader.substring(7));
    return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));
}
```

`authHeader.substring(7)` uklanja prefiks "Bearer " i ostavlja samo JWT token. Iz tokena se izvlači email, koji se koristi za pretragu korisnika u bazi. Ovo je isti pattern koji se koristio u Oceni 6.

---

## SecurityConfig — šta se nije menjalo

SecurityConfig ostaje nepromenjen. Autorizacija se radi **ručno u kontrolerima** (extract token → check role), a ne u SecurityConfig. Ovo je konzistentno sa postojećim pristupom i fleksibilnije — jer nam treba provera "je li menadžer *ovog konkretnog* mesta", a ne samo "je li MANAGER".

---

## A2 — Upravljanje menadžerima (sa autorizacijom)

**Pre:** Ko god je mogao da pozove `/assign-manager` ili `/remove-manager`.

**Posle:** 
- `PUT /api/locations/{id}/assign-manager` — samo admin, inače → 403
- `DELETE /api/locations/{id}/remove-manager` — samo admin, inače → 403

**Šta se dešava pri dodeli:**
```java
manager.setRole(Role.MANAGER);     // korisnik postaje menadžer
userRepository.save(manager);
location.setManager(manager);      // mesto dobija svog menadžera
locationService.save(location);
```

**Šta se dešava pri uklanjanju:**
```java
manager.setRole(Role.USER);        // korisnik se vraća na USER
userRepository.save(manager);
location.setManager(null);         // mesto ostaje bez menadžera
locationService.save(location);
```

Ovo je direktno iz specifikacije: *"Menadžer koji je uklonjen sa objekta postaje običan korisnik."*

---

## API Testovi — Svi prolaze (25/25)

```
T1:  POST mesta bez auth → 401/403          ✅
T2:  POST mesta kao USER → 403              ✅
T3:  POST mesta bez imageUrl → 400          ✅
T4:  Admin kreira mesto → 200               ✅
T5:  Response sadrzi averageRating          ✅
T6:  GET /locations → 200                   ✅
T7:  Lista ima averageRating                ✅
T8:  Admin dodeljuje menadzera → 200        ✅
T9:  Menadzer azurira atribute → 200        ✅
T10: Naziv nije promenjen (menadzer)        ✅
T10b:Adresa je promenjena                   ✅
T11: Menadzer ne moze brisati → 403         ✅
T12: Admin brise mesto → 200                ✅
T13: Menadzer kreira dogadjaj → 200         ✅
T14: POST dogadjaja bez auth → 401/403      ✅
T15: Dogadjaj bez imageUrl → 400            ✅
T16: Menadzer azurira dogadjaj → 200        ✅
T17: Menadzer brise dogadjaj → 200          ✅
T18: Pretraga po imenu → 200                ✅
T19: Pretraga vraca rezultate               ✅
T20: Filter po tipu → 200                   ✅
T21: Pretraga po adresi → 200               ✅
T22: Filter po datumu → 200                 ✅
T23: Filter vraca dogadjaj za sutra         ✅
T24: Filter po proslom datumu → 200         ✅
```

---

## Moguća pitanja na odbrani

**Q: Kako si sprečila da menadžer menja naziv mesta?**  
A: U `PUT /api/locations/{id}`, proveravam ulogu korisnika. Samo blok `if (isAdmin)` sadrži `setName()` i `setImageUrl()`. Menadžer ulazi samo u zajednički blok koji setuje adresu, tip i opis.

**Q: Kako znaš da menadžer radi sa *sopstvenim* mestom (za događaje)?**  
A: Metoda `isManagerOf(user, location)` proverava tri uslova: role == MANAGER, location.manager != null, i location.manager.id == user.id. Sva tri moraju biti tačna.

**Q: Kako radi filter po datumu?**  
A: `GET /api/events/filter/date?date=2026-06-16` — `date` se parsira kao `LocalDate`. U JPQL query-ju, `CAST(e.dateTime AS date)` odstranjuje vremenski deo i upoređuje samo datum. Radi za prošlost i budućnost.

**Q: Kako radi unified search za mesta?**  
A: `GET /api/locations?name=X&address=Y&type=Z` — parametri su opcioni. Ako bar jedan postoji, poziva se `searchLocations()`. JPQL uslov `:param IS NULL OR ... LIKE ...` znači: ako je parametar null, ne filtrira po tom polju.

**Q: Šta se dešava kada admin ukloni menadžera sa mesta?**  
A: Korisnik se vraća na `Role.USER`, a `location.manager` se postavlja na `null`. Ovo je atomična operacija — prvo se menja rola, pa se čuva korisnik, pa se briše menadžer sa mesta.
