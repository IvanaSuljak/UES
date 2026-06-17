# Ocena 9 — M1, M2, M3, M4

## M1 — Ažuriranje atributa mesta i rukovanje događajima

**Već implementirano (od Ocene 7/8):**
- Manager može menjati `address`, `type`, `description` svoje lokacije (`PUT /api/locations/{id}`) — **ne može menjati naziv ni sliku** (samo admin)
- Manager može kreirati, izmeniti i obrisati događaje na svojoj lokaciji (`POST/PUT/DELETE /api/events`)
- Validacija: manager može raditi samo na svojoj lokaciji; backend vraća 403 za svaki pokušaj pristupa tuđoj lokaciji
- Sve akcije su dostupne kroz **Manager Dashboard** (`/manager`)

**Odbrambeno pitanje:**
- *Zašto manager ne može menjati naziv lokacije?* — Specifikacija kaže "ažurirati atribute mesta poput adrese, tipa i opisa" — naziv i slika su ekskluzivno admin prerogativ

---

## M2 — Rukovanje utiscima

**Backend (`ReviewController.java`):**
- `GET /api/reviews/my-reviews` — manager vidi sve utiske za svoju lokaciju (i sakrivene, samo ne obrisane)
- `PUT /api/reviews/{id}/hide` — sakrij utisak: **ocena se I DALJE RAČUNA** u proseku, ali utisak nije vidljiv korisnicima
- `PUT /api/reviews/{id}/unhide` — otkrij sakriveni utisak
- `DELETE /api/reviews/{id}` — logičko brisanje: `isDeleted = true`, ocena se **NE RAČUNA** u prosek

**Razlika sakriveno vs obrisano:**
- `isHidden = true`: utisak nije vidljiv na stranici lokacije, ali ocena ulazi u prosek
- `isDeleted = true`: utisak je logički obrisan, ocena se ignoriše u svim kalkulacijama
- Ova razlika je implementirana u `getAverageRatingForLocation()` query koji filtrira samo `isDeleted = false`

**Frontend (`manager-dashboard.html/ts`):**
- Sekcija "Recenzije na mojim događajima" prikazuje sve utiske
- Sakriveni utisci imaju `badge-hidden` oznaku i tamniju pozadinu
- Dugmad: "Sakrij" / "Prikaži" (toggle) i "Obriši"

---

## M3 — Komentari i reply na utiske (nested)

**Model (`Comment.java`):**
- `text` — tekst komentara
- `review` — utisak na koji se odnosi
- `user` — ko je napisao
- `parentComment` — null za prvi nivo (manager odgovor na utisak), ili referenca na parent za nested reply

**Backend (`CommentController.java`):**
- `POST /api/comments/review/{reviewId}` — **samo manager** te lokacije može postawiti prvi komentar (odgovor na utisak)
- `POST /api/comments/{commentId}/reply` — **bilo koji ulogovani korisnik** može odgovoriti na bilo koji komentar
- `GET /api/comments/review/{reviewId}` — svi top-level komentari za utisak
- `GET /api/comments/{commentId}/replies` — odgovori na konkretan komentar
- `DELETE /api/comments/{id}` — autor ili manager može obrisati komentar

**Pravilo pristupa:**
- Menadžer mora biti manager te konkretne lokacije da bi odgovorio na utisak
- Nakon što manager odgovori, korisnik može odgovoriti na menadžerov komentar
- Proizvoljna dubina: reply na reply na reply...

**Frontend (`location-details.component.ts/html`):**
- Ispod svakog utiska: dugme "Prikaži komentare" i dugme "Odgovori"
- "Prikaži komentare" — toggle koji učitava komentare sa `GET /api/comments/review/{id}`
- "Odgovori" bez odabranog komentara — odgovara na utisak (manager only)
- "Odgovori" pored komentara — odgovara na komentar (svi ulogovani)
- Forma za reply se prikazuje inline, ispod odgovarajućeg utiska

**Manager dashboard:**
- "Odgovori" dugme sada pravilno poziva `POST /api/comments/review/{reviewId}` (prethodno je pozivalo lažni endpoint)

---

## M4 — Analitika mesta

**Backend (`AnalyticsController.java`):**

Endpoint: `GET /api/analytics/location/{locationId}?startDate=2025-01-01&endDate=2025-12-31`

Vraća:
```json
{
  "locationName": "Exit Festival",
  "period": { "start": "2025-01-01", "end": "2025-12-31" },
  "totalEvents": 12,
  "regularEvents": 8,
  "irregularEvents": 4,
  "freeEvents": 3,
  "paidEvents": 9,
  "topEvents": [{ "title": "Techno noć", "occurrences": 4 }],
  "eventsPerMonth": { "2025-01": 2, "2025-02": 1 },
  "totalReviews": 7,
  "avgRating": 8.2,
  "avgPerformance": 8.5,
  "avgSound": 9.0,
  "avgSpace": 7.8,
  "avgOverall": 8.1,
  "recentReviews": [...],
  "topLocations": [...]
}
```

**Dostupni periodi:**
- Nedeljno (poslednih 7 dana)
- Mesečno (poslednih 30 dana)
- Godišnje (poslednih 365 dana)
- Prilagođeno (korisnik bira start/end datum)

**Frontend (`analytics.component.ts/html/css`):**

Dostupno na ruti `/analytics` (samo za MANAGER — u navbaru pod dropdown-om).

Prikazuje:
1. **KPI kartice** — 7 brojeva: ukupno, redovni, neredovni, besplatni, plaćeni događaji, broj utisaka, prosečna ocena
2. **Pie chart** — redovni vs neredovni (Chart.js)
3. **Pie chart** — besplatni vs plaćeni (Chart.js)
4. **Bar chart** — broj događaja po mesecu (Chart.js)
5. **Radar chart** — prosečne ocene po kategorijama: nastup, zvuk/svetlo, prostor, ukupno (Chart.js)
6. **Top 5 događaja** — sortiranih po učestalosti (tabela)
7. **Top 5 lokacija** — sortiranih po prosečnoj oceni (tabela, trenutna lokacija je označena)
8. **Najskorija 3 utiska** u izabranom periodu

**Zašto Chart.js?** — KVA specifikacija zahteva grafičku biblioteku; Chart.js je standardna, laka za integraciju, podržava sve potrebne tipove grafika.

---

## Koraci za browser testiranje

### M2 — Sakrivanje/brisanje utisaka
1. Loguji se kao menadžer (npr. Luka Lukic)
2. Idi na `/manager`
3. U sekciji "Recenzije" vidiš sve utiske
4. Klikni **"Sakrij"** na nekom utisku — utisak dobija oznaku "Sakriveno"
5. Idi na stranicu lokacije — sakriveni utisak nije vidljiv
6. Vrati se u dashboard, klikni **"Prikaži"** — utisak se vraća
7. Klikni **"Obriši"** — utisak nestaje; ocena lokacije se ponovo računa bez tog utiska

### M3 — Komentari
1. Loguji se kao menadžer
2. Idi na stranicu lokacije (npr. Exit Festival)
3. Na nekom utisku klikni **"Odgovori"** → upiši tekst → Pošalji → videti komentar
4. Odjavi se, loguji kao korisnik
5. Na istom utisku klikni "Prikaži komentare" → vidiš menadžerov odgovor
6. Klikni **"Odgovori"** pored menadžerovog komentara → upiši tekst → Pošalji
7. Provjeri da se reply pojavljuje

### M4 — Analitika
1. Loguji se kao menadžer
2. Klikni dropdown → **"Analitika"** (`/analytics`)
3. Izaberi lokaciju, klikni "Mesečno", klikni **"Prikaži analitiku"**
4. Vidiš: KPI kartice, 4 grafika (pie, pie, bar, radar), top tabele, nedavne utiske
5. Promeni period na "Godišnje" i ponovo prikaži — videti drugačije podatke
6. Testiraj "Prilagođeno" — unesi konkretne datume
