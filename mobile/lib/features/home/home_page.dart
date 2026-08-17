import 'package:flutter/material.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Student League', style: Theme.of(context).textTheme.displaySmall),
              const SizedBox(height: 8),
              const Text('Live campus sports scores, teams, and referee control.'),
              const Spacer(),
              FilledButton(
                onPressed: () => Navigator.of(context).pushNamed('/referee'),
                child: const Text('Open Referee Mode'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
