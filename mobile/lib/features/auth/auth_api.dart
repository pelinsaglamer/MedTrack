import '../../core/api_client.dart';
import '../../core/token_store.dart';

class AuthApi {
  const AuthApi(this.api);
  final ApiClient api;
  Future<Tokens> login(String email, String password) async => Tokens.fromJson(
    await api.request(
      'POST',
      '/auth/login',
      body: {'email': email.trim(), 'password': password},
    ),
  );
  Future<Tokens> refresh(String token) async => Tokens.fromJson(
    await api.request('POST', '/auth/refresh', body: {'refreshToken': token}),
  );
  Future<void> logout(String token) async {
    await api.request('POST', '/auth/logout', body: {'refreshToken': token});
  }
}
