import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/favorites_store.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class TeamPage extends StatefulWidget {
  const TeamPage({super.key, required this.teamId});
  final String teamId;

  @override
  State<TeamPage> createState() => _TeamPageState();
}

class _TeamPageState extends State<TeamPage> with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  List<TeamMember> members = [];
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
    try {
      final data = await context.read<LeagueStore>().teamMembers(widget.teamId);
      if (mounted) setState(() => members = data);
    } catch (_) {
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    final fav = context.watch<FavoritesStore>();
    final name = store.teamName(widget.teamId);
    final games = store.teamMatches(widget.teamId);
    final played = games.where((m) => m.isFinished).toList();
    final upcoming = games.where((m) => !m.isFinished).toList();

    return Scaffold(
      appBar: AppBar(title: Text(name)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), boxShadow: const [BoxShadow(color: Color(0x1400205B), blurRadius: 8)]),
                  child: Text(initials(name), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, fontSize: 18)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('КОМАНДА', style: TextStyle(color: AppColors.ice, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.8)),
                      Text(name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.navy)),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: fav.hasTeam(widget.teamId) ? 'Убрать из избранного' : 'В избранное',
                  onPressed: () => fav.toggleTeam(widget.teamId),
                  icon: Icon(fav.hasTeam(widget.teamId) ? Icons.star : Icons.star_border, color: fav.hasTeam(widget.teamId) ? AppColors.ice : const Color(0xFFC5CED8)),
                ),
              ],
            ),
          ),
          TabBar(
            controller: _tabs,
            tabs: const [
              Tab(text: 'РЕЗУЛЬТАТЫ'),
              Tab(text: 'КАЛЕНДАРЬ'),
              Tab(text: 'СОСТАВ'),
            ],
          ),
          if (loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
          Expanded(
            child: TabBarView(
              controller: _tabs,
              children: [
                _MatchList(rows: played, store: store, teamId: widget.teamId, empty: 'Сыгранных матчей ещё нет'),
                _MatchList(rows: upcoming, store: store, teamId: widget.teamId, empty: 'Ближайших игр нет'),
                _Squad(members: members),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MatchList extends StatelessWidget {
  const _MatchList({required this.rows, required this.store, required this.teamId, required this.empty});
  final List<LeagueMatch> rows;
  final LeagueStore store;
  final String teamId;
  final String empty;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) return ListView(children: [EmptyHint(title: empty)]);
    return ListView(
      children: [
        for (final match in rows)
          MatchRow(
            match: match,
            homeName: store.teamName(match.homeTeamId),
            awayName: store.teamName(match.awayTeamId),
            highlightTeamId: teamId,
          ),
      ],
    );
  }
}

class _Squad extends StatelessWidget {
  const _Squad({required this.members});
  final List<TeamMember> members;

  @override
  Widget build(BuildContext context) {
    if (members.isEmpty) {
      return ListView(children: const [EmptyHint(title: 'Заявка ещё пустая')]);
    }
    return ListView(
      children: [
        for (final member in members)
          ListTile(
            onTap: () => context.push('/players/${member.playerId}'),
            leading: CircleAvatar(
              backgroundColor: const Color(0x294CB4E5),
              child: Text(initials(member.displayName), style: const TextStyle(color: AppColors.navy, fontWeight: FontWeight.w800, fontSize: 12)),
            ),
            title: Text(member.displayName, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text([if (member.jerseyNumber != null) '#${member.jerseyNumber}', member.position].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
          ),
      ],
    );
  }
}
