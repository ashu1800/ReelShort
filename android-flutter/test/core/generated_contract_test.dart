/// Golden contract tests for the two generated Dart ports.
///
/// `test/core/app_strings_golden.json` and `test/state/app_ui_state_golden.json`
/// are snapshotted straight from the Kotlin sources by `tool/port_app_strings.py`
/// and `tool/port_app_ui_state.py`. Until Phase 5-2 nothing in the repository
/// read them: `docs/parity-matrix.md` claimed this suite existed, but the file
/// had never been committed, so a drift between the Kotlin copy of truth and the
/// Dart port could only be caught by a human diffing both sides.
///
/// These tests restore the claim: the generated section payloads (discovery
/// groups, enum member order) must equal the golden, the surviving VIP/wallet
/// copy the Phase 5-2 cleanup promised to keep must still be there, and the
/// golden must stay self-consistent with the counts the generator declares.
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/state/app_ui_state.dart';

/// Reads and decodes a golden snapshot relative to the package root.
///
/// `flutter test` runs with the package root as the working directory, so the
/// fixtures are resolved from `test/` rather than from the test file's own path.
Map<String, Object?> _golden(String relativePath) {
  final File file = File(relativePath);
  expect(file.existsSync(), isTrue, reason: 'missing golden: $relativePath');
  return jsonDecode(file.readAsStringSync()) as Map<String, Object?>;
}

/// The `fields` object of the AppStrings golden.
Map<String, Object?> _stringFields(Map<String, Object?> golden) =>
    (golden['fields']! as Map<Object?, Object?>).cast<String, Object?>();

/// The `fields` list of the AppUiState golden, keyed by field name.
Map<String, Map<Object?, Object?>> _stateFields(Map<String, Object?> golden) {
  final List<Object?> fields = golden['fields']! as List<Object?>;
  return <String, Map<Object?, Object?>>{
    for (final Object? entry in fields)
      (entry! as Map<Object?, Object?>)['name']! as String:
          entry as Map<Object?, Object?>,
  };
}

void main() {
  group('AppStrings golden contract', () {
    late Map<String, Object?> golden;

    setUpAll(() {
      golden = _golden('test/core/app_strings_golden.json');
    });

    test('the golden is self-consistent and records its Kotlin source', () {
      final Map<String, Object?> fields = _stringFields(golden);
      expect(golden['field_count'], fields.length);
      expect(fields, isNotEmpty);
      expect(
        golden['source'],
        'android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt',
      );
    });

    test('every generated field matches the golden verbatim', () {
      final Map<String, Object?> fields = _stringFields(golden);
      // `copyValues` is generated alongside the table, so this compares all 189
      // fields rather than a hand-picked slice: a slice silently stops checking
      // whatever nobody remembers to add to it.
      final Map<String, String> live = copyValues(AppLanguage.english);
      expect(live.keys.toSet(), fields.keys.toSet());
      live.forEach((String name, String value) {
        expect(
          fields[name],
          value,
          reason: 'AppStrings.$name drifted from the Kotlin golden',
        );
      });
    });

    test('surviving VIP and wallet copy is unchanged after the 5-2 cleanup', () {
      final Map<String, Object?> fields = _stringFields(golden);
      final AppStrings copy = strings(AppLanguage.english);
      // These five are unreferenced from Kotlin yet still rendered by the Dart
      // screens, which is exactly why the cleanup had to keep them.
      const Map<String, String> kept = <String, String>{
        'accountWalletAddressLabel': 'Wallet address',
        'accountVipStatusActive': 'VIP active',
        'accountVipStatusInactive': 'VIP not active',
        'accountVipExpiryPrefix': 'Expires ',
        'bookRowFallbackSuffix': ' episodes',
      };
      kept.forEach((String name, String value) {
        expect(fields[name], value, reason: '$name must survive in the golden');
      });
      expect(copy.accountVipStatusActive, kept['accountVipStatusActive']);
      expect(copy.accountVipStatusInactive, kept['accountVipStatusInactive']);
      expect(copy.accountVipExpiryPrefix, kept['accountVipExpiryPrefix']);
      expect(copy.accountWalletAddressLabel, kept['accountWalletAddressLabel']);
      expect(copy.bookRowFallbackSuffix, kept['bookRowFallbackSuffix']);
    });

    test('the discovery groups match the golden including tag order', () {
      final List<Object?> groups =
          golden['search_discovery_groups']! as List<Object?>;
      final List<SearchDiscoveryGroup> live = searchDiscoveryGroups(
        AppLanguage.english,
      );
      expect(live, hasLength(groups.length));
      for (int index = 0; index < groups.length; index++) {
        final Map<String, Object?> expected =
            (groups[index]! as Map<Object?, Object?>).cast<String, Object?>();
        expect(live[index].title, expected['title']);
        expect(
          live[index].tags,
          (expected['tags']! as List<Object?>).cast<String>(),
          reason: 'tag order is part of the shelf contract',
        );
      }
      expect(
        searchDiscoveryTags(AppLanguage.english),
        live.expand((SearchDiscoveryGroup group) => group.tags).toList(),
      );
    });
  });

  group('AppUiState golden contract', () {
    late Map<String, Object?> golden;

    setUpAll(() {
      golden = _golden('test/state/app_ui_state_golden.json');
    });

    test('the golden state field and enum counts are self-consistent', () {
      final List<Object?> fields = golden['fields']! as List<Object?>;
      final Map<String, Object?> enums =
          (golden['enums']! as Map<Object?, Object?>).cast<String, Object?>();
      expect(golden['field_count'], fields.length);
      expect(golden['enum_count'], enums.length);
      expect(
        golden['source'],
        'android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt',
      );
    });

    test('every generated enum keeps its Kotlin member order', () {
      final Map<String, Object?> enums =
          (golden['enums']! as Map<Object?, Object?>).cast<String, Object?>();
      expect(
        AppScreen.values.map((AppScreen value) => value.name).toList(),
        (enums['AppScreen']! as List<Object?>).cast<String>(),
      );
      expect(
        AuthMode.values.map((AuthMode value) => value.name).toList(),
        (enums['AuthMode']! as List<Object?>).cast<String>(),
      );
      expect(
        AccountOperation.values
            .map((AccountOperation value) => value.name)
            .toList(),
        (enums['AccountOperation']! as List<Object?>).cast<String>(),
      );
      expect(
        UiMessageType.values.map((UiMessageType value) => value.name).toList(),
        (enums['UiMessageType']! as List<Object?>).cast<String>(),
      );
    });

    test('a fresh state agrees with the golden scalar defaults', () {
      final Map<String, Map<Object?, Object?>> byName = _stateFields(golden);
      final AppUiState state = const AppUiState();
      // Every default is recorded as the Kotlin default **expression text**, not
      // as a JSON primitive, so compare against the expression the port implies.
      expect(byName['screen']!['default'], 'AppScreen.${state.screen.name}');
      expect(
        byName['language']!['default'],
        'AppLanguage.defaultLanguage',
        reason: 'the golden records the Kotlin expression, not the member',
      );
      expect(byName['authMode']!['default'], 'AuthMode.${state.authMode.name}');
      expect(byName['isLoading']!['default'], state.isLoading ? 'true' : 'false');
      expect(byName['messageTrigger']!['default'], '${state.messageTrigger}');
      expect(
        byName['authPromptVisible']!['default'],
        state.authPromptVisible ? 'true' : 'false',
      );
      expect(
        byName['apiBaseUrl']!['default'],
        "''",
        reason: 'the port defaults to an empty string literal',
      );
      expect(byName['vipActive']!['default'], state.vipActive ? 'true' : 'false');
      expect(
        byName['vipPaywallVisible']!['default'],
        state.vipPaywallVisible ? 'true' : 'false',
      );
    });

    test('collection defaults start empty like the port', () {
      final Map<String, Map<Object?, Object?>> byName = _stateFields(golden);
      final AppUiState state = const AppUiState();
      // Collections are recorded as their Kotlin expression text rather than as
      // JSON arrays (`const <BookSummary>[]`); assert the expression shape, then
      // assert the port really starts empty.
      for (final String name in <String>[
        'homeShelf',
        'searchResults',
        'episodes',
        'watchHistory',
        'orders',
        'vipOrders',
        'withdrawals',
        'favorites',
      ]) {
        final Object? recorded = byName[name]!['default'];
        expect(
          recorded,
          isA<String>(),
          reason: '$name default is recorded as the Kotlin expression text',
        );
        expect(
          recorded as String,
          contains('[]'),
          reason: '$name default should be an empty collection literal',
        );
      }
      expect(state.homeShelf, isEmpty);
      expect(state.episodes, isEmpty);
      expect(state.favorites, isEmpty);
      expect(state.watchHistory, isEmpty);
      expect(state.orders, isEmpty);
      expect(state.vipOrders, isEmpty);
      expect(state.withdrawals, isEmpty);
    });

    test('nullable and type metadata agree with the port', () {
      final Map<String, Map<Object?, Object?>> byName = _stateFields(golden);
      expect(byName['session']!['nullable'], isTrue);
      expect(byName['screen']!['nullable'], isFalse);
      expect(byName['screen']!['type'], 'AppScreen');
      expect(byName['isLoading']!['type'], 'bool');
      expect(byName['selectedBook']!['nullable'], isTrue);
      // Enum-typed fields are declared by their Dart/Kotlin type name.
      for (final String enumName in <String>[
        'AppScreen',
        'AuthMode',
        'AccountOperation',
        'UiMessageType',
      ]) {
        final Iterable<Map<Object?, Object?>> withType = byName.values.where(
          (Map<Object?, Object?> field) => field['type'] == enumName,
        );
        expect(withType, isNotEmpty, reason: 'a field should use $enumName');
      }
    });
  });
}
