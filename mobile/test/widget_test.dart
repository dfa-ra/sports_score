import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:student_league/core/api_client.dart';
import 'package:student_league/core/app.dart';
import 'package:student_league/core/format.dart';
import 'package:student_league/core/models.dart';
import 'package:student_league/state/favorites_store.dart';
import 'package:student_league/widgets/match_row.dart';

void main() {
  testWidgets('renders KRONBARS phone shell', (WidgetTester tester) async {
    await tester.pumpWidget(const StudentLeagueApp(autoload: false));
    await tester.pump();

    expect(find.text('KRONBARS'), findsWidgets);
    expect(find.text('Игры'), findsOneWidget);
    expect(find.text('Таблица'), findsOneWidget);
    expect(find.text('Live'), findsOneWidget);
    expect(find.text('Профиль'), findsOneWidget);
    expect(find.text('ВСЕ'), findsOneWidget);
  });

  testWidgets('table tab keeps season selector and inner tabs', (WidgetTester tester) async {
    await tester.pumpWidget(const StudentLeagueApp(autoload: false));
    await tester.pump();
    await tester.tap(find.text('Таблица'));
    await tester.pumpAndSettle();

    expect(find.text('ТАБЛИЦА'), findsWidgets);
    expect(find.text('РЕЗУЛЬТАТЫ'), findsOneWidget);
    expect(find.text('БОМБАРДИРЫ'), findsOneWidget);
  });

  testWidgets('live tab is its own empty screen', (WidgetTester tester) async {
    await tester.pumpWidget(const StudentLeagueApp(autoload: false));
    await tester.pump();
    await tester.tap(find.text('Live'));
    await tester.pumpAndSettle();

    expect(find.text('LIVE'), findsWidgets);
    expect(find.text('Сейчас никто не играет'), findsOneWidget);
    expect(find.text('ВСЕ'), findsNothing);
  });

  testWidgets('profile tab shows login for guests', (WidgetTester tester) async {
    await tester.pumpWidget(const StudentLeagueApp(autoload: false));
    await tester.pump();
    await tester.tap(find.text('Профиль'));
    await tester.pumpAndSettle();

    expect(find.text('Войти'), findsOneWidget);
    expect(find.text('Почта'), findsOneWidget);
    expect(find.text('Сервер'), findsNothing);
    expect(find.text('Сохранить и обновить'), findsNothing);
  });

  testWidgets('match row stars a game without leaving the list', (WidgetTester tester) async {
    final fav = FavoritesStore(persist: false);
    final match = LeagueMatch(
      id: 'm1',
      tournamentId: 't1',
      homeTeamId: 'h1',
      awayTeamId: 'a1',
      status: 'SCHEDULED',
      homeScore: 0,
      awayScore: 0,
    );
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: fav,
        child: MaterialApp(
          home: Scaffold(
            body: MatchRow(match: match, homeName: 'ФК Общага', awayName: 'Политех'),
          ),
        ),
      ),
    );

    expect(find.text('ФК Общага'), findsOneWidget);
    expect(find.text('Политех'), findsOneWidget);
    await tester.tap(find.byTooltip('В избранное'));
    await tester.pump();
    expect(fav.hasMatch('m1'), isTrue);
    expect(find.byTooltip('Убрать из избранного'), findsOneWidget);
  });

  test('recent game line names the opponent', () {
    expect(
      recentGameLine(
        teamId: 'h1',
        homeTeamId: 'h1',
        awayTeamId: 'a1',
        homeScore: 2,
        awayScore: 1,
        opponentName: 'Политех',
        scheduledAt: DateTime(2026, 8, 24, 17, 12),
      ),
      '2:1 · Политех · 24 авг.',
    );
  });

  test('compiled API points at the stand, not the phone loopback', () {
    expect(ApiClient.compiledBaseUrl, contains('144.31.153.52:3000'));
    expect(ApiClient.compiledBaseUrl, isNot(contains('127.0.0.1')));
  });

  test('favorites toggle teams and matches in memory', () {
    final fav = FavoritesStore(persist: false);
    fav.toggleMatch('m1');
    fav.toggleTeam('t1');
    expect(fav.count, 2);
    fav.toggleMatch('m1');
    expect(fav.hasMatch('m1'), isFalse);
    expect(fav.hasTeam('t1'), isTrue);
  });
}
