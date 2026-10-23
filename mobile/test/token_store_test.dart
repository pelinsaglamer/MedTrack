import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medtrack/core/token_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('secure session roundtrip and clear', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final store = SecureTokenStore(const FlutterSecureStorage());
    expect(await store.read(), isNull);
    await store.write(const Tokens('fictional-access', 'fictional-refresh'));
    final restored = await store.read();
    expect(restored!.accessToken, 'fictional-access');
    expect(restored.refreshToken, 'fictional-refresh');
    await store.clear();
    expect(await store.read(), isNull);
  });
  test('malformed secure records are cleared so login can recover', () async {
    for (final value in ['invalid-json', '{"accessToken":3}', '[]']) {
      FlutterSecureStorage.setMockInitialValues({'medtrack.session.v1': value});
      final store = SecureTokenStore(const FlutterSecureStorage());
      expect(await store.read(), isNull);
      expect(await const FlutterSecureStorage().readAll(), isEmpty);
    }
  });
}
