import { HttpErrorResponse } from '@angular/common/http';

interface ProblemBody {
  detail?: string;
  errors?: { field?: string; message?: string }[];
}

const FALLBACK = 'Something went wrong. Please try again.';

export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return FALLBACK;
  }
  if (error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  if (error.status >= 500) {
    return 'The server had a problem. Please try again later.';
  }
  const body = error.error as ProblemBody | null;
  const fieldErrors = body?.errors
    ?.filter((e) => e.message)
    .map((e) => (e.field ? `${e.field}: ${e.message}` : e.message))
    .join('; ');
  if (fieldErrors) {
    return fieldErrors;
  }
  if (typeof body?.detail === 'string' && body.detail.length > 0) {
    return body.detail;
  }
  return FALLBACK;
}
