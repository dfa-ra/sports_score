import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';
import '../../state/league_store.dart';
import '../../widgets/marks.dart';
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
      appBar: AppBar(title: const Text('Пульт')),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
          children: [
            const Text('ПУЛЬТ', style: TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800, letterSpacing: 0.8, fontSize: 12)),
            const SizedBox(height: 4),
            const Text('Выберите матч', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: AppColors.navy)),
            const SizedBox(height: 6),
            const Text('Только игры, на которые вас назначили. Дальше — часы, гол и карточки.', style: TextStyle(color: AppColors.muted)),
            const SizedBox(height: 16),
            if (loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
            if (error != null)
              Text(error!, style: const TextStyle(color: AppColors.danger))
            else if (!loading && matches.isEmpty)
              const EmptyHint(title: 'Нет назначений', text: 'Когда поставят на игру — карточка появится сама.')
            else
              for (final match in matches)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Material(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: () => context.push('/referee/${match.id}'),
                      child: Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: AppColors.line),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              matchStateLabel(match.status).toUpperCase(),
                              style: TextStyle(
                                color: match.isLive ? AppColors.ice : AppColors.muted,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.6,
                                fontSize: 11,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                TeamMark(name: store.teamName(match.homeTeamId), logoUrl: store.teamLogo(match.homeTeamId), size: 22),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    '${store.teamName(match.homeTeamId)} — ${store.teamName(match.awayTeamId)}',
                                    style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy),
                                  ),
                                ),
                                TeamMark(name: store.teamName(match.awayTeamId), logoUrl: store.teamLogo(match.awayTeamId), size: 22),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text('${match.homeScore} : ${match.awayScore}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, fontSize: 20)),
                            Text(formatWhen(match.scheduledAt), style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
          ],
        ),
      ),
    );
  }
}
