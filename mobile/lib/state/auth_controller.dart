import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../core/api_client.dart';
import '../core/models.dart';

class AuthController extends ChangeNotifier {
  AuthController(this.api, {FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();

  final ApiClient api;
  final FlutterSecureStorage _storage;

  AuthUser? user;
  bool busy = false;
  String? error;

  bool get isAuthenticated => api.accessToken != null && user != null;
  bool get canOfficiate => _has('REFEREE') || _has('ADMIN');
  bool get canManageLeague => _has('ADMIN');
  bool get canAccessMyTeam => isAuthenticated && (_has('PLAYER') || _has('CAPTAIN') || _has('ADMIN') || _has('REFEREE'));

  bool _has(String role) => user?.roles.contains(role) == true || user?.role == role;

  Future<void> restore() async {
    try {
      api.accessToken = await _storage.read(key: 'sl_access');
      if (api.accessToken == null) return;
      final data = await api.get('/auth/me');
      user = AuthUser.fromJson(Map<String, dynamic>.from(data as Map));
      notifyListeners();
    } catch (_) {
      api.accessToken = null;
      user = null;
    }
  }

  Future<bool> login(String email, String password) async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      final data = await api.post('/auth/login', {'email': email.trim(), 'password': password});
      final map = Map<String, dynamic>.from(data as Map);
      api.accessToken = map['accessToken']?.toString();
      user = AuthUser.fromJson(Map<String, dynamic>.from(map['user'] as Map));
      await _storage.write(key: 'sl_access', value: api.accessToken);
      await _storage.write(key: 'sl_refresh', value: map['refreshToken']?.toString());
      if (canManageLeague) {
        await api.restoreAdminBaseUrl();
      } else {
        api.resetToCompiled();
      }
      return true;
    } catch (e) {
      error = e is ApiException ? e.message : 'Не удалось войти.';
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    try {
      final refresh = await _storage.read(key: 'sl_refresh');
      if (refresh != null) {
        await api.post('/auth/logout', {'refreshToken': refresh});
      }
    } catch (_) {
    } finally {
      api.accessToken = null;
      user = null;
      api.resetToCompiled();
      await _storage.delete(key: 'sl_access');
      await _storage.delete(key: 'sl_refresh');
      notifyListeners();
    }
  }
}
