import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Router } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './register.html',
  styleUrls: ['./register.css']
})
export class RegisterComponent {
  fullName = '';
  email = '';
  password = '';
  message = '';

  constructor(private http: HttpClient, private router: Router) {}

  registerUser() {
    const payload = {
      fullName: this.fullName,
      email: this.email,
      password: this.password
    };

    // ✨ IZMENJENO: Šalje zahtev za registraciju umesto direktnog kreiranja korisnika
    this.http.post(`${environment.apiUrl}/account-requests`, payload).subscribe({
      next: (res: any) => {
        console.log('✅ Zahtev poslat:', res);
        this.message = 'Zahtev za registraciju uspešno poslat! Sačekajte da admin odobri vaš nalog. ✅';

        // ⏩ Posle 3 sekunde prebacujemo korisnika na login
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 3000);
      },
      error: (err) => {
        console.error('❌ Greška pri slanju zahteva:', err);
        this.message = err.error?.error || 'Došlo je do greške. Email možda već postoji.';
      }
    });
  }
}
