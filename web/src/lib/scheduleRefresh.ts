import { ref } from 'vue'

/**
 * Bumped after an admin saves a kickoff or venue.
 * Views that already hold that match refetch; unrelated caches stay.
 */
export const scheduleRevision = ref(0)

export function noteScheduleChange() {
  scheduleRevision.value += 1
}
