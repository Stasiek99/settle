import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { providePrimeNG } from 'primeng/config';

import { ApiStatus } from './api-status';

describe('ApiStatus', () => {
  let http: HttpTestingController;

  function render() {
    const fixture = TestBed.createComponent(ApiStatus);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ReturnType<typeof render>): string {
    return fixture.nativeElement.textContent as string;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), providePrimeNG()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the API health endpoint through the /api proxy prefix', () => {
    render();

    const request = http.expectOne('/api/actuator/health');
    expect(request.request.method).toBe('GET');
    request.flush({ status: 'UP' });
  });

  it('shows CHECKING until the response arrives', () => {
    const fixture = render();

    expect(text(fixture)).toContain('CHECKING');
    http.expectOne('/api/actuator/health').flush({ status: 'UP' });
  });

  it('shows UP when the API reports UP', async () => {
    const fixture = render();
    http.expectOne('/api/actuator/health').flush({ status: 'UP' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('UP');
    expect(text(fixture)).not.toContain('DOWN');
  });

  it('shows DOWN when the API reports another status', async () => {
    const fixture = render();
    http.expectOne('/api/actuator/health').flush({ status: 'OUT_OF_SERVICE' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('DOWN');
  });

  it('shows DOWN when the request fails', async () => {
    const fixture = render();
    http.expectOne('/api/actuator/health').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('DOWN');
  });

  it('checks again when the button is pressed', async () => {
    const fixture = render();
    http.expectOne('/api/actuator/health').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();

    http.expectOne('/api/actuator/health').flush({ status: 'UP' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(fixture)).toContain('UP');
  });
});
