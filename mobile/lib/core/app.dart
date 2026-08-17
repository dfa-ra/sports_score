import 'package:flutter/material.dart';
import '../features/home/home_page.dart';
import '../features/referee/referee_control_page.dart';

class StudentLeagueApp extends StatelessWidget {
  const StudentLeagueApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Student League',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF2FD67B),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HomePage(),
      routes: {
        '/referee': (_) => const RefereeControlPage(),
      },
    );
  }
}
