import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class RefereeMatchesPage extends StatefulWidget {
  const RefereeMatchesPage({super.key});

  @override
  State<RefereeMatchesPage> createState() => _RefereeMatchesPageState();
}

class _RefereeMatchesPageState extends State<RefereeMatchesPage> {
  List<LeagueMatch> matches = [];
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final data = await context.read<AuthController>().api.get('/referee/matches');
      final rows = ((data as List?) ?? const [])
          .whereType<Map>()
          .map((item) => LeagueMatch.fromJson(Map<String, dynamic>.from(item)))
          .toList()
        ..sort((a, b) {
          int rank(String status) => status == 'LIVE' || status == 'PAUSED'
              ? 0
              : status == 'SCHEDULED'
                  ? 1
                  : 2;
          final byStatus = rank(a.status) - rank(b.status);
          if (byStatus != 0) return byStatus;
          return (a.scheduledAt ?? DateTime(2100)).compareTo(b.scheduledAt ?? DateTime(2100));
        });
      if (mounted) setState(() => matches = rows);
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException ? e.message : 'Назначения не загрузились.');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    return Scaffold(
      appBar: AppBar(title: const Text('Пульт судьи')),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Text('Выберите матч', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy)),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Text('Только игры, на которые вас назначили.', style: TextStyle(color: AppColors.muted)),
            ),
            if (loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
            if (error != null)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(error!, style: const TextStyle(color: AppColors.danger)),
              )
            else if (!loading && matches.isEmpty)
              const EmptyHint(title: 'Нет назначений')
            else
              for (final match in matches)
                ListTile(
                  onTap: () => context.push('/referee/${match.id}'),
                  title: Text(
                    '${store.teamName(match.homeTeamId)} — ${store.teamName(match.awayTeamId)}',
                    style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.navy),
                  ),
                  subtitle: Text('${matchStateLabel(match.status)} · ${longKickoff(match.scheduledAt)}'),
                  trailing: Text(
                    '${match.homeScore}:${match.awayScore}',
                    style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy),
                  ),
                ),
          ],
        ),
      ),
    );
  }
}
