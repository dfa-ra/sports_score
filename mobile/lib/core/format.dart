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
