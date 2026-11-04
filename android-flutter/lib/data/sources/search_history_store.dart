/// Recent search queries persistence boundary.
///
/// The history is not a secret, so it uses the same plain-preferences path as
/// the language choice rather than the encrypted session store.
library;

/// How many queries the history keeps, newest first.
const int searchHistoryLimit = 6;

/// Reads and writes the recent search queries.
abstract interface class SearchHistoryStore {
  /// Returns the stored queries, newest first.
  Future<List<String>> loadHistory();

  /// Replaces the stored queries.
  Future<void> saveHistory(List<String> queries);
}

/// In-memory history used when durable storage is unavailable, and by tests.
class InMemorySearchHistoryStore implements SearchHistoryStore {
  /// Creates a store seeded with [initial].
  InMemorySearchHistoryStore([List<String> initial = const <String>[]])
    : _queries = List<String>.of(initial);

  List<String> _queries;

  @override
  Future<List<String>> loadHistory() async =>
      List<String>.unmodifiable(_queries);

  @override
  Future<void> saveHistory(List<String> queries) async {
    _queries = List<String>.of(queries);
  }
}

/// Returns [existing] with [query] promoted to the front.
///
/// Matching is case-insensitive, so searching `Love` after `love` moves one
/// entry instead of storing both; the list never exceeds [limit].
List<String> mergedSearchHistory(
  List<String> existing,
  String query, {
  int limit = searchHistoryLimit,
}) {
  final String normalized = query.trim();
  if (normalized.isEmpty) {
    return existing;
  }
  final List<String> next = <String>[normalized];
  for (final String entry in existing) {
    if (next.length >= limit) {
      break;
    }
    if (entry.toLowerCase() == normalized.toLowerCase()) {
      continue;
    }
    next.add(entry);
  }
  return next;
}
