<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import api from '../api/client'
import PlayoffPairCard from './PlayoffPairCard.vue'
import TeamCrest from './TeamCrest.vue'
import { fromLocalInput, toLocalInput } from '../lib/datetime'
import { apiError } from '../lib/errors'
import { noteScheduleChange } from '../lib/scheduleRefresh'
import {
  advancedTeamId,
  arrivalLine,
  bracketIsEmpty,
  bracketPanels,
  branchKind,
  bronzeBoardColumns,
  bronzeChampionCell,
  bronzeEarlyCell,
  bronzeFinalCell,
  bronzeLinkCell,
  bronzeMatchRows,
  bronzeRoundColumn,
  bronzeSemiCell,
  bronzeThirdRow,
  championCell,
  championHeaderCell,
  championWhen,
  connectorCell,
  defaultPanelIndex,
  gridColumns,
  gridRows,
  headerCell,
  matchCell,
  roundNote,
  columnSlots,
  linkSlots,
  slotsOf,
  thirdPlaceSlots,
  cssViewportWidth,
  stackChampion,
  teamCountOf,
  useRoundTabs,
  winnerSide,
  type PlayoffRound,
  type PlayoffSlot,
} from '../lib/playoffGrid'
import { useTeamDirectory } from '../lib/useTeamDirectory'

const props = defineProps<{
  tournamentId: string
  rounds: PlayoffRound[]
  editable?: boolean
  loading?: boolean
  error?: boolean
}>()
const emit = defineEmits<{ saved: []; retry: []; 'show-table': [] }>()

const names = useTeamDirectory()
const drafts = reactive<Record<string, { scheduledAt: string; venue: string }>>({})
const fresh = reactive<Record<string, { scheduledAt: string | null; venue: string | null }>>({})
const timeError = reactive<Record<string, string>>({})
const saveError = ref('')
const pending = reactive<Record<string, boolean>>({})
const root = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)
const tablist = ref<HTMLElement | null>(null)
function readViewport() {
  if (typeof window === 'undefined') return 1280
  return cssViewportWidth(window.innerWidth, window.visualViewport?.width, window.visualViewport?.scale)
}

const viewport = ref(readViewport())
const containerWidth = ref(0)
const active = ref(0)
let pan: { x: number; y: number; index: number; id: number } | null = null
const lit = ref<string[]>([])
const canHover = ref(false)
const panelHeight = ref(0)
let boardObserver: ResizeObserver | null = null
let panelObserver: ResizeObserver | null = null

const rounds = computed(() => props.rounds ?? [])
const panels = computed(() => bracketPanels(rounds.value))
const teamCount = computed(() => teamCountOf(rounds.value))
const compact = computed(() => teamCount.value > 8)
const tabMode = computed(() => !props.editable && useRoundTabs(teamCount.value, viewport.value, containerWidth.value))
const empty = computed(() => !props.loading && !props.error && bracketIsEmpty(rounds.value))
const squeeze = computed(() => !compact.value && !tabMode.value && viewport.value > 720 && viewport.value <= 1040)
const stack = computed(() => stackChampion(rounds.value.length, viewport.value))
const firstSlots = computed(() => Math.max(1, slotsOf(rounds.value[0]).length))
const hasBronze = computed(() => rounds.value.some((round) => thirdPlaceSlots(slotsOf(round)).length > 0))
const gridStyle = computed(() => ({
  gridTemplateColumns: gridColumns(rounds.value.length, compact.value, squeeze.value, stack.value),
  gridTemplateRows: stack.value ? 'auto auto auto auto' : gridRows(firstSlots.value, compact.value),
}))
const showSwitcher = computed(() => tabMode.value && panels.value.length > 1)

function sameStamp(left?: string | null, right?: string | null) {
  if (!left && !right) return true
  if (!left || !right) return false
  const a = Date.parse(left)
  const b = Date.parse(right)
  if (Number.isNaN(a) || Number.isNaN(b)) return left === right
  return a === b
}

function viewSlot(slot: PlayoffSlot): PlayoffSlot {
  if (!slot.id) return slot
  const patch = fresh[slot.id]
  if (!patch) return slot
  return { ...slot, scheduledAt: patch.scheduledAt, venue: patch.venue }
}

watch(
  () => props.rounds,
  (next) => {
    for (const round of next ?? []) {
      for (const slot of round.slots ?? []) {
        if (!slot.id) continue
        const patch = fresh[slot.id]
        if (patch && sameStamp(slot.scheduledAt, patch.scheduledAt) && (slot.venue || '') === (patch.venue || '')) {
          delete fresh[slot.id]
        }
        const shown = fresh[slot.id]
        drafts[slot.id] = {
          scheduledAt: toLocalInput(shown ? shown.scheduledAt : slot.scheduledAt),
          venue: shown ? (shown.venue || '') : (slot.venue || ''),
        }
      }
    }
    active.value = defaultPanelIndex(bracketPanels(next ?? []))
    void nextTick(() => {
      scrollTo(active.value, false)
      measureLinks()
    })
  },
  { immediate: true },
)

function measureContainer() {
  const card = root.value?.querySelector('.po-card') as HTMLElement | null
  const box = card ?? root.value
  if (!box) return
  const style = getComputedStyle(box)
  const pad = (parseFloat(style.paddingLeft) || 0) + (parseFloat(style.paddingRight) || 0)
  containerWidth.value = Math.max(0, box.clientWidth - pad)
}

function onViewport() {
  viewport.value = readViewport()
  measureContainer()
}

onMounted(() => {
  canHover.value = window.matchMedia('(hover: hover) and (pointer: fine)').matches
  window.addEventListener('resize', onViewport)
  window.visualViewport?.addEventListener('resize', onViewport)
  onViewport()
  if (root.value && typeof ResizeObserver !== 'undefined') {
    boardObserver = new ResizeObserver(() => {
      measureContainer()
      measurePanel()
      measureLinks()
    })
    boardObserver.observe(root.value)
  }
  void nextTick(() => {
    scrollTo(active.value, false)
    measureLinks()
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', onViewport)
  window.visualViewport?.removeEventListener('resize', onViewport)
  boardObserver?.disconnect()
  panelObserver?.disconnect()
})

function previousTitle(roundIndex: number) {
  return rounds.value[roundIndex - 1]?.title
}

function logo(id?: string | null) {
  return names.logo(id)
}

function light(id: string | null) {
  lit.value = id ? [id] : []
}

function lightSlot(slot: PlayoffSlot) {
  lit.value = [slot.homeTeamId, slot.awayTeamId].filter((id): id is string => !!id)
}

function clearLight() {
  lit.value = []
}

function pathLit(ids: Array<string | null | undefined>) {
  return ids.some((id) => !!id && lit.value.includes(id))
}

function feeders(roundIndex: number, slot: PlayoffSlot) {
  const prev = slotsOf(rounds.value[roundIndex - 1])
  const index = Number(slot.slotIndex ?? 0)
  return [prev[index * 2], prev[index * 2 + 1]]
}

function arrivals(roundIndex: number, slot: PlayoffSlot) {
  if (roundIndex <= 0) return []
  return feeders(roundIndex, slot)
    .map((feeder) => arrivalLine(feeder, previousTitle(roundIndex)))
    .filter((line): line is { name: string; detail: string } => !!line)
}

function nextHint(roundIndex: number) {
  const next = rounds.value[roundIndex + 1]
  if (!next) return ''
  const note = roundNote(slotsOf(next)).text.replace(' · завершён', '')
  return note ? `Дальше · ${next.title} ${note}` : `Дальше · ${next.title}`
}

const finalRound = computed(() => rounds.value[rounds.value.length - 1])
const finalSlot = computed(() => linkSlots(slotsOf(finalRound.value))[0])
const extraFinalSlots = computed(() => thirdPlaceSlots(slotsOf(finalRound.value)))
const champion = computed(() => {
  const slot = finalSlot.value
  const side = slot ? winnerSide(slot) : null
  const id = advancedTeamId(slot)
  if (!slot || !side || !id) return null
  const name = side === 'home' ? slot.homeTeamName : slot.awayTeamName
  return { id, name: name || 'Команда' }
})

function connectorTeams(roundIndex: number, slotIndex: number) {
  const sources = slotsOf(rounds.value[roundIndex])
  const upper = sources[slotIndex * 2]
  const lower = sources[slotIndex * 2 + 1]
  return {
    upper,
    lower,
    upperId: advancedTeamId(upper),
    lowerId: advancedTeamId(lower),
    upperKind: branchKind(upper),
    lowerKind: branchKind(lower),
  }
}

function exitKind(upper: 'on' | 'pend' | 'plain', lower: 'on' | 'pend' | 'plain') {
  if (upper === 'on' && lower === 'on') return 'on'
  if (upper === 'pend' || lower === 'pend') return 'pend'
  return 'plain'
}

function measurePanel() {
  const node = scroller.value?.querySelector('.po-panel.on, .po-panel') as HTMLElement | null
  const current = scroller.value?.querySelectorAll('.po-panel')[active.value] as HTMLElement | undefined
  panelHeight.value = current?.offsetHeight || node?.offsetHeight || 0
  if (current && typeof ResizeObserver !== 'undefined') {
    panelObserver?.disconnect()
    panelObserver = new ResizeObserver(() => {
      panelHeight.value = current.offsetHeight
    })
    panelObserver.observe(current)
  }
}

function scrollTo(index: number, smooth: boolean) {
  const el = scroller.value
  if (!el) return
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  el.scrollTo({ left: index * el.clientWidth, behavior: smooth && !reduce ? 'smooth' : 'auto' })
  void nextTick(measurePanel)
}

// Entering tab mode mounts a new scroller at scrollLeft 0. Put the active round back in view.
watch(tabMode, (on) => {
  if (!on) return
  scrollTo(active.value, false)
  requestAnimationFrame(() => scrollTo(active.value, false))
}, { flush: 'post' })

watch(viewport, () => {
  if (!tabMode.value) return
  scrollTo(active.value, false)
}, { flush: 'post' })

function go(index: number) {
  active.value = index
  scrollTo(index, true)
}

function onSwipe() {
  const el = scroller.value
  if (!el || el.clientWidth <= 0) return
  const index = Math.round(el.scrollLeft / el.clientWidth)
  if (index !== active.value && index >= 0 && index < panels.value.length) {
    active.value = index
    void nextTick(measurePanel)
  }
}

function onTouchStart(event: TouchEvent) {
  const touch = event.changedTouches[0]
  if (!touch) return
  pan = { x: touch.clientX, y: touch.clientY, index: active.value, id: touch.identifier }
}

function onTouchEnd(event: TouchEvent) {
  const touch = event.changedTouches[0]
  if (!pan || !touch || touch.identifier !== pan.id) return
  const start = pan
  pan = null
  const dx = touch.clientX - start.x
  const dy = touch.clientY - start.y
  if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return
  const toward = dx < 0 ? start.index + 1 : start.index - 1
  const next = Math.max(0, Math.min(panels.value.length - 1, toward))
  if (next !== active.value) go(next)
}

function onTabKey(event: KeyboardEvent) {
  if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return
  event.preventDefault()
  const next = event.key === 'ArrowRight'
    ? Math.min(panels.value.length - 1, active.value + 1)
    : Math.max(0, active.value - 1)
  go(next)
  void nextTick(() => {
    const button = tablist.value?.querySelector('[aria-selected="true"]') as HTMLElement | null
    button?.focus()
  })
}

async function save(slot: PlayoffSlot) {
  if (!slot.id) return
  saveError.value = ''
  const draft = drafts[slot.id]
  if (!draft?.scheduledAt) {
    timeError[slot.id] = 'Укажите время пары.'
    return
  }
  timeError[slot.id] = ''
  pending[slot.id] = true
  try {
    const { data } = await api.put<PlayoffSlot>(`/tournaments/${props.tournamentId}/playoff/slots/${slot.id}`, {
      scheduledAt: draft.scheduledAt ? fromLocalInput(draft.scheduledAt) : undefined,
      venue: draft.venue,
    })
    fresh[slot.id] = {
      scheduledAt: data?.scheduledAt || fromLocalInput(draft.scheduledAt),
      venue: data?.venue ?? draft.venue,
    }
    drafts[slot.id] = {
      scheduledAt: toLocalInput(fresh[slot.id].scheduledAt),
      venue: fresh[slot.id].venue || '',
    }
    emit('saved')
    noteScheduleChange()
  } catch (e: unknown) {
    saveError.value = apiError(e)
  } finally {
    pending[slot.id] = false
  }
}

const champHeaderStyle = computed(() => championHeaderCell(rounds.value.length, stack.value))
const champStyle = computed(() => championCell(rounds.value.length, firstSlots.value, stack.value))
const finalLinkStyle = computed(() => ({
  gridColumn: String(rounds.value.length * 2),
  gridRow: `2 / span ${firstSlots.value}`,
}))
const semiRoundIndex = computed(() => Math.max(0, rounds.value.length - 2))
const bronzeRows = computed(() => bronzeMatchRows(firstSlots.value, linkSlots(slotsOf(rounds.value[semiRoundIndex.value])).length || 2))
const bronzeBoardStyle = computed(() => ({
  gridTemplateColumns: bronzeBoardColumns(rounds.value.length, compact.value, squeeze.value),
  gridTemplateRows: `auto repeat(${bronzeRows.value}, auto)`,
}))

type LinkSeg = { d: string; kind: string; ids: Array<string | null | undefined> }
const linkPaths = ref<LinkSeg[][]>([])

function measureLinks() {
  const board = root.value?.querySelector('.bronze-board') as HTMLElement | null
  if (!board) {
    linkPaths.value = []
    return
  }
  const links = [...board.querySelectorAll<HTMLElement>('.b-link')]
  const next: LinkSeg[][] = []
  links.forEach((link, roundIndex) => {
    const box = link.getBoundingClientRect()
    const cards = (index: number) => [...board.querySelectorAll<HTMLElement>(`[data-round="${index}"] .pc`)]
      .filter((el) => !el.closest('[data-bronze]'))
    const fromCards = cards(roundIndex)
    const toCards = cards(roundIndex + 1)
    if (box.height < 1 || !toCards.length) {
      next.push([])
      return
    }
    const yOf = (el: HTMLElement) => {
      const rect = el.getBoundingClientRect()
      const y = ((rect.top + rect.height / 2 - box.top) / box.height) * 100
      return Math.max(0, Math.min(100, y))
    }
    const segs: LinkSeg[] = []
    toCards.forEach((target, index) => {
      const upper = fromCards[index * 2]
      const lower = fromCards[index * 2 + 1]
      if (!upper || !lower) return
      const teams = connectorTeams(roundIndex, index)
      const yTop = yOf(upper)
      const yBottom = yOf(lower)
      const yTarget = yOf(target)
      segs.push(
        { d: `M0 ${yTop} H50 V${yTarget}`, kind: teams.upperKind, ids: [teams.upperId] },
        { d: `M0 ${yBottom} H50 V${yTarget}`, kind: teams.lowerKind, ids: [teams.lowerId] },
        {
          d: `M50 ${yTarget} H100`,
          kind: exitKind(teams.upperKind, teams.lowerKind),
          ids: [teams.upperId, teams.lowerId],
        },
      )
    })
    next.push(segs)
  })
  linkPaths.value = next
}

function earlyStyle(roundIndex: number, slotIndex: number, slotCount: number) {
  return {
    gridColumn: bronzeRoundColumn(roundIndex),
    ...bronzeEarlyCell(slotIndex, slotCount, bronzeRows.value),
  }
}

function semiStyle(slotIndex: number, semiCount: number) {
  return {
    gridColumn: bronzeRoundColumn(semiRoundIndex.value),
    ...bronzeSemiCell(slotIndex, semiCount, bronzeRows.value),
  }
}

function finalPairStyle() {
  return {
    gridColumn: bronzeRoundColumn(rounds.value.length - 1),
    ...bronzeFinalCell(bronzeRows.value),
  }
}

function championPairStyle() {
  return {
    gridColumn: bronzeRoundColumn(rounds.value.length - 1),
    ...bronzeChampionCell(bronzeRows.value),
  }
}

function bronzePairStyle() {
  return {
    gridColumn: bronzeRoundColumn(semiRoundIndex.value),
    gridRow: bronzeThirdRow(bronzeRows.value),
  }
}
</script>

<template>
  <div ref="root" class="po">
    <div class="po-card">
      <div v-if="!loading && !error && !empty && !tabMode" class="po-card-h">
        <p class="po-hint">
          Время московское (МСК) · наведите на команду — подсветится её путь · клик по карточке открывает матч
        </p>
      </div>

      <div v-if="loading" class="po-state" role="status" aria-busy="true" aria-label="Сетка загружается">
        <div v-if="viewport <= 720" class="seg sk-seg" aria-hidden="true"><span></span><span></span><span></span></div>
        <div v-if="viewport <= 720" class="sk-stack">
          <div class="sk"><i class="n"></i><i></i><i></i><i class="w"></i></div>
          <div class="sk"><i class="n"></i><i></i><i></i><i class="w"></i></div>
        </div>
        <div v-else class="sk-desk" aria-hidden="true">
          <div v-for="count in [4, 2, 1]" :key="count" class="sk-col">
            <i class="rh"></i>
            <div v-for="n in count" :key="n" class="sk"><i class="n"></i><i></i><i></i><i class="w"></i></div>
          </div>
        </div>
      </div>

      <div v-else-if="error" class="msg err">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v6M12 16.5v.5" /></svg>
        <b>Не удалось загрузить сетку</b>
        <p>Проверьте соединение и попробуйте ещё раз.</p>
        <button class="po-action" type="button" @click="emit('retry')">Повторить</button>
      </div>

      <div v-else-if="empty" class="msg">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h5v5H3zM3 14h5v5H3zM16 9.5h5v5h-5zM8 7.5h4v9M12 12h4" /></svg>
        <b>Сетка ещё не сформирована</b>
        <p>Сетка появится после групп</p>
        <button class="po-action" type="button" @click="emit('show-table')">К таблице</button>
      </div>

      <div v-else-if="editable" class="bracket admin">
        <section v-for="panel in panels" :key="panel.key" class="round">
          <h3>{{ panel.title }}</h3>
          <article v-for="slot in panel.slots" :key="slot.id" class="admin-slot">
            <PlayoffPairCard
              :slot="viewSlot(slot)"
              :previous-title="previousTitle(panel.roundIndex)"
              :first-round="panel.roundIndex === 0 && !panel.thirdPlace"
              :compact="compact"
              :final="panel.champion && Number(slot.slotIndex ?? 0) === 0"
              :lit="lit"
              :can-hover="canHover"
              :logo="logo"
              @hover="light"
              @focus="lightSlot(slot)"
              @blur="clearLight"
            />
            <form v-if="slot.id && drafts[slot.id]" class="edit" @submit.prevent="save(slot)">
              <label class="field" :class="{ invalid: timeError[slot.id] }">Время
                <input
                  v-model="drafts[slot.id].scheduledAt"
                  type="datetime-local"
                  @input="timeError[slot.id] = ''"
                />
              </label>
              <p v-if="timeError[slot.id]" class="field-error">{{ timeError[slot.id] }}</p>
              <label class="field">Место проведения
                <input v-model="drafts[slot.id].venue" maxlength="120" placeholder="Зал" />
              </label>
              <button class="btn secondary" type="submit" :disabled="pending[slot.id]">Сохранить</button>
            </form>
          </article>
        </section>
      </div>

      <div v-else-if="tabMode" class="po-mobile">
        <div
          v-if="showSwitcher"
          ref="tablist"
          class="seg"
          role="tablist"
          aria-label="Раунды"
          @keydown="onTabKey"
        >
          <button
            v-for="(panel, index) in panels"
            :id="`po-tab-${index}`"
            :key="panel.key"
            type="button"
            role="tab"
            :aria-selected="index === active"
            :aria-controls="`po-panel-${index}`"
            :tabindex="index === active ? 0 : -1"
            :class="{ on: index === active }"
            @click="go(index)"
          >
            <span v-if="roundNote(panel.slots).live" class="dotl" aria-hidden="true"></span>
            {{ panel.tab }}
          </button>
        </div>
        <p v-if="showSwitcher" class="swipe">← свайп между раундами → · время МСК</p>
        <div
          ref="scroller"
          class="po-swipe"
          :style="panelHeight ? { height: `${panelHeight}px` } : undefined"
          @scroll.passive="onSwipe"
          @touchstart.passive="onTouchStart"
          @touchend.passive="onTouchEnd"
          @touchcancel.passive="onTouchEnd"
        >
          <section
            v-for="(panel, panelIndex) in panels"
            :id="`po-panel-${panelIndex}`"
            :key="panel.key"
            class="po-panel"
            role="tabpanel"
            :aria-labelledby="`po-tab-${panelIndex}`"
            :class="{ on: panelIndex === active }"
          >
            <h3 class="rh">
              {{ panel.title }}
              <small v-if="roundNote(panel.slots).text">{{ roundNote(panel.slots).text }}</small>
              <span v-else-if="roundNote(panel.slots).live" class="st live">идёт</span>
            </h3>
            <div class="pair-stack">
              <div v-for="slot in panel.slots" :key="slot.id" class="pair-block">
                <PlayoffPairCard
                  :slot="viewSlot(slot)"
                  :previous-title="previousTitle(panel.roundIndex)"
                  :first-round="panel.roundIndex === 0 && !panel.thirdPlace"
                  :final="panel.champion && Number(slot.slotIndex ?? 0) === 0"
                  :lit="lit"
                  :can-hover="false"
                  :logo="logo"
                />
                <ul v-if="!panel.thirdPlace && arrivals(panel.roundIndex, slot).length" class="how">
                  <li v-for="line in arrivals(panel.roundIndex, slot)" :key="line.name + line.detail">
                    <b>{{ line.name }}</b> · {{ line.detail }}
                  </li>
                </ul>
              </div>
            </div>
            <div v-if="panel.champion" class="champ-block">
              <div v-if="champion" class="champ won">
                <TeamCrest :src="logo(champion.id)" :name="champion.name" :size="30" />
                <div><b>{{ champion.name }}</b><span>Чемпион</span></div>
              </div>
              <div v-else class="champ">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3M12 14v4M8 20h8" /></svg>
                <div>
                  <b>Чемпион</b>
                  <span>{{ championWhen(finalSlot) ? `определится ${championWhen(finalSlot)}` : 'определится позже' }}</span>
                </div>
              </div>
            </div>
            <p v-else-if="!panel.thirdPlace && nextHint(panel.roundIndex)" class="next-hint">{{ nextHint(panel.roundIndex) }}</p>
          </section>
        </div>
      </div>

      <div v-else class="po-board" :class="{ compact, squeeze, stack }">
        <div v-if="hasBronze" class="bronze-board" :style="bronzeBoardStyle">
          <h3
            v-for="(round, roundIndex) in rounds"
            :key="`bh-${round.round ?? roundIndex}`"
            class="rh"
            :style="{ gridColumn: bronzeRoundColumn(roundIndex), gridRow: '1' }"
          >
            {{ round.title }}
            <small v-if="roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).text">{{ roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).text }}</small>
            <span v-else-if="roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).live" class="st live">идёт</span>
          </h3>

          <template v-for="(round, roundIndex) in rounds" :key="`bcol-${round.round ?? roundIndex}`">
            <template v-if="roundIndex < semiRoundIndex">
              <div
                v-for="slot in columnSlots(slotsOf(round), false)"
                :key="slot.id"
                class="b-slot"
                :data-round="roundIndex"
                :style="earlyStyle(roundIndex, Number(slot.slotIndex ?? 0), columnSlots(slotsOf(round), false).length)"
              >
                <PlayoffPairCard
                  :slot="viewSlot(slot)"
                  :previous-title="previousTitle(roundIndex)"
                  :first-round="roundIndex === 0"
                  :compact="compact"
                  :lit="lit"
                  :can-hover="canHover"
                  :logo="logo"
                  @hover="light"
                  @focus="lightSlot(slot)"
                  @blur="clearLight"
                />
              </div>
            </template>
            <template v-else-if="roundIndex === semiRoundIndex">
              <div
                v-for="slot in linkSlots(slotsOf(round))"
                :key="slot.id"
                class="b-slot"
                :data-round="roundIndex"
                :style="semiStyle(Number(slot.slotIndex ?? 0), linkSlots(slotsOf(round)).length)"
              >
                <PlayoffPairCard
                  :slot="viewSlot(slot)"
                  :previous-title="previousTitle(roundIndex)"
                  :compact="compact"
                  :lit="lit"
                  :can-hover="canHover"
                  :logo="logo"
                  @hover="light"
                  @focus="lightSlot(slot)"
                  @blur="clearLight"
                />
              </div>
              <div
                v-for="slot in extraFinalSlots"
                :key="slot.id"
                class="b-slot b-bronze"
                data-bronze
                :data-round="roundIndex"
                :style="bronzePairStyle()"
              >
                <PlayoffPairCard
                  :slot="viewSlot(slot)"
                  :previous-title="previousTitle(roundIndex)"
                  :compact="compact"
                  :lit="lit"
                  :can-hover="canHover"
                  :logo="logo"
                  @hover="light"
                  @focus="lightSlot(slot)"
                  @blur="clearLight"
                />
              </div>
            </template>
            <template v-else>
              <div
                v-for="slot in columnSlots(slotsOf(round), true)"
                :key="slot.id"
                class="b-slot"
                :data-round="roundIndex"
                :style="finalPairStyle()"
              >
                <PlayoffPairCard
                  :slot="viewSlot(slot)"
                  :previous-title="previousTitle(roundIndex)"
                  :compact="compact"
                  final
                  :lit="lit"
                  :can-hover="canHover"
                  :logo="logo"
                  @hover="light"
                  @focus="lightSlot(slot)"
                  @blur="clearLight"
                />
              </div>
              <div class="b-champ" :data-round="roundIndex" :style="championPairStyle()">
                <div v-if="champion" class="champ won">
                  <TeamCrest :src="logo(champion.id)" :name="champion.name" :size="30" />
                  <div><b>{{ champion.name }}</b><span>Чемпион</span></div>
                </div>
                <div v-else class="champ">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3M12 14v4M8 20h8" /></svg>
                  <div>
                    <b>Чемпион</b>
                    <span>{{ championWhen(finalSlot) ? `определится ${championWhen(finalSlot)}` : 'определится позже' }}</span>
                  </div>
                </div>
              </div>
            </template>
          </template>

          <div
            v-for="(round, roundIndex) in rounds.slice(0, -1)"
            :key="`blink-${round.round ?? roundIndex}`"
            class="b-link"
            :style="{ gridColumn: String(roundIndex * 2 + 2), ...bronzeLinkCell(bronzeRows) }"
          >
            <svg class="cn" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
              <path
                v-for="(seg, segIndex) in linkPaths[roundIndex] || []"
                :key="segIndex"
                :class="[seg.kind, { hl: pathLit(seg.ids) }]"
                :d="seg.d"
              />
            </svg>
          </div>
        </div>
        <div v-else class="po-grid" :style="gridStyle">
          <h3
            v-for="(round, roundIndex) in rounds"
            :key="`h-${round.round ?? roundIndex}`"
            class="rh"
            :style="headerCell(roundIndex, rounds.length)"
          >
            {{ round.title }}
            <small v-if="roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).text">{{ roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).text }}</small>
            <span v-else-if="roundNote(columnSlots(slotsOf(round), roundIndex === rounds.length - 1)).live" class="st live">идёт</span>
          </h3>
          <h3 class="rh" :style="champHeaderStyle">Чемпион</h3>

          <template v-for="(round, roundIndex) in rounds" :key="`c-${round.round ?? roundIndex}`">
            <div
              v-for="slot in columnSlots(slotsOf(round), roundIndex === rounds.length - 1)"
              :key="slot.id"
              class="cell"
              :data-round="roundIndex"
              :style="matchCell(roundIndex, Number(slot.slotIndex ?? 0), rounds.length)"
            >
              <PlayoffPairCard
                :slot="viewSlot(slot)"
                :previous-title="previousTitle(roundIndex)"
                :first-round="roundIndex === 0"
                :compact="compact"
                :final="roundIndex === rounds.length - 1"
                :lit="lit"
                :can-hover="canHover"
                :logo="logo"
                @hover="light"
                @focus="lightSlot(slot)"
                @blur="clearLight"
              />
            </div>
          </template>

          <template v-for="(round, roundIndex) in rounds.slice(0, -1)" :key="`k-${round.round ?? roundIndex}`">
            <div
              v-for="slot in linkSlots(slotsOf(rounds[roundIndex + 1]))"
              :key="`svg-${roundIndex}-${slot.id}`"
              class="cellc"
              :style="connectorCell(roundIndex, Number(slot.slotIndex ?? 0))"
            >
              <svg class="cn" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <path
                  :class="[connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).upperKind, { hl: pathLit([connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).upperId]) }]"
                  d="M0 25 H50 V50"
                />
                <path
                  :class="[connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).lowerKind, { hl: pathLit([connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).lowerId]) }]"
                  d="M0 75 H50 V50"
                />
                <path
                  :class="[exitKind(connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).upperKind, connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).lowerKind), { hl: pathLit([connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).upperId, connectorTeams(roundIndex, Number(slot.slotIndex ?? 0)).lowerId]) }]"
                  d="M50 50 H100"
                />
              </svg>
            </div>
          </template>

          <div v-if="rounds.length > 1" class="cellc" :style="finalLinkStyle">
            <svg class="cn" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
              <path
                :class="[branchKind(finalSlot), { hl: pathLit([champion?.id]) }]"
                d="M0 50 H100"
              />
            </svg>
          </div>

          <div class="cell champ-cell" :style="champStyle">
            <div v-if="champion" class="champ won">
              <TeamCrest :src="logo(champion.id)" :name="champion.name" :size="30" />
              <div><b>{{ champion.name }}</b><span>Чемпион</span></div>
            </div>
            <div v-else class="champ">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10v5a5 5 0 0 1-10 0V4zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3M12 14v4M8 20h8" /></svg>
              <div>
                <b>Чемпион</b>
                <span>{{ championWhen(finalSlot) ? `определится ${championWhen(finalSlot)}` : 'определится позже' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <p v-if="saveError" class="form-error">{{ saveError }}</p>
    </div>
  </div>
</template>

<style scoped>
.po { min-width: 0; max-width: 100%; }
.po-card {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 1.2rem 1.3rem 1.3rem;
  min-width: 0;
  max-width: 100%;
}
.po-card-h {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}
.po-card-h h2 { font-size: 1.0625rem; margin: 0; }
.po-hint { margin: 0; font-size: 0.8125rem; color: var(--muted); font-weight: 600; }
.po-board { min-width: 0; max-width: 100%; overflow-x: auto; }
.po-board.stack { overflow-x: visible; }
.po-board.stack .po-grid { width: 100%; }
.po-grid { display: grid; column-gap: 0; min-width: 0; }
.po-grid > * { min-width: 0; }
.cell { align-self: center; }
.bronze-board {
  display: grid;
  column-gap: 0;
  row-gap: 0.45rem;
  align-items: stretch;
}
.b-slot, .b-champ { align-self: center; min-width: 0; }
.b-link { align-self: stretch; min-width: 0; min-height: 0; }
.b-link .cn { width: 100%; height: 100%; }
.cellc { align-self: stretch; padding: 0; }
.champ-cell { align-self: center; display: grid; gap: 0.7rem; }
.rh {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  padding-bottom: 0.6rem;
  align-self: end;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--navy);
  flex-wrap: wrap;
}
.rh small {
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: none;
}
.st.live {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0.05rem 0.5rem;
  background: rgba(227, 107, 91, 0.14);
  color: #b4402f;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.cn { width: 100%; height: 100%; display: block; overflow: visible; }
.cn path {
  fill: none;
  stroke: #c9d6e3;
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
  stroke-linejoin: round;
}
.cn path.on { stroke: var(--ice); }
.cn path.pend { stroke-dasharray: 4 4; stroke: #b9c8d8; }
.cn path.hl { stroke: var(--navy); stroke-width: 3; }
.champ {
  border: 1.5px dashed rgba(0, 32, 91, 0.35);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.6);
  padding: 0.8rem;
  display: flex;
  align-items: center;
  gap: 0.7rem;
  color: var(--muted);
  font-weight: 700;
  font-size: 0.875rem;
  min-width: 0;
}
.champ svg {
  width: 30px;
  height: 30px;
  stroke: var(--navy);
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  flex: 0 0 auto;
}
.champ b {
  display: block;
  color: var(--ink);
  font-size: 0.9375rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.champ span { font-size: 0.8125rem; font-weight: 600; }
.champ.won {
  border: 2px solid var(--navy);
  background: #fff;
  color: var(--ink);
}
.po-board.compact :deep(.pr) {
  height: auto;
  min-height: 34px;
  max-height: 36px;
  overflow: hidden;
}
.po-board.compact :deep(.pf) { min-height: 26px; padding: 0.2rem 0.6rem; }
.po-board.compact :deep(.seed) { display: none; }
.seg {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  background: #eef3f8;
  border-radius: 999px;
  padding: 4px;
  gap: 4px;
}
.seg button {
  border: 0;
  background: transparent;
  text-align: center;
  padding: 0.55rem 0.2rem;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 800;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  display: flex;
  gap: 0.35rem;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  min-width: 0;
}
.seg button.on { background: #fff; color: var(--navy); box-shadow: var(--shadow); }
.dotl { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); flex: 0 0 auto; }
.swipe {
  text-align: center;
  font-size: 0.75rem;
  color: var(--muted);
  font-weight: 700;
  margin: 0.5rem 0 0.8rem;
}
.po-swipe {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  max-width: 100%;
  min-width: 0;
  touch-action: manipulation;
}
.po-swipe::-webkit-scrollbar { display: none; }
.po-panel {
  flex: 0 0 100%;
  scroll-snap-align: start;
  min-width: 0;
  max-width: 100%;
}
.pair-stack, .pair-block, .sk-stack { display: grid; gap: 0.7rem; min-width: 0; }
.how {
  list-style: none;
  margin: 0.45rem 0.2rem 0.2rem;
  padding: 0;
  display: grid;
  gap: 0.15rem;
  font-size: 0.8125rem;
  color: var(--muted);
  font-weight: 600;
}
.how b { color: var(--ink); }
.champ-block { margin-top: 0.9rem; }
.next-hint {
  margin: 0.9rem 0 0;
  padding-top: 0.9rem;
  border-top: 1px dashed var(--line-strong);
  color: var(--navy);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.msg {
  text-align: center;
  padding: 1.6rem 1rem;
  display: grid;
  gap: 0.5rem;
  justify-items: center;
}
.msg svg {
  width: 44px;
  height: 44px;
  stroke: var(--muted);
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.msg.err svg { stroke: var(--danger); }
.msg b { font-size: 1.0625rem; color: var(--navy); }
.msg p { margin: 0; font-size: 0.875rem; color: var(--muted); font-weight: 600; max-width: 30ch; }
.po-action {
  margin-top: 0.4rem;
  padding: 0.6rem 1.2rem;
  border-radius: 999px;
  background: var(--navy);
  color: #fff;
  font-weight: 800;
  font-size: 0.8125rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border: 0;
  cursor: pointer;
}
.sk { border-radius: 14px; background: #fff; border: 1px solid var(--line); overflow: hidden; }
.sk i, .sk-col > i.rh {
  display: block;
  height: 20px;
  border-radius: 8px;
  margin: 0.9rem;
  background: linear-gradient(90deg, #e8eef5 25%, #f4f7fb 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: po-shimmer 1.4s infinite;
}
.sk i.w { width: 60%; }
.sk i.n { width: 40%; height: 14px; }
.sk-desk { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.8rem; }
.sk-col { display: grid; gap: 0.7rem; align-content: start; min-width: 0; }
.sk-col > i.rh { height: 14px; width: 50%; margin: 0 0 0.2rem; }
@keyframes po-shimmer { to { background-position: -200% 0; } }
@media (prefers-reduced-motion: reduce) {
  .sk i, .sk-col > i.rh { animation: none; }
  .po-swipe { scroll-behavior: auto; }
}
.bracket.admin {
  display: flex;
  gap: 0.75rem;
  overflow-x: auto;
  max-width: 100%;
  align-items: flex-start;
  padding-bottom: 0.35rem;
}
.round {
  flex: 0 0 18rem;
  width: min(100%, 18rem);
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.admin-slot { min-width: 0; display: grid; gap: 0.55rem; }
.edit { display: grid; gap: 0.45rem; }
.edit input { min-width: 0; max-width: 100%; width: 100%; }
@media (max-width: 720px) {
  .po-card { padding: 1rem 0.85rem 1.1rem; }
  .po-hint { display: none; }
  .bracket.admin { flex-direction: column; }
  .round { flex-basis: auto; width: 100%; }
}
</style>
