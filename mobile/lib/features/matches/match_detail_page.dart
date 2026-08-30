import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class MatchDetailPage extends StatefulWidget {
  const MatchDetailPage({super.key, required this.matchId});
  final String matchId;

  @override
  State<MatchDetailPage> createState() => _MatchDetailPageState();
}

class _MatchDetailPageState extends State<MatchDetailPage> {
  List<MatchEvent> events = [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final store = context.read<LeagueStore>();
      final data = await store.eventsFor(widget.matchId);
      if (mounted) setState(() => events = data);
    } catch (_) {
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    LeagueMatch? match;
    for (final item in store.matches) {
      if (item.id == widget.matchId) match = item;
    }
    if (match == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Матч')),
        body: const Center(child: EmptyHint(title: 'Матч не найден')),
      );
    }
    final home = store.teamName(match.homeTeamId);
    final away = store.teamName(match.awayTeamId);
    final visible = events.where((e) => {'GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION'}.contains(e.eventType)).toList()
      ..sort((a, b) => (a.gameTime ?? 0).compareTo(b.gameTime ?? 0));

    return Scaffold(
      appBar: AppBar(title: Text(store.tournamentNames[match.tournamentId] ?? 'KRONBARS')),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
            child: Row(
              children: [
                Expanded(child: _Club(name: home)),
                Column(
                  children: [
                    Text(longKickoff(match.scheduledAt), style: const TextStyle(color: AppColors.muted, fontSize: 12)),
                    Text('${match.homeScore} - ${match.awayScore}', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: AppColors.navy)),
                    Text(matchStateLabel(match.status).toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 12, color: AppColors.muted)),
                  ],
                ),
                Expanded(child: _Club(name: away)),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
          if (!loading && visible.isEmpty) const EmptyHint(title: 'Пока ни гола, ни карточки'),
          if (visible.isNotEmpty)
            Container(
              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
              child: Column(
                children: [
                  for (final event in visible)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      child: Row(
                        children: [
                          if (event.teamId == match.homeTeamId) ...[
                            Expanded(child: Text(event.label, style: const TextStyle(fontWeight: FontWeight.w800))),
                            _Mark(type: event.eventType),
                            const SizedBox(width: 8),
                            Text("${eventMinute(event.gameTime)}'"),
                          ] else ...[
                            Text("${eventMinute(event.gameTime)}'"),
                            const SizedBox(width: 8),
                            _Mark(type: event.eventType),
                            Expanded(child: Text(event.label, textAlign: TextAlign.right, style: const TextStyle(fontWeight: FontWeight.w800))),
                          ],
                        ],
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _Club extends StatelessWidget {
  const _Club({required this.name});
  final String name;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 44,
          height: 44,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10), boxShadow: const [BoxShadow(color: Color(0x1400205B), blurRadius: 6)]),
          child: Text(initials(name), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
        ),
        const SizedBox(height: 6),
        Text(name, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
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
