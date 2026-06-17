import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component'; // 🟢 DODAJ
import { LocationsComponent } from './components/locations/locations';
import { EventsPageComponent } from './components/events-page/events-page.component';
import { LoginComponent } from './components/login/login';
import { RegisterComponent } from './components/register/register';
import { ProfileComponent } from './components/profile/profile';
import { AdminDashboardComponent } from './components/admin/admin-dashboard';
import { ManagerDashboardComponent } from './components/manager/manager-dashboard/manager-dashboard';
import { LocationDetailsComponent } from './components/location-details/location-details.component';
import { AnalyticsComponent } from './components/analytics/analytics.component';
import { SearchComponent } from './components/search/search.component';
import { adminGuard, authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },   // 🟢 PROMENJENO!
  { path: 'locations', component: LocationsComponent }, // 🟢 DODATO!
  { path: 'locations/:id', component: LocationDetailsComponent },
  { path: 'events', component: EventsPageComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminDashboardComponent, canActivate: [adminGuard] },
  { path: 'manager-dashboard', component: ManagerDashboardComponent, canActivate: [authGuard] },
  { path: 'manager', component: ManagerDashboardComponent, canActivate: [authGuard] },
  { path: 'analytics', component: AnalyticsComponent, canActivate: [authGuard] },
  { path: 'search', component: SearchComponent },
  { path: '**', redirectTo: '' }
];
