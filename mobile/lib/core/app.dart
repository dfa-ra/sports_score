import 'package:flutter/material.dart';
import '../features/home/home_page.dart';
import '../features/matches/matches_page.dart';
import '../features/referee/referee_control_page.dart';
import 'theme.dart';

class StudentLeagueApp extends StatelessWidget {
  const StudentLeagueApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Student League',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      home: const HomePage(),
      routes: {
        '/matches': (_) => const MatchesPage(),
        '/referee': (_) => const RefereeControlPage(),
      },
    );
  }
}
