import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router'; // 🔄 da može da učitava stranice
import { NavbarComponent } from './components/navbar/navbar';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <!-- 🔝 Navigacija -->
    <app-navbar></app-navbar>

    <!-- 🔄 Dinamički sadržaj (menja se po ruti) -->
    <router-outlet></router-outlet>

    <!-- ⚓ Footer -->
    <footer class="footer">
      © 2025 Discover Barcelona | Cultura y arte en la ciudad.
    </footer>
  `,
  styleUrls: ['./app.css']
})
export class App {}
