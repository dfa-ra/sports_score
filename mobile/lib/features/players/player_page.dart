import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/marks.dart';
import '../../widgets/match_row.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({super.key, required this.playerId});
  final String playerId;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  PlayerCard? card;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final data = await context.read<LeagueStore>().api.get('/players/${widget.playerId}/card');
      if (data is Map && mounted) {
        setState(() => card = PlayerCard.fromJson(Map<String, dynamic>.from(data)));
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator(color: AppColors.ice)));
    }
    final current = card;
    if (current == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Игрок')),
        body: const Center(child: EmptyHint(title: 'Карточка не найдена')),
      );
    }
    final store = context.watch<LeagueStore>();
    final goals = current.statistics['goals'] ?? current.statistics['Goals'];
    final assists = current.statistics['assists'] ?? current.statistics['Assists'];
    final photo = store.api.resolveMedia(current.avatarUrl);
    return Scaffold(
      appBar: AppBar(title: Text(current.displayName)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              PlayerPhoto(name: current.displayName, photoUrl: photo, size: 64),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(current.displayName, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.navy)),
                    if (current.teamName != null && current.teamName!.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          TeamMark(
                            name: current.teamName!,
                            logoUrl: store.teamLogo(current.teamId) ?? store.api.resolveMedia(current.teamLogoUrl),
                            size: 18,
                          ),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(current.teamName!, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: AppColors.muted)),
                          ),
                        ],
                      ),
                    ],
                    Text(
                      [if (current.jerseyNumber != null) '#${current.jerseyNumber}', current.position].whereType<String>().where((s) => s.isNotEmpty).join(' · '),
                      style: const TextStyle(color: AppColors.muted),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              _Stat(label: 'Голы', value: '${goals ?? 0}'),
              const SizedBox(width: 8),
              _Stat(label: 'Пас', value: '${assists ?? 0}'),
            ],
          ),
          const SizedBox(height: 16),
          const Text('Матчи', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
          const SizedBox(height: 8),
          if (current.history.isEmpty)
            const EmptyHint(title: 'Пока нет сыгранных матчей')
          else
            for (final row in current.history)
              ListTile(
                onTap: () => context.push('/matches/${row.matchId}'),
                title: Text(row.opponentName, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${row.homeScore}:${row.awayScore} · ${longKickoff(row.scheduledAt)}'),
                trailing: Text(
                  [if (row.goals > 0) '${row.goals}Г', if (row.assists > 0) '${row.assists}П'].join(' '),
                  style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy),
                ),
              ),
        ],
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.line)),
        child: Column(
          children: [
            Text(value, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy)),
            Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}
