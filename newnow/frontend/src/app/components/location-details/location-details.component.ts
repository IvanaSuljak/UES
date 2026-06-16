import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-location-details',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './location-details.component.html',
  styleUrls: ['./location-details.component.css']
})
export class LocationDetailsComponent implements OnInit {
  locationId: number = 0;
  location: any = null;
  upcomingEvents: any[] = [];
  reviews: any[] = [];

  // 🟢 DODAJ OVO - omogući korišćenje Math u template-u
  Math = Math;

  // ✅ IZMENJENO - Forma za review sa 4 kategorije ocena (sve opcione, 1-10)
  newReview = {
    performanceRating: 0,    // 🎤 Ocena nastupa (1-10)
    soundLightRating: 0,     // 🔊 Ocena zvuka i svetla (1-10)
    spaceRating: 0,          // 🏛️ Ocena prostora (1-10)
    overallRating: 0,        // 🌟 Ukupan utisak (1-10)
    comment: ''              // 💬 Komentar (obavezan)
  };

  // Sortiranje
  sortBy: string = 'date';
  sortOrder: string = 'desc';

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.locationId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadLocationDetails();
    this.loadReviews();
  }

  loadLocationDetails(): void {
    this.http.get(`http://localhost:8080/api/locations/${this.locationId}/details`)
      .subscribe({
        next: (data: any) => {
          this.location = data;
          this.upcomingEvents = data.upcomingEvents || [];
        },
        error: (err) => console.error('Greška pri učitavanju detalja:', err)
      });
  }

  loadReviews(sortBy?: string, order?: string): void {
    let url = `http://localhost:8080/api/locations/${this.locationId}/reviews`;

    if (sortBy && order) {
      url += `?sortBy=${sortBy}&order=${order}`;
    }

    this.http.get(url).subscribe({
      next: (data: any) => {
        this.reviews = data;
        console.log('📋 Učitani utisci:', this.reviews); // ✅ Debug
      },
      error: (err) => console.error('Greška pri učitavanju utisaka:', err)
    });
  }

  // ✅ IZMENJENO - submitReview sa 4 kategorije ocena
  submitReview(): void {
    const token = localStorage.getItem('token');

    if (!token) {
      alert('Morate biti ulogovani da biste ostavili utisak!');
      return;
    }

    if (!this.newReview.comment.trim()) {
      alert('Molimo unesite komentar!');
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    // ✅ Kreiraj payload - pošalji samo ocene > 0
    const payload: any = {
      comment: this.newReview.comment
    };

    if (this.newReview.performanceRating > 0) {
      payload.performanceRating = this.newReview.performanceRating;
    }
    if (this.newReview.soundLightRating > 0) {
      payload.soundLightRating = this.newReview.soundLightRating;
    }
    if (this.newReview.spaceRating > 0) {
      payload.spaceRating = this.newReview.spaceRating;
    }
    if (this.newReview.overallRating > 0) {
      payload.overallRating = this.newReview.overallRating;
    }

    console.log('📤 Šaljem payload:', payload); // ✅ Debug

    this.http.post(
      `http://localhost:8080/api/locations/${this.locationId}/reviews`,
      payload,
      { headers }
    ).subscribe({
      next: () => {
        alert('Utisak uspešno dodat! ✅');
        // ✅ Resetuj formu
        this.newReview = {
          performanceRating: 0,
          soundLightRating: 0,
          spaceRating: 0,
          overallRating: 0,
          comment: ''
        };
        this.loadLocationDetails();
        this.loadReviews(this.sortBy, this.sortOrder);
      },
      error: (err) => {
        console.error('Greška pri dodavanju utiska:', err);

        // ✅ Prikaži grešku iz backend-a ako postoji
        if (err.error && err.error.error) {
          alert('❌ ' + err.error.error);
        } else {
          alert('Greška pri dodavanju utiska!');
        }
      }
    });
  }

  onSortChange(): void {
    this.loadReviews(this.sortBy, this.sortOrder);
  }

  getRatingStars(rating: number): string {
    return '⭐'.repeat(Math.min(rating, 10)); // ✅ Max 10 zvezda
  }

  getFormattedDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('sr-RS', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
