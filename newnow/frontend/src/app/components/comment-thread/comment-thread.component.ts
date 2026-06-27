import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-comment-thread',
  standalone: true,
  imports: [CommonModule, CommentThreadComponent],
  template: `
    <div class="comment-item" [class.comment-item-reply]="depth > 0">
      <div class="comment-header">
        <strong>{{ comment.user?.fullName }}</strong>
        <span class="comment-date">{{ comment.createdAt | date:'dd.MM.yyyy HH:mm' }}</span>
      </div>
      <p class="comment-text">{{ comment.text }}</p>
      <button class="btn-reply-comment" (click)="reply.emit(comment.id)">↩ Odgovori</button>

      <div class="comment-replies" *ngIf="comment.replies?.length">
        <app-comment-thread
          *ngFor="let child of comment.replies"
          [comment]="child"
          [depth]="depth + 1"
          (reply)="reply.emit($event)">
        </app-comment-thread>
      </div>
    </div>
  `
})
export class CommentThreadComponent {
  @Input() comment: any;
  @Input() depth = 0;
  @Output() reply = new EventEmitter<number>();
}
