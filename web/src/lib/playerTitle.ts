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
