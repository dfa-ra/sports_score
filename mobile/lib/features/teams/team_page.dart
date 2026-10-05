import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/favorites_store.dart';
import '../../state/league_store.dart';
import '../../widgets/marks.dart';
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
  String? captainId;
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
      final data = await store.teamMembers(widget.teamId);
      final captain = await store.captainOf(widget.teamId);
      if (mounted) {
        setState(() {
          members = data;
          captainId = captain;
        });
      }
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
                TeamMark(name: name, logoUrl: store.teamLogo(widget.teamId), size: 56),
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
                _Squad(members: members, captainId: captainId),
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
  const _Squad({required this.members, this.captainId});
  final List<TeamMember> members;
  final String? captainId;

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
              child: Text(initials(member.title), style: const TextStyle(color: AppColors.navy, fontWeight: FontWeight.w800, fontSize: 12)),
            ),
            title: Text(member.title, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text([if (member.jerseyNumber != null) '#${member.jerseyNumber}', member.position].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
            trailing: member.playerId == captainId
                ? Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: const Color(0x2E4CB4E5),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: const Text(
                      'Капитан',
                      style: TextStyle(color: AppColors.navy, fontSize: 11, fontWeight: FontWeight.w700),
                    ),
                  )
                : null,
          ),
      ],
    );
  }
}
