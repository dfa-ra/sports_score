import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/format.dart';
import '../core/models.dart';
import '../core/theme.dart';
import 'player_photo.dart';
import 'team_mark.dart';

class PlayerCardSheet extends StatefulWidget {
  const PlayerCardSheet({
    super.key,
    required this.card,
    this.onEdit,
    this.resolveMedia,
  });

  final PlayerCard card;
  final VoidCallback? onEdit;
  final String? Function(String?)? resolveMedia;

  @override
  State<PlayerCardSheet> createState() => _PlayerCardSheetState();
}

class _PlayerCardSheetState extends State<PlayerCardSheet> {
  int tab = 0;

  String? media(String? url) => (widget.resolveMedia ?? (value) => value)(url);

  String marks(PlayerMatchHistory row) {
    return [
      if (row.minutesPlayed != null) "${row.minutesPlayed}'",
      if (row.goals > 0) '${row.goals}Г',
      if (row.assists > 0) '${row.assists}П',
      if (row.yellowCards > 0) '${row.yellowCards}Ж',
      if (row.redCards > 0) '${row.redCards}К',
    ].join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    final card = widget.card;
    final age = ageLine(card.dateOfBirth);
    final stats = <({String label, Object value})>[
      if (card.statistics['appearances'] != null) (label: 'Игры', value: card.statistics['appearances']!),
      if (card.statistics['goals'] != null) (label: 'Голы', value: card.statistics['goals']!),
      if (card.statistics['assists'] != null) (label: 'Пас', value: card.statistics['assists']!),
      if (card.statistics['yellowCards'] != null) (label: 'Жёлтые', value: card.statistics['yellowCards']!),
      if (card.statistics['redCards'] != null) (label: 'Красные', value: card.statistics['redCards']!),
      if (card.statistics['cleanSheets'] != null) (label: 'Сухие', value: card.statistics['cleanSheets']!),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
          child: Row(
            children: [
              PlayerPhoto(url: media(card.avatarUrl), name: card.displayName, size: 76, tile: true),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(card.displayName, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy, height: 1.1)),
                    if (age.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(age, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                    ],
                    Text(
                      [
                        if (card.jerseyNumber != null) '№${card.jerseyNumber}',
                        if (card.teamName == null || card.teamName!.isEmpty) (card.position ?? 'Игрок'),
                      ].join(' · '),
                      style: const TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ],
                ),
              ),
              if (widget.onEdit != null)
                IconButton(
                  tooltip: 'Изменить',
                  onPressed: widget.onEdit,
                  icon: const Icon(Icons.edit_outlined, color: AppColors.navy),
                ),
            ],
          ),
        ),
        if (card.teamName != null && card.teamName!.isNotEmpty)
          InkWell(
            onTap: card.teamId == null ? null : () => context.push('/teams/${card.teamId}'),
            child: Container(
              margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: const Color(0xFFF4F7FB), borderRadius: BorderRadius.circular(10)),
              child: Row(
                children: [
                  TeamMark(url: media(card.teamLogoUrl), name: card.teamName!, size: 22),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(card.teamName!, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                  ),
                  Text(card.position ?? 'Игрок', style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                ],
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Row(
            children: [
              _Tab(label: 'Игры', on: tab == 0, onTap: () => setState(() => tab = 0)),
              _Tab(label: 'Цифры', on: tab == 1, onTap: () => setState(() => tab = 1)),
            ],
          ),
        ),
        const Divider(height: 1),
        if (tab == 1)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: stats.isEmpty
                ? const Text('Пока без цифр.', style: TextStyle(color: AppColors.muted))
                : Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final item in stats)
                        SizedBox(
                          width: 104,
                          child: Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: AppColors.line),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(item.label.toUpperCase(), style: const TextStyle(color: AppColors.muted, fontSize: 10, fontWeight: FontWeight.w700, letterSpacing: 0.4)),
                                Text('${item.value}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.navy)),
                              ],
                            ),
                          ),
                        ),
                    ],
                  ),
          )
        else if (card.history.isEmpty)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('Матчей пока нет.', style: TextStyle(color: AppColors.muted)),
          )
        else
          for (final row in card.history) _GameRow(row: row, marks: marks(row), resolveMedia: media),
      ],
    );
  }
}

class _Tab extends StatelessWidget {
  const _Tab({required this.label, required this.on, required this.onTap});
  final String label;
  final bool on;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onTap,
      style: TextButton.styleFrom(
        foregroundColor: on ? AppColors.navy : AppColors.muted,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
      child: Container(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: on ? AppColors.ice : Colors.transparent, width: 3)),
        ),
        padding: const EdgeInsets.only(bottom: 4),
        child: Text(label.toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0.6, fontSize: 13)),
      ),
    );
  }
}

class _GameRow extends StatelessWidget {
  const _GameRow({required this.row, required this.marks, required this.resolveMedia});
  final PlayerMatchHistory row;
  final String marks;
  final String? Function(String?) resolveMedia;

  @override
  Widget build(BuildContext context) {
    final homeName = row.homeTeamName ?? (row.home ? 'Команда' : row.opponentName);
    final awayName = row.awayTeamName ?? (row.home ? row.opponentName : 'Команда');
    final mark = outcomeMark[row.outcome];
    return InkWell(
      onTap: () => context.push('/matches/${row.matchId}'),
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: AppColors.line))),
        child: Row(
          children: [
            SizedBox(
              width: 36,
              child: Text(matchDayShort(row.scheduledAt), style: const TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w600)),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _Side(name: homeName, logo: resolveMedia(row.homeTeamLogoUrl), own: row.home),
                  const SizedBox(height: 4),
                  _Side(name: awayName, logo: resolveMedia(row.awayTeamLogoUrl), own: !row.home),
                  if (marks.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(marks, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
                  ],
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text('${row.homeScore}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                Text('${row.awayScore}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
              ],
            ),
            if (mark != null) ...[
              const SizedBox(width: 8),
              _Outcome(mark: mark),
            ],
          ],
        ),
      ),
    );
  }
}

class _Side extends StatelessWidget {
  const _Side({required this.name, required this.own, this.logo});
  final String name;
  final String? logo;
  final bool own;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        TeamMark(url: logo, name: name, size: 16),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontWeight: own ? FontWeight.w800 : FontWeight.w500,
              color: own ? AppColors.navy : AppColors.text,
              fontSize: 13,
            ),
          ),
        ),
      ],
    );
  }
}

class _Outcome extends StatelessWidget {
  const _Outcome({required this.mark});
  final String mark;

  @override
  Widget build(BuildContext context) {
    final color = switch (mark) {
      'В' => AppColors.win,
      'Н' => AppColors.draw,
      _ => AppColors.danger,
    };
    return Container(
      width: 22,
      height: 22,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
      child: Text(mark, style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w800)),
    );
  }
}
