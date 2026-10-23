import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/medications/medication.dart';

void main() {
  test('hosted HTTP and release local HTTP are rejected', () {
    for (final url in [
      'http://example.invalid/api/v1',
      'https://user:password@example.invalid/api/v1',
      'https://example.invalid/api/v1?x=1',
    ]) {
      expect(
        () => ApiClient.parseBaseUrl(url, allowLocalHttp: true),
        throwsArgumentError,
      );
    }
    expect(
      () => ApiClient.parseBaseUrl(
        'http://10.0.2.2:8080/api/v1',
        allowLocalHttp: false,
      ),
      throwsArgumentError,
    );
    expect(
      ApiClient.parseBaseUrl(
        'http://10.0.2.2:8080/api/v1',
        allowLocalHttp: true,
      ).host,
      '10.0.2.2',
    );
  });
  test('maps server field errors without showing raw sensitive text', () async {
    final api = ApiClient(
      MockClient(
        (_) async => http.Response(
          '{"code":"VALIDATION_ERROR","message":"sensitive","requestId":"request-1","fieldErrors":{"email":"sensitive"}}',
          422,
        ),
      ),
      Uri.parse('https://example.invalid/api/v1'),
    );
    try {
      await api.request('POST', '/auth/login');
      fail('expected failure');
    } on ApiFailure catch (e) {
      expect(e.status, 422);
      expect(e.requestId, 'request-1');
      expect(e.message, isNot(contains('sensitive')));
      expect(e.fields['email'], isNot(contains('sensitive')));
    }
  });
  test('invalid response maps to a safe error', () async {
    final api = ApiClient(
      MockClient((_) async => http.Response('not json', 200)),
      Uri.parse('https://example.invalid/api/v1'),
    );
    await expectLater(
      api.request('GET', '/medications'),
      throwsA(isA<ApiFailure>()),
    );
  });
  test('decimal quantities retain exact text and unknown is distinct', () {
    final item = Medication.fromJson({
      'id': 'fictional',
      'name': 'Example',
      'unit': 'ml',
      'initialStock': '0.123456',
    });
    expect(item.initialStock, '0.123456');
    expect(
      Medication.fromJson({
        'id': 'fictional',
        'name': 'Example',
        'unit': 'ml',
        'initialStock': null,
      }).initialStock,
      isNull,
    );
    expect(
      const Tokens('secret-access', 'secret-refresh').toString(),
      isNot(contains('secret')),
    );
  });
}
