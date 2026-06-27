import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { CommentThreadComponent } from '../comment-thread/comment-thread.component';
import { resolveMediaUrl } from '../../utils/media-url';

@Component({
  selector: 'app-location-details',
  standalone: true,
  imports: [CommonModule, FormsModule, CommentThreadComponent],
  templateUrl: './location-details.component.html',
  styleUrls: ['./location-details.component.css']
})
export class LocationDetailsComponent implements OnInit {
  locationId: number = 0;
  location: any = null;
  upcomingEvents: any[] = [];
  reviews: any[] = [];
  pastRegularEvents: any[] = [];
  commentsMap: { [reviewId: number]: any[] } = {};
  replyingToCommentId: number | null = null;
  replyingToReviewId: number | null = null;
  replyText = '';

  Math = Math;
  apiUrl = environment.apiUrl;

  newReview = {
    performanceRating: 0,
    soundLightRating: 0,
    spaceRating: 0,
    overallRating: 0,
    comment: '',
    eventId: null as number | null
  };

  sortBy: string = 'date';
  sortOrder: string = 'desc';

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.locationId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadLocationDetails();
    this.loadReviews();
    this.loadPastRegularEvents();
  }

  loadLocationDetails(): void {
    this.http.get(`${this.apiUrl}/locations/${this.locationId}/details`)
      .subscribe({
        next: (data: any) => {
          this.location = data;
          this.upcomingEvents = data.upcomingEvents || [];
        },
        error: (err) => console.error('Greška pri učitavanju detalja:', err)
      });
  }

  loadPastRegularEvents(): void {
    this.http.get<any[]>(`${this.apiUrl}/events/byLocation/${this.locationId}`)
      .subscribe({
        next: (events) => {
          const now = new Date();
          this.pastRegularEvents = events.filter(e =>
            e.isRegular === true && new Date(e.dateTime) < now
          );
        },
        error: (err) => console.error('Greška pri učitavanju događaja:', err)
      });
  }

  loadReviews(sortBy?: string, order?: string): void {
    let url = `${this.apiUrl}/locations/${this.locationId}/reviews`;
    if (sortBy && order) {
      url += `?sortBy=${sortBy}&order=${order}`;
    }

    this.http.get(url).subscribe({
      next: (data: any) => { this.reviews = data; },
      error: (err) => console.error('Greška pri učitavanju utisaka:', err)
    });
  }

  submitReview(): void {
    const token = localStorage.getItem('token');
    if (!token) {
      alert('Morate biti ulogovani da biste ostavili utisak!');
      return;
    }

    if (!this.newReview.eventId) {
      alert('Morate izabrati događaj za koji ostavljate utisak!');
      return;
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const payload: any = { eventId: this.newReview.eventId };
    const comment = this.newReview.comment.trim();
    if (comment) payload.comment = comment;

    if (this.newReview.performanceRating > 0) payload.performanceRating = this.newReview.performanceRating;
    if (this.newReview.soundLightRating > 0) payload.soundLightRating = this.newReview.soundLightRating;
    if (this.newReview.spaceRating > 0) payload.spaceRating = this.newReview.spaceRating;
    if (this.newReview.overallRating > 0) payload.overallRating = this.newReview.overallRating;

    this.http.post(`${this.apiUrl}/locations/${this.locationId}/reviews`, payload, { headers })
      .subscribe({
        next: () => {
          alert('Utisak uspešno dodat! ✅');
          this.newReview = {
            performanceRating: 0,
            soundLightRating: 0,
            spaceRating: 0,
            overallRating: 0,
            comment: '',
            eventId: null
          };
          this.loadLocationDetails();
          this.loadReviews(this.sortBy, this.sortOrder);
        },
        error: (err) => {
          if (err.error?.error) {
            alert('❌ ' + err.error.error);
          } else {
            alert('Greška pri dodavanju utiska!');
          }
        }
      });
  }

  onSortChange(): void {
    this.loadReviews(this.sortBy, this.sortOrder);
  }

  loadComments(reviewId: number): void {
    this.http.get<any[]>(`${this.apiUrl}/comments/review/${reviewId}`)
      .subscribe({
        next: (comments) => { this.commentsMap[reviewId] = comments; },
        error: (err) => console.error('Greška komentari:', err)
      });
  }

  toggleComments(reviewId: number): void {
    if (this.commentsMap[reviewId] !== undefined) {
      delete this.commentsMap[reviewId];
    } else {
      this.loadComments(reviewId);
    }
  }

  openReply(reviewId: number, commentId: number | null): void {
    this.replyingToReviewId = reviewId;
    this.replyingToCommentId = commentId;
    this.replyText = '';
  }

  cancelReply(): void {
    this.replyingToReviewId = null;
    this.replyingToCommentId = null;
    this.replyText = '';
  }

  submitReply(): void {
    if (!this.replyText.trim()) return;
    const token = localStorage.getItem('token');
    if (!token) { alert('Morate biti ulogovani!'); return; }
    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });

    const url = this.replyingToCommentId
      ? `${this.apiUrl}/comments/${this.replyingToCommentId}/reply`
      : `${this.apiUrl}/comments/review/${this.replyingToReviewId}`;

    this.http.post(url, { text: this.replyText }, { headers }).subscribe({
      next: () => {
        this.loadComments(this.replyingToReviewId!);
        this.cancelReply();
      },
      error: (err) => alert(err.error?.error || 'Greška pri slanju odgovora')
    });
  }

  getRatingStars(rating: number): string {
    return '⭐'.repeat(Math.min(rating, 10));
  }

  getFormattedDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('sr-RS', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getImageUrl(url: string): string {
    return resolveMediaUrl(url, 'assets/placeholder.jpg');
  }

  downloadPdf(): void {
    window.open(`${this.apiUrl}/search/locations/${this.locationId}/pdf`, '_blank');
  }

  isFreeEvent(price: number | null | undefined): boolean {
    return price == null || price === 0;
  }
}
