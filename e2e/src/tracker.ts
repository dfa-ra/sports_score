export class Tracker {
  tournaments = new Set<string>()
  teams = new Set<string>()
  matches = new Set<string>()
  galleries = new Set<string>()

  tournament(id?: string) {
    if (id) this.tournaments.add(id)
    return id || ''
  }

  team(id?: string) {
    if (id) this.teams.add(id)
    return id || ''
  }

  match(id?: string) {
    if (id) this.matches.add(id)
    return id || ''
  }

  gallery(id?: string) {
    if (id) this.galleries.add(id)
    return id || ''
  }

  absorb(other: Tracker) {
    for (const id of other.tournaments) this.tournaments.add(id)
    for (const id of other.teams) this.teams.add(id)
    for (const id of other.matches) this.matches.add(id)
    for (const id of other.galleries) this.galleries.add(id)
  }

  get empty() {
    return this.tournaments.size + this.teams.size + this.matches.size + this.galleries.size === 0
  }
}
