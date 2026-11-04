/// Copy added by the UI redesign.
///
/// [AppStrings] is generated from the Kotlin origin (`tool/port_app_strings.py`)
/// and must stay byte-identical to it, so redesign-only wording lives here
/// instead of being hand-edited into that file. Everything the two clients
/// share still comes from [AppStrings]; this file only holds strings that exist
/// because the Flutter screens gained a state, an action or a label the Kotlin
/// app never had.
///
/// The app is English-only (see `AppLanguage`), so this is a plain constant
/// holder rather than a localisable table.
library;

/// User-visible copy introduced by the redesign.
abstract final class AppUiCopy {
  /// Trailing action that reveals the rest of a section ("See all").
  static const String seeAll = 'See all';

  /// Semantic label of a back affordance.
  static const String back = 'Back';

  /// Semantic label of a sheet/dialog close affordance.
  static const String close = 'Close';

  /// Tooltip of the message bar dismiss button.
  static const String dismissMessage = 'Dismiss message';

  /// Tone word shown and announced beside a success message.
  static const String toneSuccess = 'Success';

  /// Tone word shown and announced beside an error message.
  static const String toneError = 'Error';

  /// Tone word shown and announced beside an informational message.
  static const String toneInfo = 'Info';

  /// Helper under the new-password field: the length rule only, because the
  /// registration instructions (pick a username, solve a captcha) do not apply
  /// to changing an existing password.
  static const String passwordMinimumHint = 'At least 8 characters';

  /// Search: heading of the results list.
  static const String searchResultsSection = 'Results';

  /// Search: title of the empty result state.
  static const String searchNoResultsTitle = 'No dramas matched this search';

  /// Search: body of the empty result state.
  static const String searchNoResultsBody =
      'Try a shorter keyword, or pick one of the suggestions below.';

  /// Search: title shown before the user has searched anything.
  static const String searchStartTitle = 'Find your next drama';

  /// Search: body shown before the user has searched anything.
  static const String searchStartBody =
      'Search by title, or start from a tag below.';

  /// Search: non-interactive pill shown with the empty result state. It is a
  /// status prompt, not a control: the field above is where a new search
  /// starts.
  static const String searchAgain = 'Search again';

  /// Search: heading of the poster rail that gives the discover page something
  /// to browse before the user has typed anything.
  static const String searchTrendingSection = 'Trending now';

  /// Search: heading of the recent-query list.
  static const String searchRecentSection = 'Recent searches';

  /// Search: trailing action of the recent-query list.
  static const String searchRecentClear = 'Clear';

  /// Search: semantic label of the removable query pill in the results header.
  static const String clearSearch = 'Clear search';

  /// Search: semantic label of the results pill that drops the answered query.
  ///
  /// Distinct from [clearSearch] on purpose: the field's own clear action only
  /// empties the text, while this one throws the result list away.
  static const String clearResults = 'Clear results';

  /// Funds: section heading of the money actions.
  static const String fundsActionsSection = 'Actions';

  /// Funds: the zero-balance call to action that sends the user back to the
  /// shelf, where points are actually earned.
  static const String fundsEarnAction = 'Watch to earn';

  /// Funds: caption under a disabled Withdraw action, explaining why it cannot
  /// be used yet instead of letting the tap dead-end in the sub-page.
  static const String fundsWithdrawEmptyHint =
      'Watch an episode to earn points first';

  /// Funds: accessible name of the header refresh affordance.
  static const String refresh = 'Refresh';

  /// Funds: empty title of the VIP order list.
  static const String fundsVipOrdersEmptyTitle = 'No VIP orders yet';

  /// Funds: empty body of the VIP order list.
  static const String fundsVipOrdersEmptyBody =
      'Your VIP recharge orders will appear here.';

  /// Funds: empty title of the point ledger.
  static const String fundsPointRecordsEmptyTitle = 'No point activity yet';

  /// Funds: empty body of the point ledger.
  static const String fundsPointRecordsEmptyBody =
      'Watch an episode to start earning points.';

  /// Funds: guest-state call to action.
  static const String fundsGuestAction = 'Sign in';

  /// Withdrawal: label of the step that returns to the amount input.
  static const String withdrawalEditAmount = 'Edit amount';

  /// Withdrawal: empty title of the withdrawal record list.
  static const String withdrawalRecordsEmptyTitle = 'No withdrawals yet';

  /// Account: section heading of the shortcut entries.
  static const String accountShortcutsSection = 'Shortcuts';

  /// Account: section heading of the settings rows.
  static const String accountSettingsSection = 'Settings';

  /// Account: subtitle of the bank-card row (it now opens a bind sheet).
  static const String accountBankCardHint = 'Bind a bank card for withdrawal';

  /// Account: bank-card bind sheet title.
  static const String bankCardBindTitle = 'Bind bank card';

  /// Account: bank-card bind submit label.
  static const String bankCardBindAction = 'Bind card';

  /// Account: bank-card form labels.
  static const String bankCardNumberLabel = 'Card number';
  static const String bankCardExpiryLabel = 'Expiry (MM/YY)';
  static const String bankCardCvvLabel = 'CVV';

  /// Account: what the bank-card bind call reports.
  ///
  /// The bind is a stub: `/api/app/wallet/bank-card` still answers 410 and the
  /// form never sends anything, so the only honest outcome is a generic
  /// failure. Replace this copy and the stub together when a payout channel
  /// exists.
  static const String bankCardBindFailed =
      'Unknown error. Please try again later.';

  /// VIP: label of the validity row.
  static const String vipValidity = 'Valid for';

  /// VIP: label of the price row.
  static const String vipPrice = 'Price';

  /// Short "Expired" status word for a status pill.
  ///
  /// The full sentence (`vipPayExpired`) is long-form copy: it stays visible as
  /// a caption under the pill rather than being squeezed into the capsule.
  static const String statusExpired = 'Expired';

  /// Player: label of the like action.
  static const String playerLike = 'Like';

  /// Player: label of the comment action.
  static const String playerComments = 'Comments';

  /// Player: empty title of the comment list while it loads.
  static const String playerCommentsLoading = 'Loading comments';

  /// Player: empty title of the comment list.
  static const String playerCommentsEmptyTitle = 'No comments yet';

  /// Player: empty body of the comment list.
  static const String playerCommentsEmptyBody = 'Be the first to comment.';

  /// Player: title of the comment list when its request failed.
  ///
  /// The player prefetches comments on playback entry; when that request fails
  /// the drawer used to claim "no comments yet" and never retried.
  static const String playerCommentsLoadFailedTitle = "Couldn't load comments";

  /// Player: body of the failed comment list.
  static const String playerCommentsLoadFailedBody =
      'Check your connection and try again.';

  /// Player: action that retries the comment list.
  static const String playerCommentsRetry = 'Retry';

  /// Player: semantic label of the comment sheet's send affordance, which is an
  /// icon-only button and was previously announced as nothing at all.
  static const String playerCommentSend = 'Send comment';

  /// Player: episode-drawer heading.
  static const String playerEpisodes = 'Episodes';

  /// Episode sheet: heading of the free run, when a VIP gate splits the grid.
  static const String episodesFreeSection = 'Free';

  /// Episode sheet: heading of the VIP-gated run.
  static const String episodesVipSection = 'VIP';

  /// Episode sheet: the word announced for an episode behind the gate.
  ///
  /// The grid cell prints only the lock glyph, so this word exists solely for
  /// the semantic label and the spine summary.
  static const String episodeLocked = 'VIP locked';

  /// Episode sheet: lead-in of the paywall banner, followed by an episode range
  /// (`VIP unlocks EP 08–38`).
  static const String vipUnlockRangePrefix = 'VIP unlocks ';

  /// Password field: semantic label and tooltip of the toggle that reveals a
  /// masked value.
  static const String showPassword = 'Show password';

  /// Password field: semantic label and tooltip of the toggle that masks the
  /// value again.
  static const String hidePassword = 'Hide password';

  /// Episode spine: state word for a partially watched episode, appended after
  /// the count in the generated description (for example `2 in progress`).
  static const String episodeInProgress = 'in progress';

  /// Detail screen: expand action for long drama description.
  static const String readMore = 'Read more';

  /// Detail screen: collapse action for long drama description.
  static const String showLess = 'Show less';
}
