import { describe, expect, it } from 'vitest'
import { normalizeBackgroundImageUrl } from './weekly-skill-card'

describe('normalizeBackgroundImageUrl', () => {
  it('allows site-local image paths', () => {
    expect(normalizeBackgroundImageUrl(' /recommendation-banners/weekly/banner.jpg ')).toBe(
      '/recommendation-banners/weekly/banner.jpg',
    )
  })

  it('allows https image URLs and same-origin test URLs', () => {
    expect(normalizeBackgroundImageUrl('https://cdn.example.com/banner.jpg')).toBe('https://cdn.example.com/banner.jpg')
    expect(normalizeBackgroundImageUrl('http://test.example.invalid:18002/banner.jpg', 'http://test.example.invalid:18002')).toBe(
      'http://test.example.invalid:18002/banner.jpg',
    )
  })

  it('rejects unsafe or cross-origin non-https URLs', () => {
    expect(normalizeBackgroundImageUrl('http://cdn.example.com/banner.jpg', 'http://test.example.invalid:18002')).toBeUndefined()
    expect(normalizeBackgroundImageUrl('//evil.example/banner.jpg')).toBeUndefined()
    expect(normalizeBackgroundImageUrl('javascript:alert(1)')).toBeUndefined()
    expect(normalizeBackgroundImageUrl('data:image/svg+xml;base64,PHN2Zy8+')).toBeUndefined()
    expect(normalizeBackgroundImageUrl('')).toBeUndefined()
  })
})
