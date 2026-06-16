import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './profile.html',
  styleUrls: ['./profile.css']
})
export class ProfileComponent implements OnInit {
  userName = '';
  userEmail = '';
  userRole = '';

  // 🟢 NOVO - K9
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
    this.userName = this.authService.getUserName();
    this.userEmail = localStorage.getItem('userEmail') || '';
    this.userRole = this.authService.getUserRole();
  }

  // 🟢 NOVO - K9: Prikaži/sakrij formu
  togglePasswordForm() {
    this.showPasswordForm = !this.showPasswordForm;
    this.resetPasswordForm();
  }

  // 🟢 NOVO - K9: Resetuj formu
  resetPasswordForm() {
    this.oldPassword = '';
    this.newPassword = '';
    this.confirmPassword = '';
    this.passwordError = '';
    this.passwordSuccess = '';
  }

  // 🟢 NOVO - K9: Promeni lozinku
  changePassword() {
    this.passwordError = '';
    this.passwordSuccess = '';

    // Validacija
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

    // HTTP zahtev
    const token = localStorage.getItem('token');
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);

    const payload = {
      oldPassword: this.oldPassword,
      newPassword: this.newPassword
    };

    this.http.put(`${environment.apiUrl}/users/change-password`, payload, { headers })
      .subscribe({
        next: (response: any) => {
          this.passwordSuccess = response.message;
          this.resetPasswordForm();
          setTimeout(() => {
            this.showPasswordForm = false;
          }, 2000);
        },
        error: (err) => {
          if (err.error && typeof err.error === 'string') {
            this.passwordError = err.error;
          } else {
            this.passwordError = 'Greška pri promeni lozinke!';
          }
        }
      });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
