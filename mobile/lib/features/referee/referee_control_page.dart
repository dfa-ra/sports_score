import 'package:flutter/material.dart';

/// Large-button referee UI optimized for live match event entry.
class RefereeControlPage extends StatelessWidget {
  const RefereeControlPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Live Match Control')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text('0 : 0', style: Theme.of(context).textTheme.displayMedium),
            const SizedBox(height: 24),
            Expanded(
              child: GridView.count(
                crossAxisCount: 2,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                children: const [
                  _BigAction(label: 'Start'),
                  _BigAction(label: 'Pause'),
                  _BigAction(label: 'Goal'),
                  _BigAction(label: 'Assist'),
                  _BigAction(label: 'Yellow'),
                  _BigAction(label: 'Red'),
                  _BigAction(label: 'Sub'),
                  _BigAction(label: 'Finish'),
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
  const _BigAction({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(88)),
      onPressed: () {},
      child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
    );
  }
}
