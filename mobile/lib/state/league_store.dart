import 'dart:async';

import 'package:flutter/foundation.dart';

import '../core/api_client.dart';
import '../core/format.dart';
import '../core/models.dart';

class LeagueStore extends ChangeNotifier {
  LeagueStore(this.api);

  final ApiClient api;

  final Map<String, TeamBrief> teams = {};
  final Map<String, String> tournamentNames = {};
  List<TournamentBrief> tournaments = [];
  List<LeagueMatch> matches = [];
  List<StandingTable> standingTables = [];
  List<PlayerStat> scorers = [];
  List<PlayerStat> assists = [];
  List<PlayerStat> keepers = [];
  String? tournamentId;
  String? tournamentName;
  bool loading = false;
  String? error;

  String teamName(String? id) {
    if (id == null) return 'Команда';
    return teams[id]?.name ?? 'Команда';
  }

  String? teamLogo(String? id) {
    if (id == null) return null;
    return api.resolveMedia(teams[id]?.logoUrl);
  }

  Future<void> load() async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      final results = await Future.wait([
        api.get('/teams', query: {'size': '200', 'includeDisbanded': 'true'}),
        api.get('/matches', query: {'size': '100', 'sort': 'scheduledAt,desc'}),
        api.get('/tournaments', query: {'size': '50'}),
      ]);
      final teamPage = results[0] as Map;
      final matchPage = results[1] as Map;
      final tourPage = results[2] as Map;
      teams
        ..clear()
        ..addEntries(
          ((teamPage['content'] as List?) ?? const [])
              .whereType<Map>()
              .map((item) => TeamBrief.fromJson(Map<String, dynamic>.from(item)))
              .map((team) => MapEntry(team.id, team)),
        );
      matches = ((matchPage['content'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => LeagueMatch.fromJson(Map<String, dynamic>.from(item)))
          .toList();
      tournaments = ((tourPage['content'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => TournamentBrief.fromJson(Map<String, dynamic>.from(item)))
          .toList();
      tournamentNames
        ..clear()
        ..addEntries(tournaments.map((t) => MapEntry(t.id, t.name)));
      try {
        final current = await api.get('/tournaments/current');
        if (current is Map && current['id'] != null) {
          tournamentId = current['id'].toString();
        }
      } catch (_) {}
      tournamentId ??= tournaments.isEmpty ? null : tournaments.first.id;
      await loadTournament();
    } on TimeoutException {
      error = 'Сервер не ответил: ${api.baseUrl}';
    } catch (e) {
      error = e is ApiException ? e.message : 'Не удалось загрузить лигу с ${api.baseUrl}';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<void> selectTournament(String id) async {
    tournamentId = id;
    await loadTournament();
    notifyListeners();
  }

  Future<void> loadTournament() async {
    if (tournamentId == null) return;
    try {
      final results = await Future.wait([
        api.get('/tournaments/$tournamentId'),
        api.get('/tournaments/$tournamentId/standings'),
        api.get('/statistics/board', query: {'tournamentId': tournamentId!, 'limit': '50'}),
      ]);
      if (results[0] is Map) {
        tournamentName = (results[0] as Map)['name']?.toString();
      }
      standingTables = StandingTable.parse(results[1])
          .map(
            (table) => StandingTable(
              id: table.id,
              name: table.name,
              rows: [...table.rows]..sort(StandingTable.compareRows),
            ),
          )
          .toList();
      final board = results[2] is Map ? Map<String, dynamic>.from(results[2] as Map) : const <String, dynamic>{};
      scorers = ((board['scorers'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => PlayerStat.fromJson(Map<String, dynamic>.from(item), 'goals'))
          .toList();
      assists = ((board['assists'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => PlayerStat.fromJson(Map<String, dynamic>.from(item), 'assists'))
          .toList();
      keepers = ((board['goalkeepers'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) => PlayerStat.fromJson(Map<String, dynamic>.from(item), 'cleanSheets'))
          .toList();
    } catch (e) {
      error = e is ApiException ? e.message : 'Турнир не загрузился.';
    }
  }

  List<LeagueMatch> liveMatches() => matches.where((m) => m.isLive).toList();

  List<LeagueMatch> upcomingMatches() {
    final list = matches.where((m) => m.status == 'SCHEDULED' || m.isLive).toList();
    list.sort((a, b) => (a.scheduledAt ?? DateTime(2100)).compareTo(b.scheduledAt ?? DateTime(2100)));
    return list;
  }

  List<LeagueMatch> dayMatches(String dayKey) {
    return matches.where((m) => m.scheduledAt != null && ymd(m.scheduledAt!) == dayKey).toList()
      ..sort((a, b) => (a.scheduledAt ?? DateTime(2100)).compareTo(b.scheduledAt ?? DateTime(2100)));
  }

  List<LeagueMatch> tournamentResults() {
    return matches
        .where((m) => m.tournamentId == tournamentId && m.isFinished)
        .toList()
      ..sort((a, b) => (b.scheduledAt ?? DateTime(0)).compareTo(a.scheduledAt ?? DateTime(0)));
  }

  Map<String, List<LeagueMatch>> grouped(List<LeagueMatch> items) {
    final map = <String, List<LeagueMatch>>{};
    for (final match in items) {
      map.putIfAbsent(match.tournamentId, () => []).add(match);
    }
    return map;
  }

  LeagueMatch? findMatch(String id) {
    for (final match in matches) {
      if (match.id == id) return match;
    }
    return null;
  }

  Future<LeagueMatch?> matchById(String id) async {
    final cached = findMatch(id);
    if (cached != null) return cached;
    try {
      final data = await api.get('/matches/$id');
      if (data is Map) return LeagueMatch.fromJson(Map<String, dynamic>.from(data));
    } catch (_) {}
    return null;
  }

  Future<List<MatchEvent>> eventsFor(String matchId) async {
    final data = await api.get('/matches/$matchId/events');
    return ((data as List?) ?? const [])
        .whereType<Map>()
        .map((item) => MatchEvent.fromJson(Map<String, dynamic>.from(item)))
        .where((event) => !event.voided && !{'PERIOD_START', 'PERIOD_END', 'OTHER'}.contains(event.eventType))
        .toList();
  }

  Future<MatchLineups> lineupsFor(String matchId) async {
    final data = await api.get('/matches/$matchId/lineups');
    if (data is Map) return MatchLineups.fromJson(Map<String, dynamic>.from(data));
    return MatchLineups();
  }

  Future<List<LeagueMatch>> teamForm(String teamId) async {
    final data = await api.get('/teams/$teamId/form', query: {'limit': '5'});
    return ((data as List?) ?? const [])
        .whereType<Map>()
        .map((item) => LeagueMatch.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  Future<String?> captainOf(String teamId) async {
    if (teams.containsKey(teamId)) return teams[teamId]?.captainId;
    try {
      final data = await api.get('/teams/$teamId');
      if (data is Map) {
        final brief = TeamBrief.fromJson(Map<String, dynamic>.from(data));
        teams[teamId] = brief;
        return brief.captainId;
      }
    } catch (_) {}
    return null;
  }

  Future<List<TeamMember>> teamMembers(String teamId) async {
    final data = await api.get('/teams/$teamId/members');
    return ((data as List?) ?? const [])
        .whereType<Map>()
        .map((item) => TeamMember.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  List<LeagueMatch> teamMatches(String teamId) {
    return matches.where((m) => m.homeTeamId == teamId || m.awayTeamId == teamId).toList()
      ..sort((a, b) => (b.scheduledAt ?? DateTime(0)).compareTo(a.scheduledAt ?? DateTime(0)));
  }

  List<LeagueMatch> headToHead(String homeId, String awayId, {String? exceptMatchId, int limit = 5}) {
    final rows = matches
        .where(
          (m) =>
              m.isFinished &&
              m.id != exceptMatchId &&
              ((m.homeTeamId == homeId && m.awayTeamId == awayId) || (m.homeTeamId == awayId && m.awayTeamId == homeId)),
        )
        .toList()
      ..sort((a, b) => (b.scheduledAt ?? DateTime(0)).compareTo(a.scheduledAt ?? DateTime(0)));
    if (rows.length <= limit) return rows;
    return rows.sublist(0, limit);
  }
}
