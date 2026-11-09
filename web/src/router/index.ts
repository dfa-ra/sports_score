import { createRouter, createWebHistory } from 'vue-router'
import { setPageMeta } from '../lib/pageMeta'
import { useAuthStore } from '../stores/auth'

const FALLBACK_DESCRIPTION = 'Турниры, таблица, календарь и матчи студенческой лиги ИТМО.'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: 'Главная', description: 'Лига ИТМО: бомбардиры и лучшие по передачам' } },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guest: true, title: 'Вход', description: 'Вход в Лигу ИТМО.' } },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { guest: true, title: 'Регистрация', description: 'Регистрация в Лиге ИТМО.' } },
    { path: '/forgot-password', name: 'forgot-password', component: () => import('../views/ForgotPasswordView.vue'), meta: { guest: true, title: 'Восстановление пароля', description: 'Ссылка для нового пароля.' } },
    { path: '/reset-password', name: 'reset-password', component: () => import('../views/ResetPasswordView.vue'), meta: { title: 'Новый пароль', description: 'Задайте новый пароль.' } },
    { path: '/privacy', name: 'privacy', component: () => import('../views/PrivacyView.vue'), meta: { title: 'Политика конфиденциальности', description: 'Как Лига ИТМО обрабатывает персональные данные.' } },
    { path: '/terms', name: 'terms', component: () => import('../views/TermsView.vue'), meta: { title: 'Условия пользования', description: 'Лига ИТМО: условия использования сайта' } },
    { path: '/table', name: 'table', component: () => import('../views/TableView.vue'), meta: { title: 'Таблица', description: 'Турнирная таблица, результаты, бомбардиры и лидеры по передачам.' } },
    { path: '/calendar', name: 'calendar', component: () => import('../views/CalendarView.vue'), meta: { title: 'Игры', description: 'Лига ИТМО: календарь матчей' } },
    { path: '/live', name: 'live', component: () => import('../views/CalendarView.vue'), meta: { title: 'Live', description: 'Матчи, которые идут сейчас.' } },
    { path: '/tournaments', name: 'tournaments', component: () => import('../views/TournamentsView.vue'), meta: { title: 'Турниры', description: 'Лига ИТМО: турниры, сезон и формат' } },
    { path: '/tournaments/:id', name: 'tournament', component: () => import('../views/TournamentDetailView.vue'), meta: { title: 'Турнир', description: 'Лига ИТМО: турнир' } },
    { path: '/matches', redirect: '/calendar' },
    { path: '/matches/:id', name: 'match', component: () => import('../views/MatchDetailView.vue'), meta: { title: 'Матч', description: 'Лига ИТМО: протокол матча' } },
    { path: '/teams', name: 'teams', component: () => import('../views/TeamsView.vue'), meta: { title: 'Команды', description: 'Лига ИТМО: команды' } },
    { path: '/teams/:id', name: 'team', component: () => import('../views/TeamDetailView.vue'), meta: { title: 'Команда', description: 'Лига ИТМО: команда' } },
    { path: '/players', name: 'players', component: () => import('../views/PlayersView.vue'), meta: { title: 'Игроки', description: 'Лига ИТМО: поиск игроков' } },
    { path: '/players/:id', name: 'player', component: () => import('../views/PlayerDetailView.vue'), meta: { title: 'Игрок', description: 'Игрок Лиги ИТМО' } },
    { path: '/statistics', redirect: { path: '/table', query: { tab: 'scorers' } } },
    { path: '/my-team', name: 'my-team', component: () => import('../views/MyTeamView.vue'), meta: { auth: true, title: 'Моя команда', description: 'Заявка и матчи вашей команды.' } },
    { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { auth: true, title: 'Профиль', description: 'Профиль участника Лиги ИТМО.' } },
    { path: '/admin', name: 'admin', component: () => import('../views/admin/AdminDashboard.vue'), meta: { auth: true, roles: ['ADMIN'], title: 'Админ', description: 'Управление лигой.' } },
    { path: '/referee', name: 'referee', component: () => import('../views/referee/RefereeDashboard.vue'), meta: { auth: true, roles: ['REFEREE', 'ADMIN'], title: 'Судья', description: 'Матчи судьи.' } },
    { path: '/referee/matches/:id', name: 'referee-match', component: () => import('../views/referee/LiveMatchControl.vue'), meta: { auth: true, roles: ['REFEREE', 'ADMIN'], title: 'Пульт судьи', description: 'Протокол матча.' } },
    { path: '/analyst', name: 'analyst', component: () => import('../views/analyst/AnalystDashboard.vue'), meta: { auth: true, roles: ['ANALYST', 'ADMIN'], title: 'Аналитик', description: 'Матчи аналитика.' } },
    { path: '/analyst/matches/:id', name: 'analyst-match', component: () => import('../views/analyst/AnalystPad.vue'), meta: { auth: true, roles: ['ANALYST', 'ADMIN'], title: 'Пульт аналитика', description: 'Статистика матча.' } },
    { path: '/forbidden', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { title: 'Недостаточно прав', description: 'Этот раздел закрыт для вашей роли.' } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: 'Не найдено', description: 'Страница не найдена.' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.name === 'calendar' && to.query.tab === 'live') return { path: '/live' }
  if (to.name === 'calendar' && to.query.tab === 'played') return { path: '/table', query: { tab: 'results' } }
  const auth = useAuthStore()
  await auth.restoreSession()
  if (to.meta.auth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.guest && auth.isAuthenticated) return { name: 'home' }
  // Signed-in visitors can open /my-team. The page shows a stub unless they are a player or captain.
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.some((role) => auth.hasRole(role as any))) {
    if (to.path === '/admin' || to.path.startsWith('/referee')) {
      return { name: 'forbidden', replace: true }
    }
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  const meta = to.meta as { title?: string; description?: string }
  setPageMeta({
    title: meta.title || 'Лига ИТМО',
    description: meta.description || FALLBACK_DESCRIPTION,
    path: to.fullPath,
  })
})

export default router
