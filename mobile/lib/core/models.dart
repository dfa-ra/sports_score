DateTime? parseTime(dynamic value) {
  if (value == null) return null;
  return DateTime.tryParse(value.toString());
}

class LeagueMatch {
  LeagueMatch({
    required this.id,
    required this.tournamentId,
    required this.homeTeamId,
    required this.awayTeamId,
    required this.status,
    required this.homeScore,
    required this.awayScore,
    this.scheduledAt,
    this.period,
    this.periodCount = 2,
  });

  final String id;
  final String tournamentId;
  final String homeTeamId;
  final String awayTeamId;
  final String status;
  final int homeScore;
  final int awayScore;
  final DateTime? scheduledAt;
  final int? period;
  final int periodCount;

  factory LeagueMatch.fromJson(Map<String, dynamic> json) {
    return LeagueMatch(
      id: json['id'].toString(),
      tournamentId: json['tournamentId']?.toString() ?? '',
      homeTeamId: json['homeTeamId'].toString(),
      awayTeamId: json['awayTeamId'].toString(),
      status: json['status']?.toString() ?? 'SCHEDULED',
      homeScore: (json['homeScore'] as num?)?.toInt() ?? 0,
      awayScore: (json['awayScore'] as num?)?.toInt() ?? 0,
      scheduledAt: parseTime(json['scheduledAt']),
      period: (json['period'] as num?)?.toInt(),
      periodCount: (json['periodCount'] as num?)?.toInt() ?? 2,
    );
  }

  bool get isLive => status == 'LIVE' || status == 'PAUSED';
  bool get isFinished => status == 'FINISHED' || status == 'CANCELLED';
}

class TeamBrief {
  TeamBrief({required this.id, required this.name, this.shortName});
  final String id;
  final String name;
  final String? shortName;

  factory TeamBrief.fromJson(Map<String, dynamic> json) {
    return TeamBrief(
      id: json['id'].toString(),
      name: json['name']?.toString() ?? 'Команда',
      shortName: json['shortName']?.toString(),
    );
  }
}

class TournamentBrief {
  TournamentBrief({required this.id, required this.name, this.startsOn});
  final String id;
  final String name;
  final DateTime? startsOn;

  factory TournamentBrief.fromJson(Map<String, dynamic> json) {
    return TournamentBrief(
      id: json['id'].toString(),
      name: json['name']?.toString() ?? 'Турнир',
      startsOn: parseTime(json['startsOn'] ?? json['startDate']),
    );
  }
}

class StandingRow {
  StandingRow({
    required this.teamId,
    required this.teamName,
    required this.played,
    required this.wins,
    required this.draws,
    required this.losses,
    required this.goalsFor,
    required this.goalsAgainst,
    required this.points,
  });

  final String teamId;
  final String teamName;
  final int played;
  final int wins;
  final int draws;
  final int losses;
  final int goalsFor;
  final int goalsAgainst;
  final int points;

  factory StandingRow.fromJson(Map<String, dynamic> json) {
    return StandingRow(
      teamId: json['teamId'].toString(),
      teamName: json['teamName']?.toString() ?? 'Команда',
      played: (json['played'] as num?)?.toInt() ?? 0,
      wins: (json['wins'] as num?)?.toInt() ?? 0,
      draws: (json['draws'] as num?)?.toInt() ?? 0,
      losses: (json['losses'] as num?)?.toInt() ?? 0,
      goalsFor: (json['goalsFor'] as num?)?.toInt() ?? 0,
      goalsAgainst: (json['goalsAgainst'] as num?)?.toInt() ?? 0,
      points: (json['points'] as num?)?.toInt() ?? 0,
    );
  }
}

class PlayerStat {
  PlayerStat({required this.playerId, required this.displayName, required this.value, this.appearances = 0});
  final String playerId;
  final String displayName;
  final int value;
  final int appearances;

  factory PlayerStat.fromJson(Map<String, dynamic> json, String valueKey) {
    return PlayerStat(
      playerId: json['playerId'].toString(),
      displayName: json['displayName']?.toString() ?? 'Игрок',
      value: (json[valueKey] as num?)?.toInt() ?? 0,
      appearances: (json['appearances'] as num?)?.toInt() ?? 0,
    );
  }
}

class MatchEvent {
  MatchEvent({
    required this.id,
    required this.eventType,
    required this.teamId,
    this.playerName,
    this.playerJersey,
    this.gameTime,
    this.period,
  });

  final String id;
  final String eventType;
  final String teamId;
  final String? playerName;
  final int? playerJersey;
  final int? gameTime;
  final int? period;

  factory MatchEvent.fromJson(Map<String, dynamic> json) {
    return MatchEvent(
      id: json['id'].toString(),
      eventType: json['eventType']?.toString() ?? 'OTHER',
      teamId: json['teamId']?.toString() ?? '',
      playerName: json['playerName']?.toString(),
      playerJersey: (json['playerJersey'] as num?)?.toInt(),
      gameTime: (json['gameTime'] as num?)?.toInt(),
      period: (json['period'] as num?)?.toInt(),
    );
  }

  String get label {
    final name = playerName ?? eventType;
    if (playerJersey != null) return '#$playerJersey $name';
    return name;
  }
}

class AuthUser {
  AuthUser({
    required this.id,
    required this.email,
    required this.role,
    this.firstName,
    this.lastName,
    this.photoUrl,
    this.roles = const [],
  });

  final String id;
  final String email;
  final String role;
  final String? firstName;
  final String? lastName;
  final String? photoUrl;
  final List<String> roles;

  String get displayName {
    final full = '${firstName ?? ''} ${lastName ?? ''}'.trim();
    return full.isEmpty ? email : full;
  }

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    final assignments = <String>[];
    final raw = json['roles'];
    if (raw is List) {
      for (final item in raw) {
        if (item is Map && item['status'] == 'APPROVED' && item['role'] != null) {
          assignments.add(item['role'].toString());
        }
      }
    }
    return AuthUser(
      id: json['id'].toString(),
      email: json['email']?.toString() ?? '',
      role: json['role']?.toString() ?? 'FAN',
      firstName: json['firstName']?.toString(),
      lastName: json['lastName']?.toString(),
      photoUrl: json['photoUrl']?.toString(),
      roles: assignments.isEmpty ? [json['role']?.toString() ?? 'FAN'] : assignments,
    );
  }
}
