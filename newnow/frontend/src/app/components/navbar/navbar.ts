import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.html',
  styleUrls: ['./navbar.css']
})
export class NavbarComponent implements OnInit {
  isAuthenticated = false;
  userName = '';
  userRole: 'USER' | 'MANAGER' | 'ADMIN' = 'USER';
  isMobileMenuOpen = false;
  isDropdownOpen = false;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit() {
    // Pretplata na promene prijave
    this.authService.isAuthenticated$.subscribe((status) => {
      this.isAuthenticated = status;

      if (status) {
        this.userName = this.authService.getUserName();
        const role = this.authService.getUserRole();
        this.userRole = role as 'USER' | 'MANAGER' | 'ADMIN';

        // ✅ DEBUG - proveri koji je role
        console.log('👤 Navbar - Korisnik:', this.userName);
        console.log('🎭 Navbar - Rola:', this.userRole);
      } else {
        this.userName = '';
        this.userRole = 'USER';
      }
    });
  }

  // Toggle dropdown meni
  toggleDropdown() {
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  // Zatvori dropdown
  closeDropdown() {
    this.isDropdownOpen = false;
  }

  // Zatvori dropdown kada klikneš bilo gde na stranici
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInsideDropdown = target.closest('.dropdown');

    if (!clickedInsideDropdown && this.isDropdownOpen) {
      this.isDropdownOpen = false;
    }
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  logout() {
    this.authService.logout();
    this.isDropdownOpen = false;
    this.router.navigate(['/login']);
  }
}
