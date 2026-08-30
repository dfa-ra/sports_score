import 'package:flutter_test/flutter_test.dart';
import 'package:student_league/core/app.dart';

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
}
