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
    this.sportCode,
    this.gameTimeSeconds,
    this.periodLengthSeconds = 1200,
    this.clockRunningSince,
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
  final String? sportCode;
  final int? gameTimeSeconds;
  final int periodLengthSeconds;
  final DateTime? clockRunningSince;

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
      sportCode: json['sportCode']?.toString(),
      gameTimeSeconds: (json['gameTimeSeconds'] as num?)?.toInt(),
      periodLengthSeconds: (json['periodLengthSeconds'] as num?)?.toInt() ?? 1200,
      clockRunningSince: parseTime(json['clockRunningSince']),
    );
  }

  bool get isLive => status == 'LIVE' || status == 'PAUSED';
  bool get isFinished => status == 'FINISHED' || status == 'CANCELLED';
}

class TeamBrief {
  TeamBrief({required this.id, required this.name, this.shortName, this.logoUrl});
  final String id;
  final String name;
  final String? shortName;
  final String? logoUrl;

  factory TeamBrief.fromJson(Map<String, dynamic> json) {
    return TeamBrief(
      id: json['id'].toString(),
      name: json['name']?.toString() ?? 'Команда',
      shortName: json['shortName']?.toString(),
      logoUrl: json['logoUrl']?.toString(),
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
    this.secondaryPlayerName,
    this.secondaryPlayerJersey,
    this.gameTime,
    this.period,
    this.voided = false,
  });

  final String id;
  final String eventType;
  final String teamId;
  final String? playerName;
  final int? playerJersey;
  final String? secondaryPlayerName;
  final int? secondaryPlayerJersey;
  final int? gameTime;
  final int? period;
  final bool voided;

  factory MatchEvent.fromJson(Map<String, dynamic> json) {
    return MatchEvent(
      id: json['id'].toString(),
      eventType: json['eventType']?.toString() ?? 'OTHER',
      teamId: json['teamId']?.toString() ?? '',
      playerName: json['playerName']?.toString(),
      playerJersey: (json['playerJersey'] as num?)?.toInt(),
      secondaryPlayerName: json['secondaryPlayerName']?.toString(),
      secondaryPlayerJersey: (json['secondaryPlayerJersey'] as num?)?.toInt(),
      gameTime: (json['gameTime'] as num?)?.toInt() ?? (json['gameTimeSeconds'] as num?)?.toInt(),
      period: (json['period'] as num?)?.toInt(),
      voided: json['voided'] == true,
    );
  }

  String get label {
    final name = playerName ?? eventType;
    if (playerJersey != null) return '#$playerJersey $name';
    return name;
  }
}

class LineupPlayer {
  LineupPlayer({required this.playerId, required this.name, this.jerseyNumber, this.position});
  final String playerId;
  final String name;
  final int? jerseyNumber;
  final String? position;

  factory LineupPlayer.fromJson(Map<String, dynamic> json) {
    return LineupPlayer(
      playerId: json['playerId'].toString(),
      name: json['name']?.toString() ?? 'Игрок',
      jerseyNumber: (json['jerseyNumber'] as num?)?.toInt(),
      position: json['position']?.toString(),
    );
  }
}

class TeamLineup {
  TeamLineup({
    required this.teamId,
    required this.teamName,
    required this.confirmed,
    required this.starters,
    required this.bench,
  });

  final String teamId;
  final String teamName;
  final bool confirmed;
  final List<LineupPlayer> starters;
  final List<LineupPlayer> bench;

  factory TeamLineup.fromJson(Map<String, dynamic> json) {
    List<LineupPlayer> parse(dynamic raw) =>
        ((raw as List?) ?? const []).whereType<Map>().map((item) => LineupPlayer.fromJson(Map<String, dynamic>.from(item))).toList();
    return TeamLineup(
      teamId: json['teamId']?.toString() ?? '',
      teamName: json['teamName']?.toString() ?? 'Команда',
      confirmed: json['confirmed'] == true,
      starters: parse(json['starters']),
      bench: parse(json['bench']),
    );
  }
}

class MatchLineups {
  MatchLineups({this.home, this.away});
  final TeamLineup? home;
  final TeamLineup? away;

  factory MatchLineups.fromJson(Map<String, dynamic> json) {
    TeamLineup? side(dynamic raw) => raw is Map ? TeamLineup.fromJson(Map<String, dynamic>.from(raw)) : null;
    return MatchLineups(home: side(json['home']), away: side(json['away']));
  }
}

class TeamMember {
  TeamMember({required this.playerId, required this.displayName, this.jerseyNumber, this.position});
  final String playerId;
  final String displayName;
  final int? jerseyNumber;
  final String? position;

  factory TeamMember.fromJson(Map<String, dynamic> json) {
    final first = json['playerFirstName']?.toString() ?? '';
    final last = json['playerLastName']?.toString() ?? '';
    final display = json['displayName']?.toString();
    return TeamMember(
      playerId: json['playerId'].toString(),
      displayName: (display != null && display.isNotEmpty) ? display : '$first $last'.trim(),
      jerseyNumber: (json['jerseyNumber'] as num?)?.toInt(),
      position: json['position']?.toString(),
    );
  }
}

class PlayerBrief {
  PlayerBrief({
    required this.id,
    required this.displayName,
    this.avatarUrl,
    this.jerseyNumber,
    this.position,
  });

  final String id;
  final String displayName;
  final String? avatarUrl;
  final int? jerseyNumber;
  final String? position;

  factory PlayerBrief.fromJson(Map<String, dynamic> json) {
    return PlayerBrief(
      id: json['id'].toString(),
      displayName: json['displayName']?.toString() ??
          '${json['firstName'] ?? ''} ${json['lastName'] ?? ''}'.trim(),
      avatarUrl: json['avatarUrl']?.toString(),
      jerseyNumber: (json['jerseyNumber'] as num?)?.toInt(),
      position: json['position']?.toString(),
    );
  }
}

class PlayerProfile {
  PlayerProfile({
    this.id,
    this.firstName = '',
    this.lastName = '',
    this.displayName = '',
    this.jerseyNumber,
    this.position = '',
    this.bio = '',
    this.avatarUrl,
    this.dateOfBirth,
  });

  final String? id;
  String firstName;
  String lastName;
  String displayName;
  int? jerseyNumber;
  String position;
  String bio;
  final String? avatarUrl;
  final DateTime? dateOfBirth;

  factory PlayerProfile.fromJson(Map<String, dynamic> json) {
    return PlayerProfile(
      id: json['id']?.toString(),
      firstName: json['firstName']?.toString() ?? '',
      lastName: json['lastName']?.toString() ?? '',
      displayName: json['displayName']?.toString() ?? '',
      jerseyNumber: (json['jerseyNumber'] as num?)?.toInt(),
      position: json['position']?.toString() ?? '',
      bio: json['bio']?.toString() ?? '',
      avatarUrl: json['avatarUrl']?.toString(),
      dateOfBirth: parseTime(json['dateOfBirth']),
    );
  }

  Map<String, dynamic> toRequest() => {
        'firstName': firstName,
        'lastName': lastName,
        if (displayName.isNotEmpty) 'displayName': displayName,
        if (jerseyNumber != null) 'jerseyNumber': jerseyNumber,
        if (position.isNotEmpty) 'position': position,
        if (bio.isNotEmpty) 'bio': bio,
      };
}

class PlayerCard {
  PlayerCard({
    required this.id,
    required this.displayName,
    this.avatarUrl,
    this.jerseyNumber,
    this.position,
    this.dateOfBirth,
    this.teamId,
    this.teamName,
    this.teamLogoUrl,
    this.statistics = const {},
    this.history = const [],
  });

  final String id;
  final String displayName;
  final String? avatarUrl;
  final int? jerseyNumber;
  final String? position;
  final DateTime? dateOfBirth;
  final String? teamId;
  final String? teamName;
  final String? teamLogoUrl;
  final Map<String, dynamic> statistics;
  final List<PlayerMatchHistory> history;

  factory PlayerCard.fromJson(Map<String, dynamic> json) {
    final team = json['team'];
    return PlayerCard(
      id: json['id'].toString(),
      displayName: json['displayName']?.toString() ??
          '${json['firstName'] ?? ''} ${json['lastName'] ?? ''}'.trim(),
      avatarUrl: json['avatarUrl']?.toString(),
      jerseyNumber: (json['jerseyNumber'] as num?)?.toInt(),
      position: json['position']?.toString(),
      dateOfBirth: parseTime(json['dateOfBirth']),
      teamId: team is Map ? team['id']?.toString() : null,
      teamName: team is Map ? team['name']?.toString() : null,
      teamLogoUrl: team is Map ? team['logoUrl']?.toString() : null,
      statistics: json['statistics'] is Map ? Map<String, dynamic>.from(json['statistics'] as Map) : const {},
      history: ((json['matchHistory'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => PlayerMatchHistory.fromJson(Map<String, dynamic>.from(item)))
          .toList(),
    );
  }
}

class PlayerMatchHistory {
  PlayerMatchHistory({
    required this.matchId,
    required this.opponentName,
    required this.homeScore,
    required this.awayScore,
    required this.outcome,
    required this.goals,
    required this.assists,
    this.homeTeamName,
    this.awayTeamName,
    this.homeTeamLogoUrl,
    this.awayTeamLogoUrl,
    this.home = true,
    this.yellowCards = 0,
    this.redCards = 0,
    this.minutesPlayed,
    this.scheduledAt,
  });

  final String matchId;
  final String opponentName;
  final String? homeTeamName;
  final String? awayTeamName;
  final String? homeTeamLogoUrl;
  final String? awayTeamLogoUrl;
  final bool home;
  final int homeScore;
  final int awayScore;
  final String? outcome;
  final int goals;
  final int assists;
  final int yellowCards;
  final int redCards;
  final int? minutesPlayed;
  final DateTime? scheduledAt;

  factory PlayerMatchHistory.fromJson(Map<String, dynamic> json) {
    return PlayerMatchHistory(
      matchId: json['matchId'].toString(),
      opponentName: json['opponentName']?.toString() ?? 'Соперник',
      homeTeamName: json['homeTeamName']?.toString(),
      awayTeamName: json['awayTeamName']?.toString(),
      homeTeamLogoUrl: json['homeTeamLogoUrl']?.toString(),
      awayTeamLogoUrl: json['awayTeamLogoUrl']?.toString(),
      home: json['home'] == true,
      homeScore: (json['homeScore'] as num?)?.toInt() ?? 0,
      awayScore: (json['awayScore'] as num?)?.toInt() ?? 0,
      outcome: json['outcome']?.toString(),
      goals: (json['goals'] as num?)?.toInt() ?? 0,
      assists: (json['assists'] as num?)?.toInt() ?? 0,
      yellowCards: (json['yellowCards'] as num?)?.toInt() ?? 0,
      redCards: (json['redCards'] as num?)?.toInt() ?? 0,
      minutesPlayed: (json['minutesPlayed'] as num?)?.toInt(),
      scheduledAt: parseTime(json['scheduledAt']),
    );
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
