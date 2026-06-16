import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';

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

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, HttpClientModule, FormsModule],
  templateUrl: './admin-dashboard.html',
  styleUrls: ['./admin-dashboard.css']
})
export class AdminDashboardComponent implements OnInit {
  activeTab: 'requests' | 'locations' | 'managers' = 'requests';

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

  // Dodela menadžera
  selectedLocation: Location | null = null;
  selectedManagerId: number | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService
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
    this.showLocationForm = true;
  }

  closeLocationForm() {
    this.showLocationForm = false;
    this.editingLocation = null;
  }

  saveLocation() {
    const url = this.editingLocation
      ? `${environment.apiUrl}/locations/${this.editingLocation.id}`
      : `${environment.apiUrl}/locations`;

    const method = this.editingLocation ? 'put' : 'post';

    this.http.request(method, url, {
      body: this.locationForm,
      headers: this.authService.getAuthHeaders()
    }).subscribe({
        next: () => {
          alert(this.editingLocation ? 'Lokacija ažurirana ✅' : 'Lokacija dodata ✅');
          this.closeLocationForm();
          this.loadLocations();
        },
        error: (err) => alert(err.error?.error || 'Greška pri čuvanju lokacije.')
      });
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
    this.http.get<User[]>(`${environment.apiUrl}/users`)
      .subscribe({
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
}
