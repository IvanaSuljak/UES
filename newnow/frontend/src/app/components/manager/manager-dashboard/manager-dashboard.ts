import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface Location {
  id: number;
  name: string;
  address: string;
  type: string;
  description: string;
  imageUrl: string;
}

interface Event {
  id: number;
  title: string;
  description: string;
  dateTime: string;
  price: number;
  type: string;
  imageUrl: string;
  isRegular: boolean;
}

interface Review {
  id: number;
  user: {
    id: number;
    fullName: string;
  };
  event: {
    id: number;
    title: string;
  };
  rating: number;
  comment: string;
  createdAt: string;
  managerReply?: string;
  hidden: boolean;
}

@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [CommonModule, HttpClientModule, FormsModule],
  templateUrl: './manager-dashboard.html',
  styleUrls: ['./manager-dashboard.css']
})
export class ManagerDashboardComponent implements OnInit {
  myLocation: Location | null = null;
  events: Event[] = [];
  reviews: Review[] = [];

  showEventForm = false;
  editingEvent: Event | null = null;

  showReplyForm = false;
  replyingToReview: Review | null = null;
  replyText = '';

  showLocationForm = false;
  locationForm = { address: '', type: '', description: '' };
  customLocationType = '';

  eventForm = {
    title: '',
    description: '',
    dateTime: '',
    price: 0,
    type: '',
    imageUrl: '',
    isRegular: false
  };
  customEventType = '';

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() {
    this.loadMyLocation();
    this.loadReviews();
  }

  loadMyLocation() {
    const token = localStorage.getItem('token');
    if (!token) {
      alert('Niste ulogovani!');
      this.router.navigate(['/login']);
      return;
    }

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    this.http.get<Location>(`${environment.apiUrl}/locations/my-location`, { headers })
      .subscribe({
        next: (data) => {
          this.myLocation = data;
          this.loadEvents();
        },
        error: (err) => {
          console.error('Greška:', err);
          if (err.status === 403) {
            alert('Nemate pristup! Samo menadžeri mogu videti ovu stranicu.');
            this.router.navigate(['/']);
          }
        }
      });
  }

  loadEvents() {
    if (!this.myLocation) return;

    this.http.get<Event[]>(`${environment.apiUrl}/events/byLocation/${this.myLocation.id}`)
      .subscribe({
        next: (data) => {
          console.log('✅ Učitani eventi:', data);
          this.events = data;
        },
        error: (err) => console.error('❌ Greška pri učitavanju evenata:', err)
      });
  }

  // ✅ UČITAVANJE RECENZIJA
  loadReviews() {
    const token = localStorage.getItem('token');
    if (!token) return;

    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    this.http.get<Review[]>(`${environment.apiUrl}/reviews/my-reviews`, { headers })
      .subscribe({
        next: (data) => {
          console.log('✅ Učitane recenzije:', data);
          this.reviews = data;
        },
        error: (err) => console.error('❌ Greška pri učitavanju recenzija:', err)
      });
  }

  // ==================== LOCATION MANAGEMENT (samo atributi) ====================

  openLocationEditForm() {
    if (!this.myLocation) return;
    this.locationForm = {
      address: this.myLocation.address,
      type: this.myLocation.type,
      description: this.myLocation.description
    };
    this.customLocationType = '';
    this.showLocationForm = true;
  }

  closeLocationEditForm() {
    this.showLocationForm = false;
    this.customLocationType = '';
  }

  saveLocationEdit() {
    if (!this.myLocation) return;
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    const finalType = this.locationForm.type === 'Other'
      ? this.customLocationType.trim()
      : this.locationForm.type;

    if (!finalType) {
      alert('Unesite tip mesta.');
      return;
    }

    const payload = { ...this.locationForm, type: finalType };

    this.http.put(
      `${environment.apiUrl}/locations/${this.myLocation.id}`,
      payload,
      { headers }
    ).subscribe({
      next: (updated: any) => {
        alert('Lokacija ažurirana ✅');
        this.myLocation!.address = updated.address;
        this.myLocation!.type = updated.type;
        this.myLocation!.description = updated.description;
        this.closeLocationEditForm();
      },
      error: (err) => alert(err.error?.error || 'Greška pri ažuriranju lokacije.')
    });
  }

  // ==================== EVENT MANAGEMENT ====================

  openEventForm(event?: Event) {
    if (event) {
      this.editingEvent = event;
      this.eventForm = {
        title: event.title,
        description: event.description,
        dateTime: event.dateTime,
        price: event.price,
        type: event.type,
        imageUrl: event.imageUrl,
        isRegular: event.isRegular
      };
    } else {
      this.editingEvent = null;
      this.eventForm = {
        title: '',
        description: '',
        dateTime: '',
        price: 0,
        type: '',
        imageUrl: '',
        isRegular: false
      };
    }
    this.customEventType = '';
    this.showEventForm = true;
  }

  closeEventForm() {
    this.showEventForm = false;
    this.editingEvent = null;
    this.customEventType = '';
  }

  saveEvent() {
    const finalType = this.eventForm.type === 'Other'
      ? this.customEventType.trim()
      : this.eventForm.type;

    if (!finalType) {
      alert('Izaberite ili unesite tip događaja.');
      return;
    }

    const url = this.editingEvent
      ? `${environment.apiUrl}/events/${this.editingEvent.id}`
      : `${environment.apiUrl}/events`;

    const method = this.editingEvent ? 'put' : 'post';

    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    const payload = {
      ...this.eventForm,
      type: finalType,
      locationId: this.myLocation?.id
    };

    this.http.request(method, url, { body: payload, headers })
      .subscribe({
        next: () => {
          alert(this.editingEvent ? 'Događaj ažuriran ✅' : 'Događaj dodat ✅');
          this.closeEventForm();
          this.loadEvents();
        },
        error: (err) => {
          console.error('Greška pri čuvanju:', err);
          alert('Greška pri čuvanju događaja: ' + (err.error?.error || err.error?.message || err.message));
        }
      });
  }

  deleteEvent(id: number) {
    if (confirm('Da li ste sigurni da želite da obrišete ovaj događaj?')) {
      const token = localStorage.getItem('token');
      const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

      this.http.delete(`${environment.apiUrl}/events/${id}`, { headers })
        .subscribe({
          next: () => {
            alert('Događaj obrisan ✅');
            this.loadEvents();
          },
          error: (err) => {
            console.error('Greška pri brisanju:', err);
            alert('Greška pri brisanju: ' + (err.error?.error || err.message));
          }
        });
    }
  }

  // ==================== REVIEW MANAGEMENT ====================

  openReplyForm(review: Review) {
    this.replyingToReview = review;
    this.replyText = review.managerReply || '';
    this.showReplyForm = true;
  }

  closeReplyForm() {
    this.showReplyForm = false;
    this.replyingToReview = null;
    this.replyText = '';
  }

  saveReply() {
    if (!this.replyingToReview || !this.replyText.trim()) {
      alert('Unesite odgovor!');
      return;
    }

    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    // M3 — manager odgovara na utisak (POST /api/comments/review/{reviewId})
    this.http.post(
      `${environment.apiUrl}/comments/review/${this.replyingToReview.id}`,
      { text: this.replyText },
      { headers }
    ).subscribe({
      next: () => {
        alert('Odgovor poslat ✅');
        this.closeReplyForm();
        this.loadReviews();
      },
      error: (err) => {
        console.error('Greška:', err);
        alert('Greška pri slanju odgovora: ' + (err.error?.error || err.message));
      }
    });
  }

  hideReview(id: number) {
    if (confirm('Sakrij recenziju? (i dalje se računa u rating)')) {
      const token = localStorage.getItem('token');
      const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

      this.http.put(`${environment.apiUrl}/reviews/${id}/hide`, {}, { headers })
        .subscribe({
          next: () => {
            alert('Recenzija sakrivena ✅');
            this.loadReviews();
          },
          error: (err) => {
            console.error('Greška:', err);
            alert('Greška: ' + (err.error?.message || err.message));
          }
        });
    }
  }

  unhideReview(id: number) {
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    this.http.put(`${environment.apiUrl}/reviews/${id}/unhide`, {}, { headers })
      .subscribe({
        next: () => {
          alert('Recenzija prikazana ✅');
          this.loadReviews();
        },
        error: (err) => {
          console.error('Greška:', err);
          alert('Greška: ' + (err.error?.message || err.message));
        }
      });
  }

  deleteReview(id: number) {
    if (confirm('Obriši recenziju? (NE računa se u rating)')) {
      const token = localStorage.getItem('token');
      const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

      this.http.delete(`${environment.apiUrl}/reviews/${id}`, { headers })
        .subscribe({
          next: () => {
            alert('Recenzija obrisana ✅');
            this.loadReviews();
          },
          error: (err) => {
            console.error('Greška:', err);
            alert('Greška: ' + (err.error?.message || err.message));
          }
        });
    }
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('email');
    this.router.navigate(['/login']);
  }
}
