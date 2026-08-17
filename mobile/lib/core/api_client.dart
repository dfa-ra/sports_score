class ApiClient {
  ApiClient({this.baseUrl = const String.fromEnvironment('API_BASE_URL', defaultValue: 'http://localhost:8080/api/v1')});

  final String baseUrl;
  String? accessToken;

  Map<String, String> headers() => {
        'Content-Type': 'application/json',
        if (accessToken != null) 'Authorization': 'Bearer $accessToken',
      };
}
