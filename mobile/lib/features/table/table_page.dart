import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/marks.dart';
import '../../widgets/match_row.dart';

class TablePage extends StatefulWidget {
  const TablePage({super.key});

  @override
  State<TablePage> createState() => _TablePageState();
}

class _TablePageState extends State<TablePage> with SingleTickerProviderStateMixin {
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: const [BoxShadow(color: Color(0x1400205B), blurRadius: 8)],
                ),
                child: const Text('KB', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('KRONBARS', style: TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.8)),
                    Text(store.tournamentName ?? 'Таблица', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: AppColors.navy)),
                    if (store.tournaments.isNotEmpty)
                      DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          isExpanded: true,
                          value: store.tournamentId,
                          items: [
                            for (final item in store.tournaments)
                              DropdownMenuItem(value: item.id, child: Text(item.name, overflow: TextOverflow.ellipsis)),
                          ],
                          onChanged: (value) {
                            if (value != null) store.selectTournament(value);
                          },
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        TabBar(
          controller: _tabs,
          tabs: const [
            Tab(text: 'ТАБЛИЦА'),
            Tab(text: 'РЕЗУЛЬТАТЫ'),
            Tab(text: 'БОМБАРДИРЫ'),
          ],
        ),
        if (store.loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
        Expanded(
          child: TabBarView(
            controller: _tabs,
            children: [
              _Standings(store: store),
              _Results(store: store),
              _Scorers(store: store),
            ],
          ),
        ),
      ],
    );
  }
}

class _Standings extends StatelessWidget {
  const _Standings({required this.store});
  final LeagueStore store;

  @override
  Widget build(BuildContext context) {
    if (store.standings.isEmpty) {
      return ListView(children: const [EmptyHint(title: 'Таблица пустая')]);
    }
    return ListView(
      children: [
        Container(
          margin: const EdgeInsets.fromLTRB(12, 10, 12, 16),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
          child: Column(
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                child: Row(
                  children: [
                    SizedBox(width: 28, child: Text('#', style: _head)),
                    Expanded(child: Text('КОМАНДА', style: _head)),
                    SizedBox(width: 28, child: Text('И', style: _head, textAlign: TextAlign.center)),
                    SizedBox(width: 48, child: Text('Г', style: _head, textAlign: TextAlign.center)),
                    SizedBox(width: 28, child: Text('О', style: _head, textAlign: TextAlign.center)),
                  ],
                ),
              ),
              for (var i = 0; i < store.standings.length; i++)
                InkWell(
                  onTap: () => context.push('/teams/${store.standings[i].teamId}'),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    child: Row(
                      children: [
                        _Rank(index: i),
                        const SizedBox(width: 6),
                        TeamMark(name: store.standings[i].teamName, logoUrl: store.teamLogo(store.standings[i].teamId), size: 18),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(store.standings[i].teamName, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                        ),
                        SizedBox(width: 28, child: Text('${store.standings[i].played}', textAlign: TextAlign.center)),
                        SizedBox(width: 48, child: Text('${store.standings[i].goalsFor}:${store.standings[i].goalsAgainst}', textAlign: TextAlign.center)),
                        SizedBox(
                          width: 28,
                          child: Text('${store.standings[i].points}', textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800)),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Results extends StatelessWidget {
  const _Results({required this.store});
  final LeagueStore store;

  @override
  Widget build(BuildContext context) {
    final rows = store.tournamentResults();
    if (rows.isEmpty) {
      return ListView(children: const [EmptyHint(title: 'Сыгранных матчей ещё нет')]);
    }
    return ListView(
      children: [
        for (final match in rows)
          MatchRow(
            match: match,
            homeName: store.teamName(match.homeTeamId),
            awayName: store.teamName(match.awayTeamId),
          ),
      ],
    );
  }
}

class _Scorers extends StatelessWidget {
  const _Scorers({required this.store});
  final LeagueStore store;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        _StatCard(title: 'Голы', rows: store.scorers),
        _StatCard(title: 'Передачи', rows: store.assists),
        _StatCard(title: 'Сухие', rows: store.keepers),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.title, required this.rows});
  final String title;
  final List<PlayerStat> rows;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
          const SizedBox(height: 8),
          if (rows.isEmpty)
            const Text('Пока пусто', style: TextStyle(color: AppColors.muted))
          else
            for (final row in rows)
              InkWell(
                onTap: () => context.push('/players/${row.playerId}'),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    children: [
                      Expanded(child: Text(row.displayName)),
                      Text('${row.value}', style: const TextStyle(fontWeight: FontWeight.w800)),
                    ],
                  ),
                ),
              ),
        ],
      ),
    );
  }
}

class _Rank extends StatelessWidget {
  const _Rank({required this.index});
  final int index;

  @override
  Widget build(BuildContext context) {
    final ice = index < 2;
    final navy = index < 4;
    return Container(
      width: 22,
      height: 22,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: ice ? AppColors.ice : navy ? AppColors.navy : Colors.transparent,
      ),
      child: Text(
        '${index + 1}',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w800,
          color: ice ? AppColors.navy : navy ? Colors.white : AppColors.muted,
        ),
      ),
    );
  }
}

const _head = TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.4);
