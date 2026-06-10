import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerState = vi.hoisted(() => ({ pathname: '/', resolvedPathname: '/' }))
const useAuthMock = vi.hoisted(() => vi.fn(() => ({ user: null, isLoading: false })))

// Layout is a component-only file with no exported pure functions or constants.
// We verify that the named export exists for the router to consume.

vi.mock('@tanstack/react-router', () => ({
  Outlet: () => null,
  Link: ({ children }: { children: unknown }) => children,
  useRouterState: () => routerState,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: useAuthMock,
}))

vi.mock('@/shared/components/language-switcher', () => ({
  LanguageSwitcher: () => null,
}))

vi.mock('@/shared/components/user-menu', () => ({
  UserMenu: () => null,
}))

vi.mock('./layout-header-style', () => ({
  getAppHeaderClassName: () => 'header-class',
}))

vi.mock('./layout-main-content', () => ({
  WUKONG_EMBEDDED_PATH: '/wukong',
  resolveAppMainContentPathname: (p: string) => p,
  getAppMainContentLayout: () => ({
    mainClassName: 'main-class',
    contentClassName: 'content-class',
  }),
}))

import { Layout } from './layout'

describe('Layout', () => {
  beforeEach(() => {
    routerState.pathname = '/'
    routerState.resolvedPathname = '/'
    useAuthMock.mockClear()
  })

  it('exports a named Layout component function', () => {
    expect(typeof Layout).toBe('function')
    expect(Layout.name).toBe('Layout')
  })

  it('keeps auth enabled for regular app routes', () => {
    renderToStaticMarkup(createElement(Layout))

    expect(useAuthMock).toHaveBeenCalledWith(true)
  })

  it('disables auth lookups for the Wukong embedded route', () => {
    routerState.pathname = '/wukong'
    routerState.resolvedPathname = '/wukong'

    renderToStaticMarkup(createElement(Layout))

    expect(useAuthMock).toHaveBeenCalledWith(false)
  })
})
