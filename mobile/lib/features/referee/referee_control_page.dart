import 'package:flutter/material.dart';
import '../../core/theme.dart';

/// Large-button referee UI optimized for live match event entry.
class RefereeControlPage extends StatefulWidget {
  const RefereeControlPage({super.key});

  @override
  State<RefereeControlPage> createState() => _RefereeControlPageState();
}

class _RefereeControlPageState extends State<RefereeControlPage> {
  int home = 0;
  int away = 0;
  String status = 'SCHEDULED';

  void _setStatus(String value) => setState(() => status = value);

  void _goal({required bool forHome}) {
    setState(() {
      if (forHome) {
        home++;
      } else {
        away++;
      }
      status = 'LIVE';
    });
  }

  @override
  Widget build(BuildContext context) {
    final live = status == 'LIVE' || status == 'PAUSED';
    return Scaffold(
      appBar: AppBar(title: const Text('Пульт судьи')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: live ? AppColors.success.withValues(alpha: 0.45) : AppColors.line,
                ),
              ),
              child: Column(
                children: [
                  Text(
                    status,
                    style: TextStyle(
                      color: live ? AppColors.success : AppColors.accent,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.6,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '$home : $away',
                    style: Theme.of(context).textTheme.displayMedium?.copyWith(fontSize: 44),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: GridView.count(
                crossAxisCount: 2,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                children: [
                  _BigAction(label: 'Старт', color: AppColors.success, onTap: () => _setStatus('LIVE')),
                  _BigAction(label: 'Пауза', onTap: () => _setStatus('PAUSED')),
                  _BigAction(label: 'Гол дом.', color: AppColors.accent, onTap: () => _goal(forHome: true)),
                  _BigAction(label: 'Гол гости', color: AppColors.accent, onTap: () => _goal(forHome: false)),
                  _BigAction(label: 'Жёлтая', onTap: () {}),
                  _BigAction(label: 'Красная', color: AppColors.danger, onTap: () {}),
                  _BigAction(label: 'Замена', onTap: () {}),
                  _BigAction(label: 'Финиш', color: AppColors.danger, onTap: () => _setStatus('FINISHED')),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BigAction extends StatelessWidget {
  const _BigAction({
    required this.label,
    required this.onTap,
    this.color,
  });

  final String label;
  final VoidCallback onTap;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.textStrong;
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        minimumSize: const Size.fromHeight(88),
        backgroundColor: AppColors.surface,
        foregroundColor: c,
        side: BorderSide(color: c.withValues(alpha: 0.45)),
      ),
      onPressed: onTap,
      child: Text(label, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
    );
  }
}
