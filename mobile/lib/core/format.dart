const roleLabels = {
  'FAN': 'Зритель',
  'PLAYER': 'Игрок',
  'CAPTAIN': 'Капитан',
  'REFEREE': 'Судья',
  'ANALYST': 'Аналитик',
  'ADMIN': 'Админ',
};

String initials(String? name) {
  if (name == null || name.trim().isEmpty) return 'SL';
  final parts = name.trim().split(RegExp(r'\s+')).take(2);
  return parts.map((p) => p[0].toUpperCase()).join();
}

String ymd(DateTime date) {
  final m = date.month.toString().padLeft(2, '0');
  final d = date.day.toString().padLeft(2, '0');
  return '${date.year}-$m-$d';
}

String kickoffDayMonth(DateTime? at) {
  if (at == null) return '';
  final d = at.toLocal();
  final day = d.day.toString().padLeft(2, '0');
  final month = d.month.toString().padLeft(2, '0');
  return '$day/$month';
}

String kickoffClock(DateTime? at) {
  if (at == null) return '';
  final d = at.toLocal();
  final h = d.hour.toString().padLeft(2, '0');
  final min = d.minute.toString().padLeft(2, '0');
  return '$h:$min';
}

String shortKickoff(DateTime? at, String status) {
  if (status == 'LIVE' || status == 'PAUSED') return 'LIVE';
  if (at == null) return '—';
  final date = kickoffDayMonth(at);
  if (date.isEmpty) return '—';
  if (status == 'FINISHED' || status == 'CANCELLED') return '${date.replaceAll('/', '.')}.';
  return kickoffClock(at).isEmpty ? '—' : kickoffClock(at);
}

String longKickoff(DateTime? at) {
  if (at == null) return '—';
  final d = at.toLocal();
  final day = d.day.toString().padLeft(2, '0');
  final month = d.month.toString().padLeft(2, '0');
  final h = d.hour.toString().padLeft(2, '0');
  final min = d.minute.toString().padLeft(2, '0');
  return '$day.$month.${d.year} $h:$min';
}

String formatWhen(DateTime? at) {
  if (at == null) return '—';
  final d = at.toLocal();
  final h = d.hour.toString().padLeft(2, '0');
  final min = d.minute.toString().padLeft(2, '0');
  return '${d.day} ${monthShort[d.month - 1]}, $h:$min';
}

/// Same numbers as the public page: level score plus both penalty totals.
({int home, int away})? penaltyPair({
  required int homeScore,
  required int awayScore,
  int? penaltyHome,
  int? penaltyAway,
}) {
  if (homeScore != awayScore || penaltyHome == null || penaltyAway == null) return null;
  return (home: penaltyHome, away: penaltyAway);
}

String publicScore({
  required int homeScore,
  required int awayScore,
  int? penaltyHome,
  int? penaltyAway,
}) {
  final pens = penaltyPair(
    homeScore: homeScore,
    awayScore: awayScore,
    penaltyHome: penaltyHome,
    penaltyAway: penaltyAway,
  );
  if (pens == null) return '$homeScore:$awayScore';
  return '$homeScore:$awayScore (пен. ${pens.home}:${pens.away})';
}

/// Finished shootout uses the public line. A match without one keeps the spaced score.
String refereeBoardScore({
  required String status,
  required int homeScore,
  required int awayScore,
  int? penaltyHome,
  int? penaltyAway,
}) {
  if (status == 'FINISHED') {
    final line = publicScore(
      homeScore: homeScore,
      awayScore: awayScore,
      penaltyHome: penaltyHome,
      penaltyAway: penaltyAway,
    );
    if (penaltyPair(homeScore: homeScore, awayScore: awayScore, penaltyHome: penaltyHome, penaltyAway: penaltyAway) != null) {
      return line;
    }
  }
  return '$homeScore : $awayScore';
}

/// A decided shootout names the score. Otherwise the early-finish question stays.
String finishConfirmQuestion({
  required bool halfStillOpen,
  required int homeScore,
  required int awayScore,
  int? penaltyHome,
  int? penaltyAway,
  bool decisive = false,
}) {
  if (decisive) {
    final pens = penaltyPair(
      homeScore: homeScore,
      awayScore: awayScore,
      penaltyHome: penaltyHome,
      penaltyAway: penaltyAway,
    );
    if (pens != null) return 'Завершить матч? $homeScore:$awayScore, пен. ${pens.home}:${pens.away}';
  }
  if (halfStillOpen) return 'Завершить матч до конца тайма? Счёт зафиксируется как есть.';
  return 'Завершить матч?';
}

String matchStateLabel(String status) {
  return switch (status) {
    'FINISHED' => 'Завершен',
    'LIVE' => 'Live',
    'PAUSED' => 'Пауза',
    'CANCELLED' => 'Отменён',
    'SCHEDULED' => 'Не начался',
    _ => status,
  };
}

/// Own goals sit with the team that received the point. Cards stay with the player.
bool eventOnHomeSide({
  required String? teamId,
  required String? homeTeamId,
  required String? eventType,
  bool ownGoal = false,
}) {
  final byHome = teamId != null && teamId == homeTeamId;
  final own = eventType == 'OWN_GOAL' || (eventType == 'GOAL' && ownGoal);
  return own ? !byHome : byHome;
}

/// `2:00` is `2'`. Kickoff on a period line is `1'`. Six seconds into the
/// second half of a 2×15 is `16'`.
int eventMinute(
  int? gameTime, {
  int? period,
  int? periodLengthSeconds,
  String? eventType,
}) {
  final seconds = gameTime == null || gameTime < 0 ? 0 : gameTime;
  final length = periodLengthSeconds != null && periodLengthSeconds > 0 ? periodLengthSeconds : 15 * 60;
  final boundary = eventType == 'PERIOD_START' || eventType == 'PERIOD_END';
  if (boundary && seconds < 60) return 1;
  final minute = seconds ~/ 60;
  final intoPeriod = seconds % length;
  if (seconds >= length && intoPeriod > 0 && intoPeriod < 60) return minute + 1;
  if (period != null && period > 1 && seconds == (period - 1) * length) return minute + 1;
  return minute;
}

String weekdayShort(DateTime date) {
  const names = ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС'];
  return names[date.weekday - 1];
}

const monthShort = ['янв.', 'фев.', 'мар.', 'апр.', 'мая', 'июн.', 'июл.', 'авг.', 'сен.', 'окт.', 'ноя.', 'дек.'];

String formDay(DateTime? at) {
  if (at == null) return '—';
  final d = at.toLocal();
  return '${d.day} ${monthShort[d.month - 1]}';
}

String recentGameLine({
  required String teamId,
  required String homeTeamId,
  required String awayTeamId,
  required int homeScore,
  required int awayScore,
  required String opponentName,
  DateTime? scheduledAt,
}) {
  final home = teamId == homeTeamId;
  final own = home ? homeScore : awayScore;
  final theirs = home ? awayScore : homeScore;
  return '$own:$theirs · $opponentName · ${formDay(scheduledAt)}';
}

const eventLabels = {
  'GOAL': 'Гол',
  'OWN_GOAL': 'Автогол',
  'ASSIST': 'Передача',
  'YELLOW_CARD': 'Жёлтая',
  'RED_CARD': 'Красная',
  'SUBSTITUTION': 'Замена',
  'POINT': 'Очко',
  'FOUL': 'Фол',
  'TIMEOUT': 'Тайм-аут',
  'PERIOD_START': 'Начало тайма',
  'PERIOD_END': 'Конец тайма',
  'OTHER': 'Событие',
};

String eventLabel(String? type) => eventLabels[type] ?? type ?? 'Событие';

String assistWord(int count) {
  final abs = count.abs() % 100;
  final last = abs % 10;
  if (abs > 10 && abs < 20) return 'передач';
  if (last == 1) return 'передача';
  if (last >= 2 && last <= 4) return 'передачи';
  return 'передач';
}

String eventDetail({
  String? eventType,
  String? playerName,
  int? playerJersey,
  String? secondaryPlayerName,
  int? secondaryPlayerJersey,
}) {
  final main = playerTag(playerName, playerJersey);
  final second = playerTag(secondaryPlayerName, secondaryPlayerJersey);
  if (eventType == 'GOAL' && second.isNotEmpty) return '$main · передача $second';
  if (eventType == 'SUBSTITUTION' && second.isNotEmpty) return '$main → $second';
  return main;
}

int matchElapsedSeconds({
  required String status,
  int gameTimeSeconds = 0,
  DateTime? clockRunningSince,
  int cap = 900,
  DateTime? now,
}) {
  var base = gameTimeSeconds;
  if (status == 'LIVE' && clockRunningSince != null) {
    base += (now ?? DateTime.now()).difference(clockRunningSince).inSeconds;
  }
  if (base < 0) return 0;
  return base > cap ? cap : base;
}

int matchRemainingSeconds({
  required String status,
  int gameTimeSeconds = 0,
  DateTime? clockRunningSince,
  int cap = 900,
  DateTime? now,
}) {
  return cap -
      matchElapsedSeconds(
        status: status,
        gameTimeSeconds: gameTimeSeconds,
        clockRunningSince: clockRunningSince,
        cap: cap,
        now: now,
      );
}

String formatClock(int? totalSeconds) {
  final safe = totalSeconds == null || totalSeconds < 0 ? 0 : totalSeconds;
  final minutes = safe ~/ 60;
  final seconds = safe % 60;
  return '$minutes:${seconds.toString().padLeft(2, '0')}';
}

String periodLabel(int? period, {String? sportCode, int periodCount = 2}) {
  if (period == null || period < 1) return 'Матч не начат';
  if (period > periodCount) return 'Доп. время';
  if (sportCode == 'BASKETBALL') return '$period-я четверть';
  if (sportCode == 'VOLLEYBALL') return '$period-я партия';
  return '$period-й тайм';
}

String playerTag(String? name, int? jersey) {
  if ((name == null || name.isEmpty) && jersey == null) return '';
  if (jersey == null) return name ?? '';
  return '#$jersey ${name ?? ''}'.trim();
}

String? matchOutcome({
  required String status,
  required String homeTeamId,
  required String awayTeamId,
  required int homeScore,
  required int awayScore,
  String? teamId,
}) {
  if (teamId == null || status != 'FINISHED') return null;
  final home = teamId == homeTeamId;
  if (!home && teamId != awayTeamId) return null;
  final scored = home ? homeScore : awayScore;
  final conceded = home ? awayScore : homeScore;
  if (scored > conceded) return 'WIN';
  if (scored < conceded) return 'LOSS';
  return 'DRAW';
}

const outcomeMark = {'WIN': 'В', 'DRAW': 'Н', 'LOSS': 'П'};

String ageLine(DateTime? date) {
  if (date == null) return '';
  final now = DateTime.now();
  var age = now.year - date.year;
  if (now.month < date.month || (now.month == date.month && now.day < date.day)) {
    age -= 1;
  }
  final day = date.day.toString().padLeft(2, '0');
  final month = date.month.toString().padLeft(2, '0');
  return 'Возр: $age ($day.$month.${date.year})';
}

String matchDayShort(DateTime? at) {
  if (at == null) return '';
  final local = at.toLocal();
  final day = local.day.toString().padLeft(2, '0');
  final month = local.month.toString().padLeft(2, '0');
  return '$day.$month.';
}
