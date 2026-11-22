import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/api.service';

type Decision = 'ACCEPT' | 'REJECT';

@Component({
  selector: 'app-quotation-response',
  standalone: true,
  templateUrl: './quotation-response.html',
  styleUrl: './quotation-response.css'
})
export class QuotationResponse implements OnInit {

  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  token = '';
  decision: Decision | null = null;

  loading = false;
  completed = false;
  error = '';

  resultMessage = '';

  ngOnInit(): void {

    this.token = this.route.snapshot.paramMap.get('token') ?? '';

    const decision =
      this.route.snapshot.queryParamMap.get('decision')?.toUpperCase();

    if (decision === 'ACCEPT' || decision === 'REJECT') {
      this.decision = decision;
    }

    if (!this.token || !this.decision) {
      this.error = 'This quotation response link is invalid or incomplete.';
      return;
    }

    // Automatically submit the decision when the client opens the email link.
    this.submitResponse();
  }

  get isAccept(): boolean {
    return this.decision === 'ACCEPT';
  }

  get isReject(): boolean {
    return this.decision === 'REJECT';
  }

  submitResponse(): void {

    if (!this.token || !this.decision || this.loading || this.completed) {
      return;
    }

    this.loading = true;
    this.error = '';

    this.api.quotationResponse(this.token, this.decision).subscribe({

      next: (message: string) => {

        this.loading = false;
        this.completed = true;
        this.resultMessage = this.isAccept
          ? 'Thank you for accepting our quotation. Your request will be handled manually by our team.'
          : 'You have rejected this quotation. Your quotation has been closed.';

      },

      error: (err) => {

        this.loading = false;

        this.error =
          err?.error ||
          'We could not process your response. This quotation may have already been responded to or closed.';
      }

    });
  }
}