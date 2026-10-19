# AppStateController parity audit

Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`
(1678 lines) to `android-flutter/lib/state/app_state_controller.dart`.

- Kotlin `state.value` → `_state`; `mutableState.update { it.copy(...) }` →
  `_set(_state.copyWith(...))` (`_set` assigns and calls `notifyListeners()`).
- `suspend fun` → `Future<void>`; `val state: StateFlow<AppUiState>` →
  `AppUiState get state`; the class is a `ChangeNotifier`.
- No foundation file was modified: `app_data_source.dart`, `app_ui_state.dart`,
  `playback_state.dart`, `api_client_exception.dart`, `app_models.dart`,
  `app_strings.dart` and `app_language.dart` are consumed as they are, and no
  package was added to `pubspec.yaml`.

## 1. Method-by-method mapping

Kotlin line numbers are from the source file above. Every Kotlin declaration has
a Dart counterpart; nothing was stubbed, simplified or dropped.

### Session, authentication, language

| Kotlin (line) | Dart |
| --- | --- |
| `restoreSession` (47) | `restoreSession` |
| `loadSavedCredentials` (100) | `loadSavedCredentials` |
| `login` (107) | `login` |
| `register` (117) | `register` |
| `fetchCaptcha` (137) | `fetchCaptcha` |
| `updateSavedCredentials` (148) | `_updateSavedCredentials` |
| `openHomeAfterAuthentication` (167) | `_openHomeAfterAuthentication` |
| `refreshHome` (253) | `refreshHome` |
| `refreshHomeWithPull` (268) | `refreshHomeWithPull` |
| `openHome` (290) | `openHome` |
| `refreshHomeSilently` (300) | `refreshHomeSilently` |
| `search` (315) | `search` |
| `showSearch` (331) | `showSearch` |
| `setLanguage` (335) | `setLanguage` |

### Detail, playback, progress

| Kotlin (line) | Dart |
| --- | --- |
| `openBook` (371) | `openBook` |
| `openPlayer` (426) | `openPlayer` |
| `openWatchRecord` (444) | `openWatchRecord` |
| `requestEpisodePlayback` (497) | `_requestEpisodePlayback` |
| `openPlayerForAuthenticatedUser` (531) | `_openPlayerForAuthenticatedUser` |
| `loadInteractionSilently` (586) | `_loadInteractionSilently` |
| `loadCommentsSilently` (609) | `_loadCommentsSilently` |
| `chooseResumeEpisode` (617) | `_chooseResumeEpisode` |
| `updatePlaybackPosition` (641) | `updatePlaybackPosition` |
| `reportProgressSilently` (651) | `reportProgressSilently` |
| `upsertWatchHistory` (750) | `_upsertWatchHistory` |
| `refreshPlaybackUrl` (761) | `refreshPlaybackUrl` |

### Social, favourites, navigation

| Kotlin (line) | Dart |
| --- | --- |
| `toggleLike` (775) | `toggleLike` |
| `toggleFavorite` (783) | `toggleFavorite` |
| `submitComment` (791) | `submitComment` |
| `openFavorites` (800) | `openFavorites` |
| `backToDetail` (818) | `backToDetail` |
| `backToPlaybackSource` (822) | `backToPlaybackSource` |
| `backToAccount` (832) | `backToAccount` |
| `backToFunds` (837) | `backToFunds` |
| `runSocialAction` (841) | `_runSocialAction` |

### Account, funds, VIP, withdrawals

| Kotlin (line) | Dart |
| --- | --- |
| `loadAccountSnapshot` (858) | `loadAccountSnapshot` |
| `openAccount` (864) | `openAccount` |
| `openFunds` (881) | `openFunds` |
| `openWithdrawal` (883) | `openWithdrawal` |
| `openVipRecharge` (885) | `openVipRecharge` |
| `openAccountSection` (887) | `_openAccountSection` |
| `loadAccountSnapshot(requestVersion, targetScreen)` (904) | `_loadAccountSnapshotWithLoading` (renamed, see §4) |
| `loadAccountSnapshotForAuthenticatedUser` (908) | `_loadAccountSnapshotForAuthenticatedUser` |
| `refreshAccountSilently` (955) | `_refreshAccountSilently` |
| `bindWallet` (1010) | `bindWallet` |
| `unbindWallet` (1016) | `unbindWallet` |
| `createVipOrder` (1022) | `createVipOrder` |
| `refreshLatestVipOrder` (1049) | `refreshLatestVipOrder` |
| `showVipPaywall` (1057) | `showVipPaywall` |
| `dismissVipPaywall` (1062) | `dismissVipPaywall` |
| `submitWithdrawal` (1074) | `submitWithdrawal` |
| `changePassword` (1080) | `changePassword` |

### Global actions, diagnostics, plumbing

| Kotlin (line) | Dart |
| --- | --- |
| `clearError` (1113) | `clearError` |
| `showAuthPrompt` (1117) | `showAuthPrompt` |
| `showRegisterAuthMode` (1121) | `showRegisterAuthMode` |
| `showLoginAuthMode` (1125) | `showLoginAuthMode` |
| `dismissAuthPrompt` (1129) | `dismissAuthPrompt` |
| `checkApiHealth` (1144) | `checkApiHealth` |
| `logout` (1169) | `logout` |
| `runWithLoading` (1180) | `_runWithLoading` |
| `runWithoutLoading` (1192) | `_runWithoutLoading` |
| `handleRequestError` (1202) | `_handleRequestError` |
| `runAccountOperation` (1246) | `_runAccountOperation` |
| `loadPointAccountSilently` (1267) | `_loadPointAccountSilently` |
| `openVipPaywall` (1292) | `_openVipPaywall` |
| `refreshVipPaymentState` (1316) | `_refreshVipPaymentState` |
| `requireSelectedBook` (1413) | `_requireSelectedBook` |
| `isActivePlaybackRequest` (1416) | `_isActivePlaybackRequest` |
| `hasAccountSnapshot` (1419) | `_hasAccountSnapshot` |
| `isAccountAreaScreen` (1422) | `_isAccountAreaScreen` |
| `requireAuthenticatedAccount` (1426) | `_requireAuthenticatedAccount` |
| `reloadAccountSnapshotAfterAction` (1433) | `_reloadAccountSnapshotAfterAction` |
| `PendingCompletionReport` (1468) | `_PendingCompletionReport` |
| `loadOptionalAccountData` (1474) | `_loadOptionalAccountData` |
| `loadContinueWatchingBooks` (1483) | `_loadContinueWatchingBooks` |
| `resumeEpisodeForRecord` (1503) | `_resumeEpisodeForRecord` |
| `playbackReturnScreenFor` (1514) | `_playbackReturnScreenFor` |
| `playbackFallbackEpisode` (1522) | `_playbackFallbackEpisode` |
| `shouldPromptForLogin` (1537) | `_shouldPromptForLogin` |
| `isInvalidCurrentPasswordError` (1543) | `_isInvalidCurrentPasswordError` |
| `isExpiredSessionError` (1551) | `_isExpiredSessionError` |
| `isVipRequiredError` (1558) | `_isVipRequiredError` |
| `isBlockedAccountError` (1563) | `_isBlockedAccountError` |
| `userFacingErrorMessage` (1570) | `_userFacingErrorMessage` |
| `ErrorContext` (1668) | `_ErrorContext` |

### Message helpers (now copy-table entries, see §2)

| Kotlin (line) | Dart |
| --- | --- |
| `invalidCaptchaMessage()` (1614) | `_copy.invalidCaptchaMessage` |
| `registerBadRequestMessage()` (1620) | `_copy.registerBadRequestMessage` |
| `credentialErrorMessage()` (1626) | `_copy.credentialErrorMessage` |
| `usernameExistsMessage()` (1632) | `_copy.usernameExistsMessage` |
| `invalidCurrentPasswordMessage()` (1638) | `_copy.invalidCurrentPasswordMessage` |
| `vipOrderCreatedMessage()` (1644) | `_copy.vipOrderCreatedMessage` |
| `passwordChangedMessage()` (1650) | `_copy.passwordChangedMessage` |
| `walletUpdatedMessage()` (1656) | `_copy.walletUpdatedMessage` |
| `withdrawalSubmittedMessage()` (1662) | `_copy.withdrawalSubmittedMessage` |

### Dart-only additions (no Kotlin counterpart)

| Dart | Why |
| --- | --- |
| `state` getter, `_set` | `StateFlow`/`update` replacement |
| `_AsyncMutex` | hand-written replacement for `kotlinx.coroutines.sync.Mutex` (`lock`/`tryLock`/`unlock`); no package may be added |
| `_AppStateException` | carrier for the `IllegalStateException`/`IllegalArgumentException` messages |
| `_silently` | the repeated `try { ... } catch (_: Throwable) { null }` pattern |
| `_ignoreFailure` | the repeated `catch (_: Throwable) {}` pattern (keeps the catch body non-empty for `empty_catches`) |
| `_rawMessageOf` | Kotlin reads `Throwable.message`; Dart reads `ApiClientException.message`/`_AppStateException.message`/`toString()` |
| `_ControllerCopy`, `_copy` | the single English copy table (required deliverable) |
| `_systemNowMillis` | default value of the injectable `nowMillis` clock |

## 2. Copy mapping (Chinese → English)

`app-core` leaks Chinese user-facing strings; this app is English-only. All
controller copy now lives in the single private `_ControllerCopy` table at the
bottom of `app_state_controller.dart`. Trigger conditions, message presence,
`UiMessageType` and session-clearing behaviour are unchanged; the order below is
the order of first occurrence in the Kotlin source.

| # | Kotlin literal | Kotlin line(s) | Dart copy entry | English replacement |
| --- | --- | --- | --- | --- |
| 1 | `内容暂时加载失败，可以稍后刷新。` | 93, 210, 244, 389, 477, 1577 (6 sites) | `contentLoadFailed` | `Content failed to load. Try refreshing later.` |
| 2 | `积分状态暂时加载失败，播放不受影响。` | 580 | `rewardStatusLoadFailed` | `Reward status failed to load. Playback is unaffected.` |
| 3 | `未选择剧集` | 763, 1414 (2 sites) | `noDramaSelected` | `No drama selected.` |
| 4 | `未选择分集` | 764 | `noEpisodeSelected` | `No episode selected.` |
| 5 | `评论内容不能为空` | 793 | `commentEmpty` | `Comment cannot be empty.` |
| 6 | `登录状态已失效，请重新登录。` | 1429, 1603 (2 sites) | `sessionExpired` | `Your session has expired. Sign in again.` |
| 7 | `账号不可操作，请联系支持。` | 1574 | `accountBlocked` | `This account is unavailable. Contact support.` |
| 8 | `请求失败` | 1610 | `requestFailed` | `Request failed.` |

15 Chinese literal occurrences in total (6+1+2+1+1+2+1+1 = 15, covering 8
distinct strings), replaced 1:1. The Chinese strings inside code comments
(`W5-9`, `W5-4`, `W5-11`, `M1`, `M2`) are not user-facing; their reasoning is
carried over as English comments at the same code sites.

The nine messages that were already English in Kotlin are kept verbatim, in the
same relative order, so the table has the same 17 entries in the same order:

| # | Kotlin (line) | Dart copy entry | Value (unchanged) |
| --- | --- | --- | --- |
| 9 | `invalidCaptchaMessage` (1614) | `invalidCaptchaMessage` | `Captcha answer is incorrect.` |
| 10 | `registerBadRequestMessage` (1620) | `registerBadRequestMessage` | `Check username, password, and captcha answer.` |
| 11 | `credentialErrorMessage` (1626) | `credentialErrorMessage` | `Invalid username or password.` |
| 12 | `usernameExistsMessage` (1632) | `usernameExistsMessage` | `Username already exists.` |
| 13 | `invalidCurrentPasswordMessage` (1638) | `invalidCurrentPasswordMessage` | `Current password is incorrect.` |
| 14 | `vipOrderCreatedMessage` (1644) | `vipOrderCreatedMessage` | `VIP order created. Submit payment to activate VIP.` |
| 15 | `passwordChangedMessage` (1650) | `passwordChangedMessage` | `Password changed. Sign in again with the new password.` |
| 16 | `walletUpdatedMessage` (1656) | `walletUpdatedMessage` | `Wallet updated.` |
| 17 | `withdrawalSubmittedMessage` (1662) | `withdrawalSubmittedMessage` | `Withdrawal submitted.` |

`_userFacingErrorMessage` returns the backend message verbatim for anything that
no rule claims, exactly like Kotlin; no other copy was invented.

## 3. Parity checklist for the required state

| Item | Dart |
| --- | --- |
| `rewardReportInFlight` | `_rewardReportInFlight` |
| `pendingCompletionReport` | `_pendingCompletionReport` |
| `sessionRestored` | `_sessionRestored` |
| `progressReportFailureCount` | `_progressReportFailureCount` |
| `progressReportRetryAtMillis` | `_progressReportRetryAtMillis` |
| `searchRequestVersion` | `_searchRequestVersion` |
| `playbackRequestVersion` | `_playbackRequestVersion` |
| `pendingVipEpisodeRequestVersion` | `_pendingVipEpisodeRequestVersion` |
| `vipPaywallGeneration` | `_vipPaywallGeneration` |
| `accountRequestVersion` | `_accountRequestVersion` |
| `favoritesRequestVersion` | `_favoritesRequestVersion` |
| `accountOperationMutex` | `_accountOperationMutex` (`_AsyncMutex`) |
| `vipPaymentRefreshMutex` | `_vipPaymentRefreshMutex` (`_AsyncMutex`) |
| `apiBaseUrl` initial value | `AppUiState(apiBaseUrl: dataSource.apiBaseUrl)` in the constructor |
| `messageTrigger++` | every user-visible error message in `_handleRequestError` and both `_refreshVipPaymentState` error branches |
| `rewardAwardVersion++` only when `awardedPoints > 0` | `PlaybackState.withProgressReport` (existing foundation API) |
| `walletMutationVersion++` | `_reloadAccountSnapshotAfterAction(walletMutationCompleted: true)` |
| `withdrawalSubmissionVersion++` | `_reloadAccountSnapshotAfterAction(withdrawalSubmitted: true)` |

Behavioural invariants that were checked against the Kotlin source:

- Cold start: non-empty cached shelf → one update with `isLoading = false`, then
  `refreshHomeSilently()` + cache write-back; empty cache → network load; failure
  keeps session and shows `contentLoadFailed` with `UiMessageType.error`;
  `sessionRestored` short-circuits repeat calls.
- Post-login priority: pending VIP episode → pending normal episode (watch-history
  resume) → in-place account snapshot when the screen is the account area →
  return home and reload the shelf.
- Error dispatch: 401 on a protected call clears the local session and keeps the
  remembered credentials; login/register 401s and "current password" 401s/403s
  never clear the session; `vipPaywallVisible && 401` additionally clears the VIP
  payment state; a blocked-account 403 clears the session, clears the VIP payment
  state and closes the auth panel; `openFavorites` while signed out yields an
  empty list with no error.
- Reward reporting: in-flight de-duplication (a second report only moves local
  state and stages a 100% completion), staged report re-sent with depth 1 from
  `finally`, retry deadline `nowMillis() + progressReportBackoffSeconds(failureCount) * 1000`.
- `runAccountOperation` returns silently without queueing when the mutext is held
  (`tryLock`), sets/clears `accountOperation` around the block, `createVipOrder`
  runs it with `showGlobalLoading: false`.

## 4. Behaviour that is not 1:1 (and why)

1. **`CancellationException` rethrow sites.** Dart has no coroutine
   cancellation. Every Kotlin site that rethrows a cancellation before its error
   branch now takes the normal error path, which is marked with an inline
   comment at each site (`_runWithLoading`, `_runWithoutLoading`,
   `restoreSession`, `_openHomeAfterAuthentication`, `refreshHomeWithPull`,
   `refreshHomeSilently`, `setLanguage`, `_openPlayerForAuthenticatedUser`,
   `_loadInteractionSilently`, `_loadCommentsSilently`, `_chooseResumeEpisode`,
   `_runSocialAction`, `reportProgressSilently`, `_loadPointAccountSilently`,
   `_refreshVipPaymentState`, `_refreshAccountSilently`,
   `_reloadAccountSnapshotAfterAction`, `_loadContinueWatchingBooks`,
   `_loadOptionalAccountData`, `checkApiHealth`). Observable difference: an
   abandoned request that failed after its version guard can now surface a
   message where Kotlin stayed silent. No new message was introduced and no
   session-clearing rule changed.
2. **`Mutex` fairness.** `_AsyncMutex.tryLock` is identical (immediate `false`
   instead of queueing). `lock()` waits for release but does not guarantee FIFO
   order, because Dart has no fair-lock primitive; `kotlinx` `Mutex` is FIFO.
   The only user of `lock()` is the VIP payment refresh, which needs mutual
   exclusion, not ordering.
3. **`coroutineScope` sibling cancellation.** The parallel account snapshot
   cannot cancel its sibling requests in Dart. The three unguarded futures are
   marked with `ignore()` so that a failure abandoned by an earlier `await` is
   not reported as an unhandled async error; the awaited results, their order and
   the propagated error are unchanged.
4. **Blank-message fallback.** Kotlin falls back to the exception class simple
   name (`error::class.simpleName ?: "请求失败"`). Dart falls back to the single
   `requestFailed` copy, because `ApiClientException.message` is non-nullable and
   the only blank case is an explicitly empty message; for non-API errors
   `_rawMessageOf` uses `toString()`, which already carries the type name.
5. **Contract-violation exception types.** Kotlin throws
   `IllegalStateException` (`未选择剧集`, `未选择分集`, session expired) and
   `IllegalArgumentException` (empty comment). Dart throws one private
   `_AppStateException` carrying the same message; classification in
   `_userFacingErrorMessage` and the user-visible copy are unchanged.
6. **Language-dependent message helpers.** The nine `when (language)` helpers
   returned the same English string in every branch (the `M1` guard). The port
   keeps one constant per message, so `state.language` no longer influences that
   copy - unobservable while there is exactly one language.
7. **`app_strings.dart` is not used by this file.** The generated 189-string
   table holds UI copy only; the controller's messages are not part of it, and
   importing it without using it would be an `unused_import` error. All
   controller copy therefore lives in `_ControllerCopy`, as required.

### Judgement calls for review

- The private Kotlin overload `loadAccountSnapshot(requestVersion, targetScreen)`
  was renamed to `_loadAccountSnapshotWithLoading` because Dart has no method
  overloading; the public `loadAccountSnapshot()` keeps its name.
- `_refreshAccountSilently` takes `requestVersion` as a required positional
  argument: the Kotlin default (`= accountRequestVersion`) is never used at any
  call site.
- `_hasAccountSnapshot(AppUiState state)` keeps the Kotlin parameter name even
  though it shadows the public `state` getter.
- `_playbackReturnScreenFor` is written as an exhaustive switch over all ten
  `AppScreen` values instead of Kotlin's `else -> HOME`, to satisfy the
  `exhaustive_cases` lint with identical results.
- `updatePlaybackPosition` returns without notifying when the player is not
  `ready`, matching Kotlin's `update { it }`, which emits nothing to a
  `StateFlow` because the value is equal.
- Every public member carries a `///` doc comment; private helpers carry either a
  doc comment or an inline rationale where the Kotlin source had a comment.

## 5. Verification

`flutter analyze lib/state` and
`dart analyze lib/state/app_state_controller.dart` both report
**No issues found!** The repository-wide `flutter analyze` currently fails on
files this port does not touch (`lib/theme/app_theme.dart`,
`lib/theme/app_typography.dart`, `lib/update/update_coordinator.dart`,
`test/update/update_contract_test.dart`).
