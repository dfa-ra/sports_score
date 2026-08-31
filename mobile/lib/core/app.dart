import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../features/games/games_page.dart';
import '../features/matches/match_detail_page.dart';
import '../features/players/player_page.dart';
import '../features/profile/profile_page.dart';
import '../features/referee/referee_matches_page.dart';
import '../features/referee/referee_pad_page.dart';
import '../features/shell/shell_page.dart';
import '../features/table/table_page.dart';
import '../features/teams/team_page.dart';
import '../state/auth_controller.dart';
import '../state/favorites_store.dart';
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
        builder: (_, __) => const RefereeMatchesPage(),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/referee/:id',
        builder: (_, state) => RefereePadPage(matchId: state.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/teams/:id',
        builder: (_, state) => TeamPage(teamId: state.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/players/:id',
        builder: (_, state) => PlayerPage(playerId: state.pathParameters['id']!),
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
  late final FavoritesStore favorites;
  late final GoRouter router;

  @override
  void initState() {
    super.initState();
    api = widget.api ?? ApiClient();
    auth = AuthController(api);
    league = LeagueStore(api);
    favorites = FavoritesStore(persist: widget.autoload);
    router = buildRouter();
    if (widget.autoload) {
      _boot();
    }
  }

  Future<void> _boot() async {
    await Future.wait([
      auth.restore(),
      favorites.load(),
    ]);
    if (auth.canManageLeague) {
      await api.restoreAdminBaseUrl();
    } else {
      api.resetToCompiled();
    }
    await league.load();
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: auth),
        ChangeNotifierProvider.value(value: league),
        ChangeNotifierProvider.value(value: favorites),
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
