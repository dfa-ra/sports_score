import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/format.dart';
import '../core/models.dart';
import '../core/theme.dart';

class MatchRow extends StatelessWidget {
  const MatchRow({
    super.key,
    required this.match,
    required this.homeName,
    required this.awayName,
  });

  final LeagueMatch match;
  final String homeName;
  final String awayName;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => context.push('/matches/${match.id}'),
      child: Container(
        padding: const EdgeInsets.fromLTRB(8, 10, 12, 10),
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(bottom: BorderSide(color: AppColors.line)),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 48,
              child: Text(
                shortKickoff(match.scheduledAt, match.status),
                style: TextStyle(
                  color: match.isLive ? AppColors.ice : AppColors.muted,
                  fontWeight: match.isLive ? FontWeight.w800 : FontWeight.w500,
                  fontSize: 12,
                ),
              ),
            ),
            Expanded(
              child: Column(
                children: [
                  _Side(name: homeName),
                  const SizedBox(height: 4),
                  _Side(name: awayName),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text('${match.homeScore}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                const SizedBox(height: 4),
                Text('${match.awayScore}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Side extends StatelessWidget {
  const _Side({required this.name});
  final String name;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 18,
          height: 18,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: const Color(0x294CB4E5),
            borderRadius: BorderRadius.circular(4),
          ),
          child: Text(
            initials(name),
            style: const TextStyle(fontSize: 7, fontWeight: FontWeight.w800, color: AppColors.navy),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 15, color: AppColors.ink),
          ),
        ),
      ],
    );
  }
}

class LeagueHead extends StatelessWidget {
  const LeagueHead({super.key, required this.title, this.subtitle = 'KRONBARS'});
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      color: const Color(0xFFEEF4F9),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title.toUpperCase(), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13, color: AppColors.navy)),
          Text(subtitle, style: const TextStyle(fontSize: 11, color: AppColors.muted, fontWeight: FontWeight.w600, letterSpacing: 0.5)),
        ],
      ),
    );
  }
}

class EmptyHint extends StatelessWidget {
  const EmptyHint({super.key, required this.title, this.text});
  final String title;
  final String? text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(12, 16, 12, 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFF6F9FC),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.line, style: BorderStyle.solid),
      ),
      child: Column(
        children: [
          Text(title, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, fontSize: 16)),
          if (text != null) ...[
            const SizedBox(height: 6),
            Text(text!, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
          ],
        ],
      ),
    );
  }
}
