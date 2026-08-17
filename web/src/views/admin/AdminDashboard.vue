<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '../../api/client'
const users = ref<any[]>([])
onMounted(async () => {
  const { data } = await api.get('/admin/users', { params: { size: 50 } })
  users.value = data.content
})
</script>
<template>
  <section class="stack">
    <h1>Admin Dashboard</h1>
    <p>Manage users, tournaments, matches, and referees via API-backed screens.</p>
    <div class="panel">
      <h2>Users</h2>
      <table class="table">
        <thead><tr><th>Email</th><th>Role</th><th>Enabled</th></tr></thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.email }}</td><td>{{ u.role }}</td><td>{{ u.enabled }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
