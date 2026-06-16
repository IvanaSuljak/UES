import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, RouterModule],
  templateUrl: './profile.html',
  styleUrls: ['./profile.css']
})
export class ProfileComponent implements OnInit {
  // K10 — podaci sa servera
  profile: any = null;
  loading = true;

  // K10 — edit forma
  showEditForm = false;
  editForm = { fullName: '', address: '', profileImage: '' };
  editError = '';
  editSuccess = '';

  // K9 — promena lozinke
  showPasswordForm = false;
  oldPassword = '';
  newPassword = '';
  confirmPassword = '';
  passwordError = '';
  passwordSuccess = '';

  constructor(
    private authService: AuthService,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.loadProfile();
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders().set('Authorization', `Bearer ${token}`);
  }

  loadProfile(): void {
    this.loading = true;
    this.http.get(`${environment.apiUrl}/users/profile`, { headers: this.getHeaders() })
      .subscribe({
        next: (data: any) => {
          this.profile = data;
          this.loading = false;
        },
        error: (err) => {
          console.error('Greška pri učitavanju profila:', err);
          this.loading = false;
        }
      });
  }

  // K10 — otvori edit formu
  openEditForm(): void {
    if (!this.profile) return;
    this.editForm = {
      fullName: this.profile.fullName || '',
      address: this.profile.address || '',
      profileImage: this.profile.profileImage || ''
    };
    this.editError = '';
    this.editSuccess = '';
    this.showEditForm = true;
  }

  closeEditForm(): void {
    this.showEditForm = false;
  }

  saveProfile(): void {
    this.editError = '';
    this.editSuccess = '';

    if (!this.editForm.fullName.trim()) {
      this.editError = 'Ime i prezime su obavezni!';
      return;
    }

    this.http.put(`${environment.apiUrl}/users/profile`, this.editForm, { headers: this.getHeaders() })
      .subscribe({
        next: (res: any) => {
          this.editSuccess = res.message || 'Profil ažuriran!';
          this.loadProfile();
          setTimeout(() => {
            this.showEditForm = false;
            this.editSuccess = '';
          }, 1500);
        },
        error: (err) => {
          this.editError = err.error?.error || 'Greška pri ažuriranju!';
        }
      });
  }

  // K9 — promena lozinke
  togglePasswordForm(): void {
    this.showPasswordForm = !this.showPasswordForm;
    this.resetPasswordForm();
  }

  resetPasswordForm(): void {
    this.oldPassword = '';
    this.newPassword = '';
    this.confirmPassword = '';
    this.passwordError = '';
    this.passwordSuccess = '';
  }

  changePassword(): void {
    this.passwordError = '';
    this.passwordSuccess = '';

    if (!this.oldPassword || !this.newPassword || !this.confirmPassword) {
      this.passwordError = 'Sva polja su obavezna!';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = 'Nova lozinka i potvrda se ne poklapaju!';
      return;
    }
    if (this.newPassword.length < 6) {
      this.passwordError = 'Nova lozinka mora imati najmanje 6 karaktera!';
      return;
    }

    this.http.put(
      `${environment.apiUrl}/users/change-password`,
      { oldPassword: this.oldPassword, newPassword: this.newPassword },
      { headers: this.getHeaders() }
    ).subscribe({
      next: (response: any) => {
        this.passwordSuccess = response.message;
        this.resetPasswordForm();
        setTimeout(() => { this.showPasswordForm = false; }, 2000);
      },
      error: (err) => {
        this.passwordError = err.error || 'Greška pri promeni lozinke!';
      }
    });
  }

  getFormattedDate(dateString: string): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString('sr-RS', {
      year: 'numeric', month: 'long', day: 'numeric'
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
