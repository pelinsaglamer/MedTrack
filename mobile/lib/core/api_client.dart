import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiFailure implements Exception {
  const ApiFailure(
    this.code, {
    this.status,
    this.requestId,
    this.fields = const {},
  });
  final String code;
  final int? status;
  final String? requestId;
  final Map<String, String> fields;
  String get message => switch (code) {
    'INVALID_CREDENTIALS' => 'E-posta veya şifre hatalı. Yeniden deneyin.',
    'UNAUTHORIZED' => 'Oturumunuz sona erdi. Yeniden giriş yapın.',
    'RATE_LIMITED' =>
      'Çok fazla deneme yapıldı. Bir süre sonra tekrar deneyin.',
    'VALIDATION_ERROR' => 'Girdiğiniz alanları kontrol edin.',
    'NETWORK' =>
      'Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.',
    'SECURE_STORAGE' => 'Güvenli oturum kaydına erişilemiyor. Tekrar deneyin.',
    _ => 'İşlem tamamlanamadı. Lütfen tekrar deneyin.',
  };
  @override
  String toString() => 'ApiFailure($code, $status)';
}

class ApiClient {
  ApiClient(this.client, this.baseUrl);
  final http.Client client;
  final Uri baseUrl;
  static Uri parseBaseUrl(String value, {required bool allowLocalHttp}) {
    final uri = Uri.parse(value);
    final local = {'localhost', '127.0.0.1', '10.0.2.2'}.contains(uri.host);
    if (!uri.hasAuthority ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        uri.path != '/api/v1' ||
        (uri.scheme != 'https' &&
            !(allowLocalHttp && local && uri.scheme == 'http'))) {
      throw ArgumentError(
        'API_BASE_URL must be HTTPS and end in /api/v1; local HTTP is debug-only.',
      );
    }
    return uri;
  }

  Future<Map<String, dynamic>> request(
    String method,
    String path, {
    Map<String, dynamic>? body,
    String? token,
  }) async {
    try {
      final request = http.Request(method, Uri.parse('$baseUrl$path'));
      request.followRedirects = false;
      request.headers['Accept'] = 'application/json';
      if (token != null) request.headers['Authorization'] = 'Bearer $token';
      if (body != null) {
        request.headers['Content-Type'] = 'application/json';
        request.body = jsonEncode(body);
      }
      final response = await client
          .send(request)
          .then(http.Response.fromStream)
          .timeout(const Duration(seconds: 20));
      Map<String, dynamic> data = {};
      if (response.body.isNotEmpty) {
        try {
          data = jsonDecode(
            utf8.decode(response.bodyBytes),
          ) as Map<String, dynamic>;
        } on FormatException {
          throw const ApiFailure('INVALID_RESPONSE');
        } on TypeError {
          throw const ApiFailure('INVALID_RESPONSE');
        }
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiFailure(
          data['code'] as String? ?? 'SERVER_ERROR',
          status: response.statusCode,
          requestId: data['requestId'] as String?,
          fields: (data['fieldErrors'] as Map<String, dynamic>? ?? {}).map(
            (key, value) => MapEntry(key, 'Bu alanı kontrol edin.'),
          ),
        );
      }
      return data;
    } on TimeoutException {
      throw const ApiFailure('NETWORK');
    } on http.ClientException {
      throw const ApiFailure('NETWORK');
    }
  }
}
