import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guest: true } },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { guest: true } },
    { path: '/tournaments', name: 'tournaments', component: () => import('../views/TournamentsView.vue'), meta: { auth: true } },
    { path: '/tournaments/:id', name: 'tournament', component: () => import('../views/TournamentDetailView.vue'), meta: { auth: true } },
    { path: '/matches', name: 'matches', component: () => import('../views/MatchesView.vue'), meta: { auth: true } },
    { path: '/matches/:id', name: 'match', component: () => import('../views/MatchDetailView.vue'), meta: { auth: true } },
    { path: '/teams', name: 'teams', component: () => import('../views/TeamsView.vue'), meta: { auth: true } },
    { path: '/teams/:id', name: 'team', component: () => import('../views/TeamDetailView.vue'), meta: { auth: true } },
    { path: '/players', name: 'players', component: () => import('../views/PlayersView.vue'), meta: { auth: true } },
    { path: '/players/:id', name: 'player', component: () => import('../views/PlayerDetailView.vue'), meta: { auth: true } },
    { path: '/statistics', name: 'statistics', component: () => import('../views/StatisticsView.vue'), meta: { auth: true } },
    { path: '/admin', name: 'admin', component: () => import('../views/admin/AdminDashboard.vue'), meta: { auth: true, roles: ['ADMIN'] } },
    { path: '/referee', name: 'referee', component: () => import('../views/referee/RefereeDashboard.vue'), meta: { auth: true, roles: ['REFEREE', 'ADMIN'] } },
    { path: '/referee/matches/:id', name: 'referee-match', component: () => import('../views/referee/LiveMatchControl.vue'), meta: { auth: true, roles: ['REFEREE', 'ADMIN'] } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.guest && auth.isAuthenticated) return { name: 'home' }
  const roles = to.meta.roles as string[] | undefined
  if (roles && (!auth.role || !roles.includes(auth.role))) return { name: 'home' }
  return true
})

export default router
