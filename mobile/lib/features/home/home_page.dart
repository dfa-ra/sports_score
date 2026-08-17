import 'package:flutter/material.dart';
import '../../core/theme.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
          children: [
            Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.accent.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: AppColors.accent.withValues(alpha: 0.35)),
                  ),
                  child: const Text('SL', style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 12),
                Text('Student League', style: Theme.of(context).textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.line),
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    AppColors.accent.withValues(alpha: 0.10),
                    AppColors.surface,
                  ],
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'СТУДЕНЧЕСКАЯ ЛИГА',
                    style: TextStyle(
                      color: AppColors.accent,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'Live-счёт и управление матчами',
                    style: Theme.of(context).textTheme.displaySmall?.copyWith(fontSize: 28, height: 1.15),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Смотрите турниры, составы и статистику. Судьям — быстрый пульт с крупными кнопками.',
                  ),
                  const SizedBox(height: 18),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      FilledButton(
                        onPressed: () => Navigator.of(context).pushNamed('/matches'),
                        child: const Text('Матчи'),
                      ),
                      OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.textStrong,
                          side: const BorderSide(color: AppColors.line),
                        ),
                        onPressed: () => Navigator.of(context).pushNamed('/referee'),
                        child: const Text('Режим судьи'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _NavCard(
              title: 'Матчи',
              subtitle: 'Расписание и live-карточки',
              onTap: () => Navigator.of(context).pushNamed('/matches'),
            ),
            const SizedBox(height: 10),
            _NavCard(
              title: 'Пульт судьи',
              subtitle: 'Старт, голы, карточки, финиш',
              accent: true,
              onTap: () => Navigator.of(context).pushNamed('/referee'),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavCard extends StatelessWidget {
  const _NavCard({
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.accent = false,
  });

  final String title;
  final String subtitle;
  final VoidCallback onTap;
  final bool accent;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: accent ? AppColors.accent.withValues(alpha: 0.10) : AppColors.surface,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: accent ? AppColors.accent.withValues(alpha: 0.35) : AppColors.line,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(color: AppColors.textStrong, fontWeight: FontWeight.w700, fontSize: 16)),
              const SizedBox(height: 4),
              Text(subtitle),
            ],
          ),
        ),
      ),
    );
  }
}
