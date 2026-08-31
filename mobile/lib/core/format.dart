const roleLabels = {
  'FAN': 'Зритель',
  'PLAYER': 'Игрок',
  'CAPTAIN': 'Капитан',
  'REFEREE': 'Судья',
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

String shortKickoff(DateTime? at, String status) {
  if (status == 'LIVE' || status == 'PAUSED') return 'LIVE';
  if (at == null) return '—';
  final d = at.toLocal();
  final day = d.day.toString().padLeft(2, '0');
  final month = d.month.toString().padLeft(2, '0');
  if (status == 'FINISHED' || status == 'CANCELLED') return '$day.$month.';
  final h = d.hour.toString().padLeft(2, '0');
  final min = d.minute.toString().padLeft(2, '0');
  return '$h:$min';
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

int eventMinute(int? gameTime) {
  final raw = gameTime ?? 0;
  if (raw < 0) return 0;
  return raw >= 120 ? raw ~/ 60 : raw;
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
  'ASSIST': 'Голевая',
  'YELLOW_CARD': 'Жёлтая',
  'RED_CARD': 'Красная',
  'SUBSTITUTION': 'Замена',
  'POINT': 'Очко',
  'FOUL': 'Фол',
  'PERIOD_START': 'Начало тайма',
  'PERIOD_END': 'Конец тайма',
  'OTHER': 'Событие',
};

String eventLabel(String? type) => eventLabels[type] ?? type ?? 'Событие';

String formatClock(int? totalSeconds) {
  final safe = totalSeconds == null || totalSeconds < 0 ? 0 : totalSeconds;
  final minutes = safe ~/ 60;
  final seconds = safe % 60;
  return '$minutes:${seconds.toString().padLeft(2, '0')}';
}

String periodLabel(int? period, {String? sportCode, int periodCount = 2}) {
  if (period == null || period < 1) return 'Ещё не свистнули';
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
