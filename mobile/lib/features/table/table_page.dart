import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/brand_mark.dart';
import '../../widgets/marks.dart' hide PlayerPhoto;
import '../../widgets/match_row.dart';
import '../../widgets/player_photo.dart';

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
    _tabs = TabController(length: 6, vsync: this);
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
              const BrandMark(size: 48, radius: 12),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
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
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: const [
            Tab(text: 'ТАБЛИЦА'),
            Tab(text: 'РЕЗУЛЬТАТЫ'),
            Tab(text: 'БОМБАРДИРЫ'),
            Tab(text: 'АССИСТЕНТЫ'),
            Tab(text: 'ВРАТАРИ'),
            Tab(text: 'ИГРОКИ'),
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
              _Assists(store: store),
              _Keepers(store: store),
              _Players(store: store),
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
    final tables = store.standingTables.where((table) => table.rows.isNotEmpty).toList();
    if (tables.isEmpty) {
      return ListView(children: const [EmptyHint(title: 'Таблица пустая')]);
    }
    return ListView(
      children: [
        for (final table in tables)
          Container(
            margin: const EdgeInsets.fromLTRB(12, 10, 12, 8),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (table.name != null && table.name!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                    child: Text(
                      table.name!,
                      style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, letterSpacing: 0.4),
                    ),
                  ),
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
                for (var i = 0; i < table.rows.length; i++)
                  InkWell(
                    onTap: () => context.push('/teams/${table.rows[i].teamId}'),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      child: Row(
                        children: [
                          _Rank(index: i),
                          const SizedBox(width: 6),
                          TeamMark(name: table.rows[i].teamName, logoUrl: store.teamLogo(table.rows[i].teamId), size: 18),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(table.rows[i].teamName, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                          ),
                          SizedBox(width: 28, child: Text('${table.rows[i].played}', textAlign: TextAlign.center)),
                          SizedBox(width: 48, child: Text('${table.rows[i].goalsFor}:${table.rows[i].goalsAgainst}', textAlign: TextAlign.center)),
                          SizedBox(
                            width: 28,
                            child: Text('${table.rows[i].points}', textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800)),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        const SizedBox(height: 8),
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
        _StatCard(title: 'Бомбардиры', rows: store.scorers),
      ],
    );
  }
}

class _Keepers extends StatelessWidget {
  const _Keepers({required this.store});
  final LeagueStore store;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        _StatCard(title: 'Вратари', rows: store.keepers, empty: 'Сухих матчей ещё нет', showGames: true),
      ],
    );
  }
}

class _Assists extends StatelessWidget {
  const _Assists({required this.store});
  final LeagueStore store;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        _StatCard(title: 'Ассистенты', rows: store.assists),
      ],
    );
  }
}

class _Players extends StatefulWidget {
  const _Players({required this.store});
  final LeagueStore store;

  @override
  State<_Players> createState() => _PlayersState();
}

class _PlayersState extends State<_Players> {
  final _query = TextEditingController();
  List<PlayerBrief> _results = [];
  Timer? _timer;
  bool _pending = false;
  bool _searched = false;

  @override
  void dispose() {
    _timer?.cancel();
    _query.dispose();
    super.dispose();
  }

  void _schedule(String value) {
    _timer?.cancel();
    _timer = Timer(const Duration(milliseconds: 250), () => _search(value));
  }

  Future<void> _search(String value) async {
    final q = value.trim();
    if (q.length < 2) {
      setState(() {
        _results = [];
        _searched = false;
        _pending = false;
      });
      return;
    }
    setState(() => _pending = true);
    try {
      final page = await widget.store.api.get('/players', query: {'q': q, 'size': '8'});
      final items = page is Map
          ? ((page['content'] as List?) ?? const [])
              .whereType<Map>()
              .map((item) => PlayerBrief.fromJson(Map<String, dynamic>.from(item)))
              .toList()
          : <PlayerBrief>[];
      if (!mounted) return;
      setState(() {
        _results = items;
        _searched = true;
        _pending = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _pending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        TextField(
          controller: _query,
          decoration: const InputDecoration(
            hintText: 'Начните вводить фамилию',
            prefixIcon: Icon(Icons.search),
          ),
          onChanged: _schedule,
        ),
        const SizedBox(height: 12),
        if (_query.text.trim().length < 2)
          const Text('Введите хотя бы 2 буквы', style: TextStyle(color: AppColors.muted))
        else if (_pending)
          const Text('Ищем…', style: TextStyle(color: AppColors.muted))
        else if (_searched && _results.isEmpty)
          const Text('Никого не нашли', style: TextStyle(color: AppColors.muted))
        else
          for (final player in _results)
            ListTile(
              leading: PlayerPhoto(url: widget.store.api.resolveMedia(player.avatarUrl), name: player.title, size: 36),
              title: Text(player.title, style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.navy)),
              subtitle: Text(
                '${player.position?.isNotEmpty == true ? player.position : 'Игрок'} · №${player.jerseyNumber ?? '—'}',
                style: const TextStyle(color: AppColors.muted),
              ),
              onTap: () => context.push('/players/${player.id}'),
            ),
      ],
    );
  }
}

class _StatCard extends StatefulWidget {
  const _StatCard({required this.title, required this.rows, this.empty = 'Пока пусто', this.showGames = false});
  final String title;
  final List<PlayerStat> rows;
  final String empty;
  final bool showGames;

  @override
  State<_StatCard> createState() => _StatCardState();
}

class _StatCardState extends State<_StatCard> {
  static const top = 10;
  bool expanded = false;

  @override
  Widget build(BuildContext context) {
    final rows = widget.rows;
    final visible = expanded || rows.length <= top ? rows : rows.sublist(0, top);
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(widget.title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))),
              if (!expanded && rows.length > top)
                Text('Топ-$top из ${rows.length}', style: const TextStyle(color: AppColors.muted, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 8),
          if (rows.isEmpty)
            Text(widget.empty, style: const TextStyle(color: AppColors.muted))
          else ...[
            if (widget.showGames)
              const Padding(
                padding: EdgeInsets.only(bottom: 4),
                child: Row(
                  children: [
                    Expanded(child: Text('Игрок', style: _head)),
                    SizedBox(width: 56, child: Text('Сухие', textAlign: TextAlign.end, style: _head)),
                    SizedBox(width: 48, child: Text('Игры', textAlign: TextAlign.end, style: _head)),
                  ],
                ),
              ),
            for (final row in visible)
              InkWell(
                onTap: () => context.push('/players/${row.playerId}'),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    children: [
                      Expanded(child: Text(row.title)),
                      if (widget.showGames) ...[
                        SizedBox(width: 56, child: Text('${row.value}', textAlign: TextAlign.end, style: const TextStyle(fontWeight: FontWeight.w800))),
                        SizedBox(width: 48, child: Text('${row.appearances}', textAlign: TextAlign.end)),
                      ] else
                        Text('${row.value}', style: const TextStyle(fontWeight: FontWeight.w800)),
                    ],
                  ),
                ),
              ),
          ],
          if (rows.length > top)
            TextButton(
              onPressed: () => setState(() => expanded = !expanded),
              child: Text(expanded ? 'Свернуть' : 'Показать всех'),
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
