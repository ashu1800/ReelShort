/// Regression suite for upstream error sanitization.
///
/// On device a Cloudflare 525 page used to surface as its whole JSON body in the
/// message bar: the API client passed an envelope-less body straight through and
/// the state layer echoed it. Two layers now filter it - the client drops
/// structured/oversized bodies, and the state layer substitutes shared copy when
/// what reaches it is not a sentence.
library;

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:shortlink/config/api_config.dart';
import 'package:shortlink/data/network/api_client_exception.dart';
import 'package:shortlink/data/network/http_reel_short_api_client.dart';
import 'package:shortlink/state/app_state_controller.dart';

import 'support/fake_app_data_source.dart';

/// A gateway 525 page shaped like the one seen on device.
const String _cloudflare525Json =
    '{"error":[{"code":525,"message":"SSL Handshake Failed",'
    '"ray_id":"8f2a1c9d4b7e6a01","timestamp":"2026-10-17T06:00:00Z"}],'
    '"type":"cf-error-details","host":"shortlink.hjj888.cc"}';

/// An HTML 502 page.
const String _html502 =
    '<!DOCTYPE html><html><head><title>502 Bad Gateway</title></head>'
    '<body><h1>502 Bad Gateway</h1><hr>nginx</body></html>';

/// Builds a client whose transport always answers [status] with [body].
HttpReelShortApiClient _clientFor(
  int status,
  String body, {
  String reasonPhrase = 'Bad Gateway',
}) {
  return HttpReelShortApiClient(
    config: ApiConfig('https://shortlink.test/api/app'),
    httpClient: _StubHttpClient(status, body, reasonPhrase),
    tokenProvider: () async => null,
  );
}

/// A transport that answers every request with one canned response.
class _StubHttpClient extends http.BaseClient {
  _StubHttpClient(this.status, this.body, this.reasonPhrase);

  final int status;
  final String body;
  final String reasonPhrase;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final List<int> bytes = utf8.encode(body);
    return http.StreamedResponse(
      Stream<List<int>>.value(bytes),
      status,
      reasonPhrase: reasonPhrase,
      contentLength: bytes.length,
      request: request,
    );
  }
}

/// The message key of the first error surfaced to the UI, if any.
String? _lastErrorOf(AppStateController controller) =>
    controller.state.errorMessage;

void main() {
  group('API client filtering', () {
    test('a gateway JSON body never becomes the message', () async {
      final HttpReelShortApiClient client = _clientFor(525, _cloudflare525Json);

      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>()
              .having((ApiClientException e) => e.statusCode, 'status', 525)
              .having((ApiClientException e) => e.message, 'message', isEmpty),
        ),
      );
    });

    test('an HTML error page never becomes the message', () async {
      final HttpReelShortApiClient client = _clientFor(502, _html502);
      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>()
              .having((ApiClientException e) => e.statusCode, 'status', 502)
              .having((ApiClientException e) => e.message, 'message', isEmpty),
        ),
      );
    });

    test('an oversized plain body never becomes the message', () async {
      final String huge = 'x' * (maxUserFacingMessageLength + 1);
      final HttpReelShortApiClient client = _clientFor(500, huge);
      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>().having(
            (ApiClientException e) => e.message,
            'message',
            isEmpty,
          ),
        ),
      );
    });

    test('a short plain body still passes through', () async {
      final HttpReelShortApiClient client = _clientFor(503, 'Service paused');
      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>().having(
            (ApiClientException e) => e.message,
            'message',
            'Service paused',
          ),
        ),
      );
    });

    test('an error envelope keeps its short message', () async {
      final HttpReelShortApiClient client = _clientFor(
        400,
        '{"code":1001,"message":"Username already exists.","data":null}',
      );
      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>()
              .having((ApiClientException e) => e.code, 'code', 1001)
              .having(
                (ApiClientException e) => e.message,
                'message',
                'Username already exists.',
              ),
        ),
      );
    });

    test('the status code survives an empty body', () async {
      final HttpReelShortApiClient client = _clientFor(
        520,
        '',
        reasonPhrase: '',
      );
      await expectLater(
        client.checkSystemHealth(),
        throwsA(
          isA<ApiClientException>()
              .having((ApiClientException e) => e.statusCode, 'status', 520),
        ),
      );
    });
  });

  group('sanitizer rules', () {
    test('structured and oversized text is rejected', () {
      expect(sanitizeUpstreamMessage(_cloudflare525Json), isEmpty);
      expect(sanitizeUpstreamMessage(_html502), isEmpty);
      expect(sanitizeUpstreamMessage('[1,2,3]'), isEmpty);
      expect(
        sanitizeUpstreamMessage('y' * (maxUserFacingMessageLength + 1)),
        isEmpty,
      );
      expect(sanitizeUpstreamMessage(''), isEmpty);
      expect(sanitizeUpstreamMessage('   '), isEmpty);
    });

    test('a short sentence is kept, trimmed', () {
      expect(
        sanitizeUpstreamMessage('  Invalid current password.  '),
        'Invalid current password.',
      );
    });
  });

  group('state layer fallback', () {
    test('a gateway body surfaces shared copy, not the body', () async {
      final AppStateController controller = AppStateController(
        dataSource: FakeAppDataSource()
          ..failWith(
            'checkSystemHealth',
            ApiClientException(525, null, sanitizeUpstreamMessage(_cloudflare525Json)),
          ),
      );
      addTearDown(controller.dispose);
      // The health probe reports through the controller's error path.
      await controller.checkApiHealth();
      final String? message = _lastErrorOf(controller);
      expect(message, isNotNull);
      expect(message, isNot(contains('{')));
      expect(message, isNot(contains('<')));
      expect(message, isNot(contains('ray_id')));
      expect(
        message,
        anyOf(
          'Content failed to load. Try refreshing later.',
          'Request failed.',
        ),
        reason: 'a 5xx body falls back to the shared failure copy',
      );
    });

    test('an HTML 502 falls back to shared copy', () async {
      final AppStateController controller = AppStateController(
        dataSource: FakeAppDataSource()
          ..failWith(
            'checkSystemHealth',
            ApiClientException(502, null, sanitizeUpstreamMessage(_html502)),
          ),
      );
      addTearDown(controller.dispose);
      await controller.checkApiHealth();
      final String message = _lastErrorOf(controller) ?? '';
      expect(message, isNot(contains('<')));
      expect(
        message,
        anyOf('Content failed to load. Try refreshing later.', 'Request failed.'),
      );
    });

    test('a short upstream message is still shown verbatim', () async {
      final AppStateController controller = AppStateController(
        dataSource: FakeAppDataSource()
          ..failWith(
            'checkSystemHealth',
            const ApiClientException(503, null, 'Service paused'),
          ),
      );
      addTearDown(controller.dispose);
      await controller.checkApiHealth();
      expect(_lastErrorOf(controller), 'Service paused');
    });

    test('an oversized body is replaced by shared copy', () async {
      final AppStateController controller = AppStateController(
        dataSource: FakeAppDataSource()
          ..failWith(
            'checkSystemHealth',
            ApiClientException(500, null, sanitizeUpstreamMessage('z' * 4000)),
          ),
      );
      addTearDown(controller.dispose);
      await controller.checkApiHealth();
      final String message = _lastErrorOf(controller) ?? '';
      expect(message.length, lessThanOrEqualTo(maxUserFacingMessageLength));
      expect(
        message,
        anyOf('Content failed to load. Try refreshing later.', 'Request failed.'),
      );
    });
  });
}

