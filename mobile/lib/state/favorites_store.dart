import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class FavoritesStore extends ChangeNotifier {
  FavoritesStore({this.persist = true, FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const key = 'kb_favorites';

  final bool persist;
  final FlutterSecureStorage _storage;
  final Set<String> teams = {};
  final Set<String> matches = {};

  int get count => teams.length + matches.length;
  bool hasTeam(String? id) => id != null && teams.contains(id);
  bool hasMatch(String? id) => id != null && matches.contains(id);

  Future<void> load() async {
    if (!persist) return;
    try {
      final raw = await _storage.read(key: key);
      if (raw == null || raw.isEmpty) return;
      final parsed = jsonDecode(raw);
      if (parsed is! Map) return;
      teams
        ..clear()
        ..addAll(((parsed['teams'] as List?) ?? const []).map((item) => item.toString()));
      matches
        ..clear()
        ..addAll(((parsed['matches'] as List?) ?? const []).map((item) => item.toString()));
      notifyListeners();
    } catch (_) {}
  }

  Future<void> toggleTeam(String id) async {
    if (!teams.remove(id)) teams.add(id);
    notifyListeners();
    await _save();
  }

  Future<void> toggleMatch(String id) async {
    if (!matches.remove(id)) matches.add(id);
    notifyListeners();
    await _save();
  }

  Future<void> _save() async {
    if (!persist) return;
    await _storage.write(
      key: key,
      value: jsonEncode({'teams': teams.toList(), 'matches': matches.toList()}),
    );
  }
}
