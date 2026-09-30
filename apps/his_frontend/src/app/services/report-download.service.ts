import { Injectable } from '@angular/core';
import type { LabResult, Patient } from '../models';

/** Client-side input of the imaging report download (not a backend contract). */
export interface ImagingReportContent {
  examName: string;
  findings: string;
  conclusion: string;
  performedAt: string;
  reportedAt: string;
}

/**
 * Pure client-side logic (not a backend contract): builds a text/CSV Blob and triggers a browser download via a hidden `<a download>`.
 * No PDF library is used per project rules -- report downloads are plain text or CSV.
 */
@Injectable({ providedIn: 'root' })
export class ReportDownloadService {
  downloadLabReport(result: LabResult, patient: Patient): void {
    const lines = [
      `Wynik badania laboratoryjnego`,
      `Pacjent: ${patient.lastName} ${patient.firstName}`,
      `PESEL: ${patient.pesel ?? 'brak'}`,
      `Badanie: ${result.testName}`,
      `Pobranie: ${result.collectedAt}`,
      `Wynik: ${result.resultedAt}`,
      `Status: ${result.status}`,
      '',
      'Parametr\tWynik\tJednostka\tZakres referencyjny\tFlaga',
      ...result.observations.map(
        (o) =>
          `${o.analyteName}\t${o.value}\t${o.unit}\t${o.referenceRange.low ?? ''}-${o.referenceRange.high ?? ''}\t${o.flag}`,
      ),
    ];
    this.triggerDownload(`wynik-lab-${result.id}.txt`, lines.join('\n'), 'text/plain');
  }

  downloadImagingReport(report: ImagingReportContent, patient: Patient): void {
    const lines = [
      `Wynik badania obrazowego`,
      `Pacjent: ${patient.lastName} ${patient.firstName}`,
      `PESEL: ${patient.pesel ?? 'brak'}`,
      `Badanie: ${report.examName}`,
      `Wykonano: ${report.performedAt}`,
      `Opisano: ${report.reportedAt}`,
      '',
      'Opis:',
      report.findings,
      '',
      'Wnioski:',
      report.conclusion,
    ];
    this.triggerDownload(`wynik-obrazowy-${Date.now()}.txt`, lines.join('\n'), 'text/plain');
  }

  downloadCsv(filename: string, rows: Record<string, unknown>[]): void {
    if (rows.length === 0) {
      this.triggerDownload(filename, '', 'text/csv');
      return;
    }
    const headers = Object.keys(rows[0]);
    const csvLines = [
      headers.join(','),
      ...rows.map((row) => headers.map((h) => this.csvEscape(row[h])).join(',')),
    ];
    this.triggerDownload(filename, csvLines.join('\n'), 'text/csv');
  }

  private csvEscape(value: unknown): string {
    const str = value === null || value === undefined ? '' : String(value);
    return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
  }

  private triggerDownload(filename: string, content: string, mimeType: string): void {
    const blob = new Blob([content], { type: `${mimeType};charset=utf-8` });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
