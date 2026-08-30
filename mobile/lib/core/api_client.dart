import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiException implements Exception {
  ApiException(this.message, {this.status});
  final String message;
  final int? status;

  @override
  String toString() => message;
}

class ApiClient {
  ApiClient({String? baseUrl, http.Client? httpClient})
      : baseUrl = baseUrl ??
            const String.fromEnvironment(
              'API_BASE_URL',
              defaultValue: 'http://127.0.0.1:8080/api/v1',
            ),
        _http = httpClient ?? http.Client();

  final String baseUrl;
  final http.Client _http;
  String? accessToken;

  Uri _uri(String path, [Map<String, String>? query]) {
    final normalized = path.startsWith('/') ? path : '/$path';
    return Uri.parse('$baseUrl$normalized').replace(queryParameters: query);
  }

  Map<String, String> _headers() => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (accessToken != null) 'Authorization': 'Bearer $accessToken',
      };

  Future<dynamic> get(String path, {Map<String, String>? query}) async {
    final response = await _http.get(_uri(path, query), headers: _headers()).timeout(const Duration(seconds: 8));
    return _decode(response);
  }

  Future<dynamic> post(String path, [Map<String, dynamic>? body]) async {
    final response = await _http
        .post(_uri(path), headers: _headers(), body: jsonEncode(body ?? {}))
        .timeout(const Duration(seconds: 8));
    return _decode(response);
  }

  Future<dynamic> put(String path, [Map<String, dynamic>? body]) async {
    final response = await _http
        .put(_uri(path), headers: _headers(), body: jsonEncode(body ?? {}))
        .timeout(const Duration(seconds: 8));
    return _decode(response);
  }

  dynamic _decode(http.Response response) {
    if (response.statusCode >= 400) {
      String message = 'Ошибка ${response.statusCode}';
      try {
        final parsed = jsonDecode(response.body);
        if (parsed is Map && parsed['message'] is String) {
          message = parsed['message'] as String;
        }
      } catch (_) {}
      throw ApiException(message, status: response.statusCode);
    }
    if (response.body.isEmpty) return null;
    return jsonDecode(response.body);
  }
}
