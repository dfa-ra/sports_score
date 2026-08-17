import 'package:flutter_test/flutter_test.dart';
import 'package:student_league/core/app.dart';

void main() {
  testWidgets('renders Student League home', (WidgetTester tester) async {
    await tester.pumpWidget(const StudentLeagueApp());
    expect(find.text('Student League'), findsOneWidget);
    expect(find.text('Матчи'), findsWidgets);
    expect(find.text('Режим судьи'), findsOneWidget);
  });
}
