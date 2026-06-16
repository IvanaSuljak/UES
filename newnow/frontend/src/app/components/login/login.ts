import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, RouterModule],
  templateUrl: './login.html',
  styleUrls: ['./login.css']
})
export class LoginComponent {
  email = '';
  password = '';
  message = '';
  isLoading = false;
  isSuccess = false;

  constructor(
    private http: HttpClient,
    private router: Router,
    private authService: AuthService
  ) {}

  loginUser() {
    if (!this.email || !this.password) {
      this.message = 'Molimo popunite sva polja!';
      this.isSuccess = false;
      return;
    }

    this.isLoading = true;
    this.message = '';

    const payload = { email: this.email, password: this.password };

    console.log('📤 Šaljem:', payload);

    this.http.post(`${environment.apiUrl}/auth/login`, payload).subscribe({
      next: (res: any) => {
        console.log('✅ Login uspešan:', res);

        localStorage.setItem('token', res.token);
        localStorage.setItem('userEmail', res.email);
        localStorage.setItem('userRole', res.role);
        localStorage.setItem('userName', res.fullName);

        this.authService.login(res.fullName, res.role);

        this.message = '✅ Uspešno ste prijavljeni!';
        this.isSuccess = true;
        this.isLoading = false;

        // ✅ ISPRAVLJENO - SVI korisnici idu na početnu stranicu
        setTimeout(() => {
          this.router.navigate(['/']);
        }, 1000);
      },
      error: (err: any) => {
        console.error('❌ Greška pri prijavi:', err);
        this.message = err.error?.error || '❌ Pogrešan email ili lozinka.';
        this.isSuccess = false;
        this.isLoading = false;
      }
    });
  }
}
