import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';
import '../../state/favorites_store.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class MatchDetailPage extends StatefulWidget {
  const MatchDetailPage({super.key, required this.matchId});
  final String matchId;

  @override
  State<MatchDetailPage> createState() => _MatchDetailPageState();
}

class _MatchDetailPageState extends State<MatchDetailPage> with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  LeagueMatch? match;
  List<MatchEvent> events = [];
  MatchLineups lineups = MatchLineups();
  List<LeagueMatch> homeForm = [];
  List<LeagueMatch> awayForm = [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    _load();
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final store = context.read<LeagueStore>();
    try {
      final loaded = await store.matchById(widget.matchId);
      final ev = loaded == null ? <MatchEvent>[] : await store.eventsFor(widget.matchId);
      MatchLineups lu = MatchLineups();
      var hf = <LeagueMatch>[];
      var af = <LeagueMatch>[];
      if (loaded != null) {
        try {
          lu = await store.lineupsFor(widget.matchId);
        } catch (_) {}
        try {
          final forms = await Future.wait([
            store.teamForm(loaded.homeTeamId),
            store.teamForm(loaded.awayTeamId),
          ]);
          hf = forms[0];
          af = forms[1];
        } catch (_) {}
      }
      if (!mounted) return;
      setState(() {
        match = loaded;
        events = ev;
        lineups = lu;
        homeForm = hf;
        awayForm = af;
      });
    } catch (_) {
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  List<_PeriodBlock> get _blocks {
    final current = match;
    if (current == null) return const [];
    const visible = {'GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION'};
    final chrono = events.where((e) => visible.contains(e.eventType)).toList()
      ..sort((a, b) => (a.gameTime ?? 0).compareTo(b.gameTime ?? 0));
    var home = 0;
    var away = 0;
    final map = <int, _PeriodBlock>{};
    for (final ev in chrono) {
      if (ev.eventType == 'GOAL' || ev.eventType == 'OWN_GOAL') {
        if (ev.teamId == current.homeTeamId) {
          home += 1;
        } else {
          away += 1;
        }
      }
      final period = ev.period ?? 1;
      final block = map.putIfAbsent(
        period,
        () => _PeriodBlock(
          period: period,
          label: periodLabel(period, sportCode: current.sportCode, periodCount: current.periodCount),
        ),
      );
      block.items.add(_TimelineItem(
        event: ev,
        home: ev.teamId == current.homeTeamId,
        scoreline: ev.eventType == 'GOAL' || ev.eventType == 'OWN_GOAL' ? '$home-$away' : null,
      ));
      block.score = '$home-$away';
    }
    return map.values.toList();
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    final fav = context.watch<FavoritesStore>();
    final auth = context.watch<AuthController>();
    final current = match ?? store.findMatch(widget.matchId);
    if (loading && current == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator(color: AppColors.ice)));
    }
    if (current == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Матч')),
        body: const Center(child: EmptyHint(title: 'Матч не найден')),
      );
    }
    final home = store.teamName(current.homeTeamId);
    final away = store.teamName(current.awayTeamId);

    return Scaffold(
      appBar: AppBar(title: Text(store.tournamentNames[current.tournamentId] ?? 'KRONBARS')),
      body: Column(
        children: [
          _LeagueBar(title: store.tournamentNames[current.tournamentId] ?? 'KRONBARS', onTap: () => context.go('/table')),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
            child: Container(
              padding: const EdgeInsets.fromLTRB(8, 12, 8, 12),
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
              child: Row(
                children: [
                  Expanded(
                    child: _Club(
                      name: home,
                      starred: fav.hasTeam(current.homeTeamId),
                      onStar: () => fav.toggleTeam(current.homeTeamId),
                      onTap: () => context.push('/teams/${current.homeTeamId}'),
                    ),
                  ),
                  Column(
                    children: [
                      Text(longKickoff(current.scheduledAt), style: const TextStyle(color: AppColors.muted, fontSize: 12)),
                      Text('${current.homeScore} - ${current.awayScore}', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: AppColors.navy)),
                      Text(matchStateLabel(current.status).toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 12, color: AppColors.muted)),
                      if (current.isLive)
                        Text(
                          '${formatClock(current.gameTimeSeconds)} · ${periodLabel(current.period, sportCode: current.sportCode, periodCount: current.periodCount)}',
                          style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800, fontSize: 12),
                        ),
                    ],
                  ),
                  Expanded(
                    child: _Club(
                      name: away,
                      starred: fav.hasTeam(current.awayTeamId),
                      onStar: () => fav.toggleTeam(current.awayTeamId),
                      onTap: () => context.push('/teams/${current.awayTeamId}'),
                    ),
                  ),
                ],
              ),
            ),
          ),
          TabBar(
            controller: _tabs,
            tabs: const [
              Tab(text: 'ОБЗОР'),
              Tab(text: 'СОСТАВЫ'),
              Tab(text: 'ПРОТОКОЛ'),
            ],
          ),
          if (loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
          Expanded(
            child: TabBarView(
              controller: _tabs,
              children: [
                _Overview(
                  blocks: _blocks,
                  homeForm: homeForm,
                  awayForm: awayForm,
                  canOfficiate: auth.canOfficiate,
                ),
                _Lineups(lineups: lineups),
                _Protocol(events: events, match: current),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _LeagueBar extends StatelessWidget {
  const _LeagueBar({required this.title, required this.onTap});
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: const Color(0xFFEEF4F9),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Text(title.toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13, color: AppColors.navy)),
              ),
              const Icon(Icons.chevron_right, color: AppColors.muted, size: 18),
            ],
          ),
        ),
      ),
    );
  }
}

class _Club extends StatelessWidget {
  const _Club({required this.name, required this.starred, required this.onStar, required this.onTap});
  final String name;
  final bool starred;
  final VoidCallback onStar;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        IconButton(
          tooltip: starred ? 'Убрать команду' : 'Команда в избранное',
          onPressed: onStar,
          icon: Icon(starred ? Icons.star : Icons.star_border, color: starred ? AppColors.ice : const Color(0xFFC5CED8), size: 18),
        ),
        InkWell(
          onTap: onTap,
          child: Column(
            children: [
              Container(
                width: 44,
                height: 44,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10), boxShadow: const [BoxShadow(color: Color(0x1400205B), blurRadius: 6)]),
                child: Text(initials(name), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
              ),
              const SizedBox(height: 6),
              Text(name, textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
            ],
          ),
        ),
      ],
    );
  }
}

class _Overview extends StatelessWidget {
  const _Overview({
    required this.blocks,
    required this.homeForm,
    required this.awayForm,
    required this.canOfficiate,
  });

  final List<_PeriodBlock> blocks;
  final List<LeagueMatch> homeForm;
  final List<LeagueMatch> awayForm;
  final bool canOfficiate;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 24),
      children: [
        Container(
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
          child: blocks.isEmpty
              ? const EmptyHint(title: 'Пока ни гола, ни карточки')
              : Column(
                  children: [
                    for (final block in blocks) ...[
                      Container(
                        color: const Color(0xFFF4F7FB),
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        child: Row(
                          children: [
                            Text(block.label.toUpperCase(), style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: AppColors.muted)),
                            const Spacer(),
                            Text(block.score, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: AppColors.muted)),
                          ],
                        ),
                      ),
                      for (final item in block.items) _EventRow(item: item),
                    ],
                  ],
                ),
        ),
        const SizedBox(height: 12),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: _FormCard(title: 'Форма хозяев', rows: homeForm)),
            const SizedBox(width: 8),
            Expanded(child: _FormCard(title: 'Форма гостей', rows: awayForm)),
          ],
        ),
        if (canOfficiate) ...[
          const SizedBox(height: 12),
          FilledButton(onPressed: () => context.push('/referee'), child: const Text('Открыть пульт')),
        ],
      ],
    );
  }
}

class _FormCard extends StatelessWidget {
  const _FormCard({required this.title, required this.rows});
  final String title;
  final List<LeagueMatch> rows;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
          const SizedBox(height: 6),
          if (rows.isEmpty)
            const Text('Ещё нет пяти матчей.', style: TextStyle(color: AppColors.muted, fontSize: 12))
          else
            for (final row in rows)
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Text('${row.homeScore}:${row.awayScore} · ${longKickoff(row.scheduledAt)}', style: const TextStyle(color: AppColors.muted, fontSize: 12)),
              ),
        ],
      ),
    );
  }
}

class _EventRow extends StatelessWidget {
  const _EventRow({required this.item});
  final _TimelineItem item;

  @override
  Widget build(BuildContext context) {
    final mark = _Mark(type: item.event.eventType);
    final minute = Text("${eventMinute(item.event.gameTime)}'", style: const TextStyle(color: AppColors.muted));
    final who = Column(
      crossAxisAlignment: item.home ? CrossAxisAlignment.start : CrossAxisAlignment.end,
      children: [
        Text(item.event.label, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
        if (item.scoreline != null) Text(item.scoreline!, style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
      ],
    );
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Row(
        children: item.home
            ? [Expanded(child: who), mark, const SizedBox(width: 8), minute]
            : [minute, const SizedBox(width: 8), mark, Expanded(child: who)],
      ),
    );
  }
}

class _Lineups extends StatelessWidget {
  const _Lineups({required this.lineups});
  final MatchLineups lineups;

  @override
  Widget build(BuildContext context) {
    if (lineups.home == null && lineups.away == null) {
      return ListView(children: const [EmptyHint(title: 'Составы ещё не поданы')]);
    }
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (lineups.home != null) _LineupCard(side: lineups.home!),
        if (lineups.away != null) _LineupCard(side: lineups.away!),
      ],
    );
  }
}

class _LineupCard extends StatelessWidget {
  const _LineupCard({required this.side});
  final TeamLineup side;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(side.teamName, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
          Text(
            side.confirmed ? 'Стартовый состав записан' : 'Капитан ещё не написал, кто выходит с первой минуты',
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          const SizedBox(height: 10),
          const Text('Основа', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 12, color: AppColors.muted)),
          if (side.starters.isEmpty)
            const Padding(padding: EdgeInsets.symmetric(vertical: 6), child: Text('Пока все в заявке, без первых номеров.', style: TextStyle(color: AppColors.muted)))
          else
            for (final p in side.starters) _PlayerLine(player: p),
          const SizedBox(height: 8),
          const Text('Скамейка', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 12, color: AppColors.muted)),
          if (side.bench.isEmpty)
            const Padding(padding: EdgeInsets.symmetric(vertical: 6), child: Text('Пусто.', style: TextStyle(color: AppColors.muted)))
          else
            for (final p in side.bench) _PlayerLine(player: p),
        ],
      ),
    );
  }
}

class _PlayerLine extends StatelessWidget {
  const _PlayerLine({required this.player});
  final LineupPlayer player;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          SizedBox(width: 28, child: Text('${player.jerseyNumber ?? '—'}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.muted))),
          Expanded(child: Text(player.name, style: const TextStyle(fontWeight: FontWeight.w600))),
          Text(player.position ?? '', style: const TextStyle(color: AppColors.muted, fontSize: 12)),
        ],
      ),
    );
  }
}

class _Protocol extends StatelessWidget {
  const _Protocol({required this.events, required this.match});
  final List<MatchEvent> events;
  final LeagueMatch match;

  @override
  Widget build(BuildContext context) {
    final rows = [...events].reversed.toList();
    if (rows.isEmpty) {
      return ListView(children: const [EmptyHint(title: 'Протокол пока пустой')]);
    }
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        for (final ev in rows)
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: const Color(0xFFF6F9FC), borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 56,
                  child: Text(formatClock(ev.gameTime), style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800)),
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(eventLabel(ev.eventType), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                      Text(
                        [
                          if (playerTag(ev.playerName, ev.playerJersey).isNotEmpty) playerTag(ev.playerName, ev.playerJersey),
                          if (ev.eventType == 'GOAL' && ev.secondaryPlayerName != null) 'пас ${playerTag(ev.secondaryPlayerName, ev.secondaryPlayerJersey)}',
                          periodLabel(ev.period, sportCode: match.sportCode, periodCount: match.periodCount),
                        ].join(' · '),
                        style: const TextStyle(color: AppColors.muted, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _Mark extends StatelessWidget {
  const _Mark({required this.type});
  final String type;

  @override
  Widget build(BuildContext context) {
    final color = switch (type) {
      'YELLOW_CARD' => const Color(0xFFF5C400),
      'RED_CARD' => AppColors.danger,
      'SUBSTITUTION' => AppColors.ice,
      _ => AppColors.navy,
    };
    return Container(
      width: 12,
      height: 12,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(type.contains('CARD') ? 2 : 6),
      ),
    );
  }
}

class _PeriodBlock {
  _PeriodBlock({required this.period, required this.label});
  final int period;
  final String label;
  String score = '0-0';
  final List<_TimelineItem> items = [];
}

class _TimelineItem {
  _TimelineItem({required this.event, required this.home, this.scoreline});
  final MatchEvent event;
  final bool home;
  final String? scoreline;
}
