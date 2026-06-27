import { environment } from '../../environments/environment';

export function resolveMediaUrl(url: string | null | undefined, placeholder?: string): string {
  const fallback = placeholder || 'https://placehold.co/400x250/F5F5F0/333333?text=NO+IMAGE';
  if (!url || url === 'placeholder_error') return fallback;
  if (url.startsWith('http')) return url;
  if (url.startsWith('images/') || url.startsWith('pdfs/')) {
    return `${environment.apiUrl}/files/${url}`;
  }
  const baseUrl = environment.apiUrl.endsWith('/api')
    ? environment.apiUrl.replace('/api', '')
    : environment.apiUrl;
  return baseUrl + '/uploads/' + url;
}
