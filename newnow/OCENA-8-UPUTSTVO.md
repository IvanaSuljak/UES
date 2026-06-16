# Ocena 8 — K5, K7, K8, K9, K10

## Šta je implementirano

### K5 — Ostavljanje utiska na mesto

**Backend (već postojao):**
- `POST /api/locations/{id}/reviews` — kreira utisak
- Validacija: `eventId` je **obavezan** — mora se izabrati redovan događaj koji se **već održao** na toj lokaciji
- Ako je `isRegular = false` → vraća grešku
- Ako je `dateTime` u budućnosti → vraća grešku
- Ako događaj nije na toj lokaciji → vraća grešku
- Broji koliko se puta taj događaj ukupno održao (`eventOccurrenceCount`) u trenutku pisanja utiska i pamti to polje u bazi
- 4 opcione ocene: nastup, zvuk/svetlo, prostor, ukupan utisak (1–10)
- Komentar je obavezan

**Frontend (promene u `location-details.component.ts` i `.html`):**
- Učitava sve prošle redovne događaje za lokaciju (`GET /api/events/byLocation/{id}`, filtrira na frontendu po `isRegular && dateTime < now`)
- Prikazuje dropdown za izbor događaja — bez izbora se ne može podneti utisak
- Ako nema redovnih prošlih događaja, prikazuje poruku (žuta notifikacija)
- Na svakom utisku se prikazuje naziv događaja i broj puta koliko se taj događaj održao do pisanja utiska

**Odbrambena pitanja:**
- *Zašto mora biti redovan događaj?* — Jer jedino za redovne događaje ima smisla davati ocenu po kategorijama (stalni program mesta)
- *Šta je `eventOccurrenceCount`?* — Broj dosadašnjih termina tog redovnog događaja u trenutku pisanja utiska, čuva se direktno u bazi jer će se s vremenom menjati
- *Kako se broji?* — `eventService.countEventOccurrences(eventId)` — broji sve događaje istog naslova na istoj lokaciji pre trenutnog termina

---

### K7 — Sortiranje utisaka

**Backend (već postojao):**
- `GET /api/locations/{id}/reviews?sortBy=date&order=desc` — sortiranje po datumu opadajuće
- `GET /api/locations/{id}/reviews?sortBy=date&order=asc` — sortiranje po datumu rastuće
- `GET /api/locations/{id}/reviews?sortBy=rating&order=desc` — sortiranje po prosečnoj oceni opadajuće
- `GET /api/locations/{id}/reviews?sortBy=rating&order=asc` — sortiranje po prosečnoj oceni rastuće

**Frontend (već postojalo):**
- 2 dropdowna na stranici lokacije: "Sortiraj po" (datumu/oceni) i "Redosled" (opadajuće/rastuće)
- Svaka promena okida `loadReviews(sortBy, sortOrder)`

**Odbrambena pitanja:**
- *Kako se sortira po oceni u bazi?* — Sortiranje po oceni radi se u Javi (`stream().sorted(Comparator.comparing(...))`) jer je prosečna ocena izvedena vrednost (ne kolona u bazi)
- *Zašto se sortiranje po datumu radi u bazi a po oceni u Java kodu?* — Po datumu imamo JPA `ORDER BY` direktivu (efikasno), po oceni nema direktno polje u bazi pa se sortira u memoriji

---

### K8 — Početna stranica

**HomeController (`GET /api/home`) vraća:**
1. `todayEvents` — događaji koji se dešavaju danas (od 00:00 do 23:59), sortirani po vremenu, max 6
2. `topLocations` — top 4 lokacije sortirane po prosečnoj oceni, ako su iste ocene — po broju utisaka
3. `recentReviews` — najskorija 3 **vidljiva** utiska sa najpopularnije lokacije (ona na poziciji 0 u `topLocations`)

**Frontend (`home.component.ts` i `.html`):**
- Sekcija "Događaji danas" — kartice događaja sa vremenom, tipom i linkom na lokaciju
- Sekcija "Najbolje ocenjena mesta" — top 4 mesta sa prosečnom ocenom i brojem utisaka
- Sekcija "Najnoviji utisci" — 3 kartice utisaka sa imenom korisnika, ocenom, komentarom i datumom, sa linkom na lokaciju

**Odbrambena pitanja:**
- *Šta znači "najpopularnija mesta"?* — Mesta sa najvišom prosečnom ocenom (srednja vrednost svih kategorija svih utisaka na mestu)
- *Zašto `recentReviews` traži samo vidljive utiske?* — Obrisani i skriveni utisci ne smeju biti vidljivi korisnicima

---

### K9 — Promena lozinke

**Backend (već postojao):**
- `PUT /api/users/change-password` — prima `oldPassword` i `newPassword`, proverava staru, brisira novu sa BCrypt, šalje email notifikaciju

**Frontend (već postojao u `profile.ts`):**
- Forma sa 3 polja: trenutna lozinka, nova lozinka, potvrda
- Frontend validacija: sva polja obavezna, nova lozinka mora biti ista kao potvrda, min 6 karaktera
- Nakon uspešne promene: poruka o uspehu, forma se zatvara

**Odbrambena pitanja:**
- *Zašto ne šaljemo novu lozinku na backend direktno?* — Uvek hash-ujemo na backendu (BCrypt), nikad ne čuvamo plain text
- *Zašto se šalje email?* — Korisnik dobija potvrdu da je neko promenilo lozinku, ako to nije on — može reagovati

---

### K10 — Profil korisnika

**Backend:**
- `GET /api/users/profile` — vraća: `id`, `email`, `fullName`, `address`, `role`, `profileImage`, lista `reviews` (sa utiscima korisnika), `totalReviews`, a za MANAGER i `managedLocations`
- `PUT /api/users/profile` — ažurira `fullName`, `address`, `profileImage` (dodata podrška za sliku u ovoj oceni)

**Frontend (`profile.ts`, `profile.html`, `profile.css`):**
- Prikaz avatara (ako postoji `profileImage` URL, prikazuje sliku; inače ikona)
- Dugmad: "Izmeni profil", "Promeni lozinku", "Odjavi se"
- **Modal za izmenu profila**: fullName (obavezno), address (opciono), profileImage URL (opciono)
- **Lista mesta** (vidljiva samo za MANAGER): kartice sa slikom, nazivom, tipom i adresom, link na stranicu mesta
- **Lista utisaka** korisnika: prikazuje lokaciju, prosečnu ocenu, kategorije ocene, komentar i datum

**Odbrambena pitanja:**
- *Zašto profil slika nije upload fajla?* — Specifikacija kaže "promena slike", nije navedeno file upload; koristimo URL (konzistentno sa lokacijama i događajima)
- *Otkud podaci na profilu?* — Poziva se `GET /api/users/profile` sa JWT tokenom, backend izvlači email iz tokena i vraća podatke za tog korisnika
- *Zašto managed locations vide samo menadžeri?* — Na frontendu se proverava `profile.role === 'MANAGER'`; na backendu se filtriraju lokacije po `manager.id === user.id`

---

## Testirane funkcionalnosti (browser):

1. **K5**: Idi na stranicu lokacije → "Ostavite utisak" → vidite dropdown sa redovnim prošlim događajima → izaberete → popunite ocene → submit. Ako izaberete neregularan ili budući događaj, backend vraća grešku.
2. **K7**: Na stranici lokacije izaberite sortiranje po oceni opadajuće/rastuće i po datumu.
3. **K8**: Početna stranica prikazuje današnje događaje, top lokacije i najnovije utiske.
4. **K9**: Profil → "Promeni lozinku" → unesite staru i novu → potvrda.
5. **K10**: Profil → "Izmeni profil" → izmenite podatke. Menadžer vidi svoja mesta. Svaki korisnik vidi svoje utiske.
