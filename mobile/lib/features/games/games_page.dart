import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class GamesPage extends StatefulWidget {
  const GamesPage({super.key, this.liveOnly = false});
  final bool liveOnly;

  @override
  State<GamesPage> createState() => _GamesPageState();
}

class _GamesPageState extends State<GamesPage> {
  String? day;

  List<_DayChip> get _strip {
    final today = DateTime.now();
    final noon = DateTime(today.year, today.month, today.day, 12);
    return [
      for (var i = -2; i <= 2; i++)
        _DayChip(
          keyName: ymd(noon.add(Duration(days: i))),
          label: i == 0
              ? 'Сегодня ${noon.day.toString().padLeft(2, '0')}.${noon.month.toString().padLeft(2, '0')}.'
              : '${weekdayShort(noon.add(Duration(days: i)))} ${noon.add(Duration(days: i)).day.toString().padLeft(2, '0')}.${noon.add(Duration(days: i)).month.toString().padLeft(2, '0')}.',
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final store = context.watch<LeagueStore>();
    final items = widget.liveOnly
        ? store.liveMatches()
        : day == null
            ? store.upcomingMatches()
            : store.dayMatches(day!);
    final groups = store.grouped(items);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (widget.liveOnly)
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 14, 16, 4),
            child: Text('LIVE', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 22, color: AppColors.navy)),
          )
        else
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
            child: Row(
              children: [
                _chip(label: 'Все', selected: day == null, onTap: () => setState(() => day = null)),
                ..._strip.map(
                  (item) => _chip(
                    label: item.label,
                    selected: day == item.keyName,
                    onTap: () => setState(() => day = item.keyName),
                  ),
                ),
              ],
            ),
          ),
        if (store.loading) const LinearProgressIndicator(minHeight: 2, color: AppColors.ice),
        if (store.error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(store.error!, style: const TextStyle(color: AppColors.danger)),
          ),
        Expanded(
          child: RefreshIndicator(
            color: AppColors.ice,
            onRefresh: store.load,
            child: items.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      EmptyHint(
                        title: widget.liveOnly
                            ? 'Сейчас никто не играет'
                            : day == null
                                ? 'Пока нет ближайших матчей'
                                : 'В этот день матчей нет',
                      ),
                    ],
                  )
                : ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      for (final entry in groups.entries) ...[
                        LeagueHead(title: store.tournamentNames[entry.key] ?? 'Турнир'),
                        for (final match in entry.value)
                          MatchRow(
                            match: match,
                            homeName: store.teamName(match.homeTeamId),
                            awayName: store.teamName(match.awayTeamId),
                          ),
                        if (!widget.liveOnly)
                          InkWell(
                            onTap: () => context.go('/table'),
                            child: const Padding(
                              padding: EdgeInsets.fromLTRB(14, 12, 14, 12),
                              child: Row(
                                children: [
                                  Text('К таблице', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                                  Spacer(),
                                  Icon(Icons.chevron_right, color: AppColors.muted),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ],
                  ),
          ),
        ),
      ],
    );
  }

  Widget _chip({required String label, required bool selected, required VoidCallback onTap}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: TextButton(
        onPressed: onTap,
        style: TextButton.styleFrom(
          foregroundColor: selected ? AppColors.navy : AppColors.muted,
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
        ),
        child: Container(
          decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: selected ? AppColors.ice : Colors.transparent, width: 3)),
          ),
          padding: const EdgeInsets.only(bottom: 4),
          child: Text(label.toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 12, letterSpacing: 0.3)),
        ),
      ),
    );
  }
}

class _DayChip {
  const _DayChip({required this.keyName, required this.label});
  final String keyName;
  final String label;
}
