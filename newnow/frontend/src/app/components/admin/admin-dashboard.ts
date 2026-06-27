import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';
import { resolveMediaUrl } from '../../utils/media-url';

interface AccountRequest {
  id: number;
  email: string;
  fullName: string;
  status: string;
}

interface Location {
  id: number;
  name: string;
  address: string;
  type: string;
  description: string;
  imageUrl: string;
  manager?: any;
}

interface User {
  id: number;
  email: string;
  fullName: string;
  role: string;
}

interface AdminEvent {
  id: number;
  title: string;
  description: string;
  dateTime: string;
  price: number;
  type: string;
  imageUrl: string;
  isRegular: boolean;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, HttpClientModule, FormsModule],
  templateUrl: './admin-dashboard.html',
  styleUrls: ['./admin-dashboard.css']
})
export class AdminDashboardComponent implements OnInit {
  activeTab: 'requests' | 'locations' | 'events' | 'managers' = 'requests';

  // Zahtevi za registraciju
  accountRequests: AccountRequest[] = [];
  pendingCount = 0;

  // Lokacije
  locations: Location[] = [];
  users: User[] = [];

  // Forma za lokaciju
  showLocationForm = false;
  editingLocation: Location | null = null;
  locationForm = {
    name: '',
    address: '',
    type: '',
    description: '',
    imageUrl: ''
  };
  customLocationType = '';
  selectedPdfFile: File | null = null;
  selectedImageFile: File | null = null;

  // Dodela menadžera
  selectedLocation: Location | null = null;
  selectedManagerId: number | null = null;

  // K4 — događaji (admin može na bilo kom mestu)
  selectedEventsLocationId: number | null = null;
  adminEvents: AdminEvent[] = [];
  showEventForm = false;
  editingEvent: AdminEvent | null = null;
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
  selectedEventImageFile: File | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadAccountRequests();
    this.loadLocations();
    this.loadUsers();
  }

  // === ZAHTEVI ZA REGISTRACIJU ===

  loadAccountRequests() {
    this.http.get<AccountRequest[]>(
      `${environment.apiUrl}/account-requests/pending`,
      { headers: this.authService.getAuthHeaders() }
    ).subscribe({
        next: (data) => {
          this.accountRequests = data;
          this.pendingCount = data.length;
        },
        error: (err) => console.error('Greška pri učitavanju zahteva:', err)
      });
  }

  approveRequest(id: number) {
    this.http.post(
      `${environment.apiUrl}/account-requests/${id}/approve`,
      {},
      { headers: this.authService.getAuthHeaders() }
    ).subscribe({
        next: () => {
          alert('Zahtev odobren ✅');
          this.loadAccountRequests();
        },
        error: (err) => alert(err.error?.error || 'Greška pri odobravanju.')
      });
  }

  rejectRequest(id: number) {
    this.http.post(
      `${environment.apiUrl}/account-requests/${id}/reject`,
      {},
      { headers: this.authService.getAuthHeaders() }
    ).subscribe({
        next: () => {
          alert('Zahtev odbijen ❌');
          this.loadAccountRequests();
        },
        error: (err) => alert(err.error?.error || 'Greška pri odbijanju.')
      });
  }

  // === LOKACIJE ===

  loadLocations() {
    this.http.get<Location[]>(`${environment.apiUrl}/locations`)
      .subscribe({
        next: (data) => {
          this.locations = data;
          if (this.selectedEventsLocationId == null && data.length > 0) {
            this.selectedEventsLocationId = data[0].id;
            this.loadAdminEvents();
          }
        },
        error: (err) => console.error('Greška pri učitavanju lokacija:', err)
      });
  }

  openLocationForm(location?: Location) {
    if (location) {
      this.editingLocation = location;
      this.locationForm = {
        name: location.name,
        address: location.address,
        type: location.type,
        description: location.description,
        imageUrl: location.imageUrl
      };
    } else {
      this.editingLocation = null;
      this.locationForm = {
        name: '',
        address: '',
        type: '',
        description: '',
        imageUrl: ''
      };
    }
    this.customLocationType = '';
    this.showLocationForm = true;
  }

  closeLocationForm() {
    this.showLocationForm = false;
    this.editingLocation = null;
    this.customLocationType = '';
    this.selectedImageFile = null;
    this.selectedPdfFile = null;
  }

  saveLocation() {
    const finalType = this.locationForm.type === 'Other'
      ? this.customLocationType.trim()
      : this.locationForm.type;

    if (!finalType) {
      alert('Izaberite ili unesite tip mesta.');
      return;
    }

    const saveWithPayload = (imageUrl: string) => {
      const url = this.editingLocation
        ? `${environment.apiUrl}/locations/${this.editingLocation.id}`
        : `${environment.apiUrl}/locations`;
      const method = this.editingLocation ? 'put' : 'post';

      this.http.request(method, url, {
        body: { ...this.locationForm, type: finalType, imageUrl },
        headers: this.authService.getAuthHeaders()
      }).subscribe({
        next: (saved: any) => {
          const locationId = this.editingLocation?.id || saved?.id;
          if (this.selectedPdfFile && locationId) {
            this.uploadPdf(locationId);
          } else {
            alert(this.editingLocation ? 'Lokacija ažurirana ✅' : 'Lokacija dodata ✅');
            this.closeLocationForm();
            this.loadLocations();
          }
        },
        error: (err) => this.handleSaveError(err, 'Greška pri čuvanju lokacije.')
      });
    };

    if (this.selectedImageFile) {
      const formData = new FormData();
      formData.append('file', this.selectedImageFile);
      this.http.post(`${environment.apiUrl}/files/images`, formData, {
        headers: this.authService.getAuthHeaders()
      }).subscribe({
        next: (res: any) => saveWithPayload(res.objectName),
        error: (err) => alert(err.error?.error || 'Greška pri uploadu slike.')
      });
    } else if (!this.locationForm.imageUrl.trim()) {
      alert('Unesite URL slike ili izaberite fajl za upload.');
      return;
    } else {
      saveWithPayload(this.locationForm.imageUrl);
    }
  }

  onImageFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.selectedImageFile = input.files?.[0] || null;
  }

  onPdfFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.selectedPdfFile = input.files?.[0] || null;
  }

  uploadPdf(locationId: number) {
    if (!this.selectedPdfFile) return;
    const formData = new FormData();
    formData.append('file', this.selectedPdfFile);
    this.http.post(`${environment.apiUrl}/search/locations/${locationId}/pdf`, formData, {
      headers: this.authService.getAuthHeaders()
    }).subscribe({
      next: () => {
        alert(this.editingLocation ? 'Lokacija ažurirana ✅' : 'Lokacija dodata ✅');
        this.selectedPdfFile = null;
        this.closeLocationForm();
        this.loadLocations();
      },
      error: (err) => this.handleSaveError(err, 'Lokacija sačuvana, ali PDF upload nije uspeo.')
    });
  }

  private handleSaveError(err: any, fallback: string): void {
    const message = err.error?.error || fallback;
    if (err.status === 401 || message.toLowerCase().includes('sesija')) {
      alert('Sesija je istekla. Prijavite se ponovo pa pokušajte.');
      this.authService.logout();
      this.router.navigate(['/login']);
      return;
    }
    alert(message);
  }

  getImageUrl(url: string): string {
    return resolveMediaUrl(url);
  }

  getSelectedEventsLocationName(): string {
    const loc = this.locations.find(l => l.id === this.selectedEventsLocationId);
    return loc?.name || '';
  }

  downloadPdf(locationId: number) {
    window.open(`${environment.apiUrl}/search/locations/${locationId}/pdf`, '_blank');
  }

  deleteLocation(id: number) {
    if (confirm('Da li ste sigurni da želite da obrišete ovu lokaciju?')) {
      this.http.delete(`${environment.apiUrl}/locations/${id}`, {
        headers: this.authService.getAuthHeaders()
      }).subscribe({
          next: () => {
            alert('Lokacija obrisana ✅');
            this.loadLocations();
          },
          error: (err) => alert(err.error?.error || 'Greška pri brisanju.')
        });
    }
  }

  // === MENADŽERI ===

  loadUsers() {
    this.http.get<User[]>(`${environment.apiUrl}/users`, {
      headers: this.authService.getAuthHeaders()
    }).subscribe({
        next: (data) => {
          this.users = data.filter(u => u.role === 'USER' || u.role === 'MANAGER');
        },
        error: (err) => console.error('Greška:', err)
      });
  }

  openManagerAssignment(location: Location) {
    this.selectedLocation = location;
    this.selectedManagerId = location.manager?.id || null;
  }

  assignManager() {
    if (!this.selectedLocation || !this.selectedManagerId) return;

    this.http.put(
      `${environment.apiUrl}/locations/${this.selectedLocation.id}/assign-manager`,
      { managerId: this.selectedManagerId },
      { headers: this.authService.getAuthHeaders() }
    ).subscribe({
      next: () => {
        alert('Menadžer dodeljen ✅');
        this.selectedLocation = null;
        this.selectedManagerId = null;
        this.loadLocations();
        this.loadUsers();
      },
      error: (err) => alert(err.error?.error || 'Greška pri dodeli menadžera.')
    });
  }

  removeManager(locationId: number) {
    if (confirm('Da li ste sigurni da želite da uklonite menadžera?')) {
      this.http.delete(`${environment.apiUrl}/locations/${locationId}/remove-manager`, {
        headers: this.authService.getAuthHeaders()
      }).subscribe({
          next: () => {
            alert('Menadžer uklonjen ✅');
            this.loadLocations();
            this.loadUsers();
          },
          error: (err) => alert(err.error?.error || 'Greška pri uklanjanju menadžera.')
        });
    }
  }

  // === K4 — DOGAĐAJI (ADMIN) ===

  onEventsLocationChange(): void {
    this.loadAdminEvents();
  }

  loadAdminEvents(): void {
    if (!this.selectedEventsLocationId) {
      this.adminEvents = [];
      return;
    }
    this.http.get<AdminEvent[]>(
      `${environment.apiUrl}/events/byLocation/${this.selectedEventsLocationId}`
    ).subscribe({
      next: (data) => { this.adminEvents = data || []; },
      error: (err) => console.error('Greška pri učitavanju događaja:', err)
    });
  }

  openEventForm(event?: AdminEvent): void {
    if (event) {
      this.editingEvent = event;
      this.eventForm = {
        title: event.title,
        description: event.description,
        dateTime: event.dateTime?.substring(0, 16) || event.dateTime,
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
    this.selectedEventImageFile = null;
    this.showEventForm = true;
  }

  closeEventForm(): void {
    this.showEventForm = false;
    this.editingEvent = null;
    this.customEventType = '';
    this.selectedEventImageFile = null;
  }

  onEventImageSelected(domEvent: globalThis.Event): void {
    const input = domEvent.target as HTMLInputElement;
    this.selectedEventImageFile = input.files?.[0] || null;
  }

  saveAdminEvent(): void {
    if (!this.selectedEventsLocationId) {
      alert('Izaberite lokaciju.');
      return;
    }

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

    const submitPayload = (imageUrl: string) => {
      const payload: any = {
        title: this.eventForm.title,
        description: this.eventForm.description,
        dateTime: this.eventForm.dateTime.length === 16
          ? this.eventForm.dateTime + ':00'
          : this.eventForm.dateTime,
        type: finalType,
        imageUrl,
        isRegular: this.eventForm.isRegular,
        locationId: this.selectedEventsLocationId,
        price: this.eventForm.price > 0 ? this.eventForm.price : null
      };

      this.http.request(method, url, {
        body: payload,
        headers: this.authService.getAuthHeaders()
      }).subscribe({
        next: () => {
          alert(this.editingEvent ? 'Događaj ažuriran ✅' : 'Događaj dodat ✅');
          this.closeEventForm();
          this.loadAdminEvents();
        },
        error: (err) => this.handleSaveError(err, 'Greška pri čuvanju događaja.')
      });
    };

    if (this.selectedEventImageFile) {
      const formData = new FormData();
      formData.append('file', this.selectedEventImageFile);
      this.http.post(`${environment.apiUrl}/files/images`, formData, {
        headers: this.authService.getAuthHeaders()
      }).subscribe({
        next: (res: any) => submitPayload(res.objectName),
        error: (err) => alert(err.error?.error || 'Greška pri uploadu slike u MinIO.')
      });
      return;
    }

    if (!this.eventForm.imageUrl?.trim()) {
      alert('Unesite URL slike ili izaberite fajl za upload.');
      return;
    }

    submitPayload(this.eventForm.imageUrl);
  }

  deleteAdminEvent(id: number): void {
    if (!confirm('Da li ste sigurni da želite da obrišete ovaj događaj?')) return;

    this.http.delete(`${environment.apiUrl}/events/${id}`, {
      headers: this.authService.getAuthHeaders()
    }).subscribe({
      next: () => {
        alert('Događaj obrisan ✅');
        this.loadAdminEvents();
      },
      error: (err) => alert(err.error?.error || 'Greška pri brisanju događaja.')
    });
  }
}
