export type NamedPlayer = {
  firstName?: string | null
  lastName?: string | null
  displayName?: string | null
}

export type PlayerHeader = {
  title: string
  shirt: string
}

function compact(value?: string | null) {
  return (value || '').replace(/\s+/g, ' ').trim()
}

export type PersonFields = {
  firstName?: string | null
  lastName?: string | null
  playerFirstName?: string | null
  playerLastName?: string | null
  secondaryPlayerFirstName?: string | null
  secondaryPlayerLastName?: string | null
  name?: string | null
  displayName?: string | null
  playerName?: string | null
  secondaryPlayerName?: string | null
}

/** «Фамилия Имя» from registration. Shirt nickname is not the title. */
export function registeredName(person?: PersonFields | null, role: 'primary' | 'secondary' = 'primary') {
  if (!person) return ''
  const last = role === 'secondary'
    ? person.secondaryPlayerLastName
    : (person.playerLastName ?? person.lastName)
  const first = role === 'secondary'
    ? person.secondaryPlayerFirstName
    : (person.playerFirstName ?? person.firstName)
  const legal = compact(`${last || ''} ${first || ''}`)
  if (legal) return legal
  if (role === 'secondary') return compact(person.secondaryPlayerName)
  return compact(person.playerName) || compact(person.displayName) || compact(person.name)
}

function sameName(left: string, right: string) {
  return left.localeCompare(right, 'ru', { sensitivity: 'accent' }) === 0
}

/** Profile header: registration surname and given name, shirt nickname only when it differs. */
export function playerHeader(card: NamedPlayer | null | undefined): PlayerHeader {
  const last = compact(card?.lastName)
  const first = compact(card?.firstName)
  const legal = compact(`${last} ${first}`)
  const givenFamily = compact(`${first} ${last}`)
  const shirt = compact(card?.displayName)
  const title = legal || shirt || 'Игрок'
  const showShirt = Boolean(
    shirt && legal && !sameName(shirt, legal) && !sameName(shirt, givenFamily),
  )
  return { title, shirt: showShirt ? shirt : '' }
}
