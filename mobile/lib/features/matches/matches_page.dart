import 'package:flutter/material.dart';
import '../../core/theme.dart';

/// Demo list of matches with the shared dark palette.
/// Wired to API in a later iteration; UI/layout is production-shaped now.
class MatchesPage extends StatelessWidget {
  const MatchesPage({super.key});

  static const _demo = [
    _MatchRow(status: 'LIVE', home: 1, away: 0, label: 'Campus United — Alpha FC'),
    _MatchRow(status: 'SCHEDULED', home: 0, away: 0, label: 'Beta FC — North Side'),
    _MatchRow(status: 'FINISHED', home: 2, away: 2, label: 'East Wings — Riveters'),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Матчи')),
      body: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: _demo.length,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          final m = _demo[index];
          final live = m.status == 'LIVE';
          return Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: live ? AppColors.success.withValues(alpha: 0.45) : AppColors.line,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: live ? AppColors.success.withValues(alpha: 0.14) : AppColors.bg,
                    borderRadius: BorderRadius.circular(999),
                    border: Border.all(
                      color: live ? AppColors.success.withValues(alpha: 0.4) : AppColors.line,
                    ),
                  ),
                  child: Text(
                    m.status,
                    style: TextStyle(
                      color: live ? AppColors.success : AppColors.accent,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  '${m.home} : ${m.away}',
                  style: Theme.of(context).textTheme.displayMedium?.copyWith(fontSize: 34),
                ),
                const SizedBox(height: 4),
                Text(m.label),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _MatchRow {
  const _MatchRow({
    required this.status,
    required this.home,
    required this.away,
    required this.label,
  });

  final String status;
  final int home;
  final int away;
  final String label;
}
