import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../features/games/games_page.dart';
import '../features/matches/match_detail_page.dart';
import '../features/profile/profile_page.dart';
import '../features/referee/referee_control_page.dart';
import '../features/shell/shell_page.dart';
import '../features/table/table_page.dart';
import '../state/auth_controller.dart';
import '../state/league_store.dart';
import 'api_client.dart';
import 'theme.dart';

final _rootKey = GlobalKey<NavigatorState>();

GoRouter buildRouter() {
  return GoRouter(
    navigatorKey: _rootKey,
    initialLocation: '/games',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => ShellPage(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(routes: [GoRoute(path: '/games', builder: (_, __) => const GamesPage())]),
          StatefulShellBranch(routes: [GoRoute(path: '/table', builder: (_, __) => const TablePage())]),
          StatefulShellBranch(routes: [GoRoute(path: '/live', builder: (_, __) => const GamesPage(liveOnly: true))]),
          StatefulShellBranch(routes: [GoRoute(path: '/profile', builder: (_, __) => const ProfilePage())]),
        ],
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/matches/:id',
        builder: (_, state) => MatchDetailPage(matchId: state.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/referee',
        builder: (_, __) => const RefereeControlPage(),
      ),
    ],
  );
}

class StudentLeagueApp extends StatefulWidget {
  const StudentLeagueApp({super.key, this.api, this.autoload = true});

  final ApiClient? api;
  final bool autoload;

  @override
  State<StudentLeagueApp> createState() => _StudentLeagueAppState();
}

class _StudentLeagueAppState extends State<StudentLeagueApp> {
  late final ApiClient api;
  late final AuthController auth;
  late final LeagueStore league;
  late final GoRouter router;

  @override
  void initState() {
    super.initState();
    api = widget.api ?? ApiClient();
    auth = AuthController(api);
    league = LeagueStore(api);
    router = buildRouter();
    if (widget.autoload) {
      auth.restore();
      league.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: auth),
        ChangeNotifierProvider.value(value: league),
      ],
      child: MaterialApp.router(
        title: 'KRONBARS',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(),
        routerConfig: router,
      ),
    );
  }
}
