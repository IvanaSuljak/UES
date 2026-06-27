import { Component, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { Chart, registerables } from 'chart.js';
import { environment } from '../../../environments/environment';

Chart.register(...registerables);

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.css']
})
export class AnalyticsComponent implements OnInit, OnDestroy {

  @ViewChild('pieEventsCanvas') pieEventsCanvas!: ElementRef;
  @ViewChild('piePayCanvas')    piePayCanvas!: ElementRef;
  @ViewChild('barMonthCanvas')  barMonthCanvas!: ElementRef;
  @ViewChild('radarCanvas')     radarCanvas!: ElementRef;

  analytics: any = null;
  loading = false;
  error = '';

  // Lokacije kojima je menadžer
  managedLocations: any[] = [];
  selectedLocationId: number | null = null;

  // Period
  preset: 'week' | 'month' | 'year' | 'custom' = 'month';
  startDate = '';
  endDate   = '';

  private charts: Chart[] = [];

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit(): void {
    this.loadManagedLocations();
    this.applyPreset('month');
  }

  ngOnDestroy(): void {
    this.charts.forEach(c => c.destroy());
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders().set('Authorization', `Bearer ${token}`);
  }

  loadManagedLocations(): void {
    this.http.get<any>(`${environment.apiUrl}/users/profile`, { headers: this.getHeaders() })
      .subscribe({
        next: (profile) => {
          this.managedLocations = profile.managedLocations || [];
          if (this.managedLocations.length > 0) {
            this.selectedLocationId = this.managedLocations[0].id;
          }
        },
        error: () => this.router.navigate(['/login'])
      });
  }

  applyPreset(preset: 'week' | 'month' | 'year' | 'custom'): void {
    this.preset = preset;
    const today = new Date();
    const fmt = (d: Date) => d.toISOString().substring(0, 10);

    if (preset === 'week') {
      const start = new Date(today); start.setDate(today.getDate() - 7);
      this.startDate = fmt(start); this.endDate = fmt(today);
    } else if (preset === 'month') {
      const start = new Date(today); start.setMonth(today.getMonth() - 1);
      this.startDate = fmt(start); this.endDate = fmt(today);
    } else if (preset === 'year') {
      const start = new Date(today); start.setFullYear(today.getFullYear() - 1);
      this.startDate = fmt(start); this.endDate = fmt(today);
    }
    // custom: korisnik sam unosi datume
  }

  loadAnalytics(): void {
    if (!this.selectedLocationId || !this.startDate || !this.endDate) {
      this.error = 'Izaberite lokaciju i period.';
      return;
    }
    this.loading = true;
    this.error   = '';
    this.analytics = null;

    const url = `${environment.apiUrl}/analytics/location/${this.selectedLocationId}?startDate=${this.startDate}&endDate=${this.endDate}`;
    this.http.get(url, { headers: this.getHeaders() }).subscribe({
      next: (data: any) => {
        this.analytics = data;
        this.loading   = false;
        setTimeout(() => this.renderCharts(), 100);
      },
      error: (err) => {
        this.error   = err.error?.error || 'Greška pri učitavanju analitike.';
        this.loading = false;
      }
    });
  }

  private renderCharts(): void {
    this.charts.forEach(c => c.destroy());
    this.charts = [];
    if (!this.analytics) return;

    const a = this.analytics;
    const hasEvents = (a.totalEvents ?? 0) > 0;

    // 1. Pie — redovni vs neredovni
    if (this.pieEventsCanvas && hasEvents) {
      this.charts.push(new Chart(this.pieEventsCanvas.nativeElement, {
        type: 'pie',
        data: {
          labels: ['Redovni', 'Neredovni'],
          datasets: [{ data: [a.regularEvents, a.irregularEvents], backgroundColor: ['#6366f1', '#f59e0b'] }]
        },
        options: { plugins: { legend: { position: 'bottom' } } }
      }));
    }

    // 2. Pie — besplatni vs plaćeni
    if (this.piePayCanvas && hasEvents) {
      this.charts.push(new Chart(this.piePayCanvas.nativeElement, {
        type: 'pie',
        data: {
          labels: ['Besplatni', 'Plaćeni'],
          datasets: [{ data: [a.freeEvents, a.paidEvents], backgroundColor: ['#10b981', '#ef4444'] }]
        },
        options: { plugins: { legend: { position: 'bottom' } } }
      }));
    }

    // 3. Bar — događaji po mesecu
    if (this.barMonthCanvas && a.eventsPerMonth && Object.keys(a.eventsPerMonth).length > 0) {
      const labels = Object.keys(a.eventsPerMonth);
      const values = Object.values(a.eventsPerMonth) as number[];
      this.charts.push(new Chart(this.barMonthCanvas.nativeElement, {
        type: 'bar',
        data: {
          labels,
          datasets: [{ label: 'Broj događaja', data: values, backgroundColor: '#6366f1' }]
        },
        options: { scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } } }
      }));
    }

    // 4. Radar — prosečne ocene po kategorijama
    if (this.radarCanvas && (a.avgPerformance || a.avgSound || a.avgSpace || a.avgOverall)) {
      this.charts.push(new Chart(this.radarCanvas.nativeElement, {
        type: 'radar',
        data: {
          labels: ['Nastup', 'Zvuk/Svetlo', 'Prostor', 'Ukupno'],
          datasets: [{
            label: 'Prosečna ocena',
            data: [a.avgPerformance ?? 0, a.avgSound ?? 0, a.avgSpace ?? 0, a.avgOverall ?? 0],
            backgroundColor: 'rgba(99,102,241,0.2)',
            borderColor: '#6366f1',
            pointBackgroundColor: '#6366f1'
          }]
        },
        options: { scales: { r: { min: 0, max: 10, ticks: { stepSize: 2 } } } }
      }));
    }
  }
}
