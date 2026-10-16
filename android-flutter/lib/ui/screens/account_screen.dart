/// Account: the member-centre "Me" page.
///
/// Behavioural parity with the Kotlin `AccountScreen`: no refresh and no withdrawal/VIP/diagnostics
/// entries (G10/G11 — only three of the four declared quick entries render), the continue-watching
/// preview capped at two rows with the `size/limit` pill, the wallet sheet that closes itself on
/// success through `walletMutationVersion` (bind, replace **and** unbind), the password sheet that
/// never auto-closes, the read-only bank-card explanation, the selectable support email, the
/// version row with the manual update check, a sign-out without a confirmation step, and the
/// guest panel with direct sign-in / register entries.
library;

import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/navigation_contract.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/ui_formats.dart';
import '../../core/formatting/update_ui_format.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../../update/update_coordinator.dart';
import '../../update/update_state.dart';
import '../components/app_button.dart';
import '../components/app_text_field.dart';
import '../components/poster_card.dart';
import '../update/update_dialog.dart';

/// `versionName (versionCode)` of the running build; `--` where the plugin is
/// unavailable (widget tests). Memoised like the Kotlin `BuildConfig` constant.
final Future<String> _appVersionLabel = () async {
  try {
    final PackageInfo info = await PackageInfo.fromPlatform();
    return '${info.version} (${info.buildNumber})';
  } catch (_) {
    return '--';
  }
}();

/// The Me screen.
class AccountScreen extends StatelessWidget {
  /// Creates the account screen. The shell instantiates it without arguments.
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final bool guest = state.session == null;

    return Scaffold(
      backgroundColor: colors.canvas,
      body: SafeArea(
        bottom: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 12, 18, 28),
          children: <Widget>[
            Text(copy.accountTab, style: roles.displayLarge),
            const SizedBox(height: 16),
            _IdentityCard(state: state),
            const SizedBox(height: 16),
            _QuickEntries(state: state),
            if (guest) ...<Widget>[
              const SizedBox(height: 16),
              _GuestEntries(state: state),
            ],
            if (!guest && state.watchHistory.isNotEmpty) ...<Widget>[
              const SizedBox(height: 16),
              _ContinueWatching(state: state),
            ],
            if (!guest) ...<Widget>[
              const SizedBox(height: 16),
              _WalletRow(state: state),
            ],
            const SizedBox(height: 16),
            _StaticRows(state: state),
            const SizedBox(height: 16),
            _VersionRow(state: state),
            const SizedBox(height: 24),
            // Sign-out has no confirmation step, and guests have no entry here
            // (their sign-in / register rows live in [_GuestEntries]).
            if (!guest)
              AppButton(
                label: copy.accountSignOutTitle,
                variant: AppButtonVariant.tertiary,
                isExpanded: true,
                onPressed: () => context.read<AppStateController>().logout(),
              ),
          ],
        ),
      ),
    );
  }
}

class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AuthSession? session = state.session;
    final String name = session?.username.trim() ?? '';
    final String initial = name.isEmpty
        ? '?'
        : name.characters.first.toUpperCase();
    final Color avatarFill = state.vipActive
        ? colors.accentPrimary
        : colors.surfaceAlt;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(shapes.radiusContainer),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Row(
        children: <Widget>[
          CircleAvatar(
            radius: 24,
            backgroundColor: avatarFill,
            foregroundColor: colors.onAccent,
            child: Text(initial, style: roles.displayLarge),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  name.isEmpty ? copy.accountGuestUser : name,
                  style: roles.display,
                ),
                const SizedBox(height: 4),
                Text(
                  state.vipActive
                      ? <String>[
                          copy.accountVipStatusActive,
                          if (dateOnlyLabel(state.vipUntil).isNotEmpty)
                            dateOnlyLabel(state.vipUntil),
                        ].join(' · ')
                      : copy.accountVipStatusInactive,
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _QuickEntries extends StatelessWidget {
  const _QuickEntries({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final bool guest = state.session == null;

    // Parity gap G11: the declared quick-entry list carries four labels but only three render,
    // because the funds entry became a bottom tab when the money screens moved down a level.
    final List<(_QuickEntrySpec, VoidCallback)>
    entries = <(_QuickEntrySpec, VoidCallback)>[
      (
        _QuickEntrySpec(
          Icons.bookmark_border_outlined,
          copy.accountFavoritesTitle,
          copy.accountFavoritesSubtitle,
        ),
        // Guests stop at the auth prompt instead of the (always empty) tab.
        guest ? controller.showAuthPrompt : controller.openFavorites,
      ),
      (
        _QuickEntrySpec(
          Icons.history_outlined,
          copy.accountWatchHistoryTitle,
          copy.accountRecentWatchSubtitle,
        ),
        guest
            ? controller.showAuthPrompt
            : () => _showWatchHistorySheet(context),
      ),
      // The password sheet is a logged-in business form; guests never see it.
      if (!guest)
        (
          _QuickEntrySpec(
            Icons.vpn_key_outlined,
            copy.accountChangePasswordTitle,
            copy.accountSignOutSubtitle,
          ),
          () => _showPasswordSheet(context),
        ),
    ];

    return Column(
      children: <Widget>[
        for (final (_QuickEntrySpec spec, VoidCallback onTap) in entries)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: InkWell(
              borderRadius: BorderRadius.circular(shapes.radiusControl),
              onTap: onTap,
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: colors.surface,
                  borderRadius: BorderRadius.circular(shapes.radiusControl),
                  border: Border.all(
                    color: colors.hairline,
                    width: shapes.strokeHairline,
                  ),
                ),
                child: Row(
                  children: <Widget>[
                    Icon(spec.icon, color: colors.accentPrimary, size: 22),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(spec.title, style: roles.body),
                          const SizedBox(height: 2),
                          Text(
                            spec.subtitle,
                            style: roles.body.copyWith(
                              color: colors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right, color: colors.textSecondary),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _QuickEntrySpec {
  const _QuickEntrySpec(this.icon, this.title, this.subtitle);

  final IconData icon;
  final String title;
  final String subtitle;
}

/// The guest panel: direct sign-in **and** register entries, matching the
/// Kotlin `GuestActionPanel` (`guestAccountEntryLabels` × modes).
class _GuestEntries extends StatelessWidget {
  const _GuestEntries({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();

    void openRegister() {
      controller.showRegisterAuthMode();
      controller.showAuthPrompt();
    }

    final List<(IconData, String, String, VoidCallback)> rows =
        <(IconData, String, String, VoidCallback)>[
          (
            Icons.person_outline,
            copy.accountGuestSignIn,
            copy.accountGuestSignInSubtitle,
            controller.showAuthPrompt,
          ),
          (
            Icons.play_arrow_outlined,
            copy.accountGuestRegister,
            copy.accountGuestRegisterSubtitle,
            openRegister,
          ),
        ];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(shapes.radiusControl),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Column(
        children: <Widget>[
          for (int i = 0; i < rows.length; i++) ...<Widget>[
            if (i > 0) Divider(height: 1, color: colors.hairline),
            InkWell(
              borderRadius: BorderRadius.circular(shapes.radiusControl),
              onTap: rows[i].$4,
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 14),
                child: Row(
                  children: <Widget>[
                    Icon(rows[i].$1, color: colors.accentPrimary, size: 22),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(rows[i].$2, style: roles.body),
                          const SizedBox(height: 2),
                          Text(
                            rows[i].$3,
                            style: roles.body.copyWith(
                              color: colors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right, color: colors.textSecondary),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _ContinueWatching extends StatelessWidget {
  const _ContinueWatching({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    // The preview shows at most `accountContinueWatchingLimit()` records and the
    // pill counts that trimmed list: `size/limit`.
    final List<WatchRecord> records = state.watchHistory
        .take(accountContinueWatchingLimit())
        .toList(growable: false);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          children: <Widget>[
            Expanded(
              child: Text(copy.accountRecentWatchTitle, style: roles.display),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: colors.surfaceAlt,
                borderRadius: BorderRadius.circular(shapes.radiusInline),
              ),
              child: Text(
                '${records.length}/${accountContinueWatchingLimit()}',
                style: roles.body.copyWith(color: colors.textSecondary),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 204,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: records.length,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (BuildContext context, int index) {
              final WatchRecord record = records[index];
              final BookSummary? book =
                  state.continueWatchingBooks[record.bookId];
              return SizedBox(
                width: 136,
                child: PosterCard(
                  title:
                      (book?.title ?? '').isNotEmpty
                      ? book!.title
                      : record.bookTitle,
                  coverUrl: book?.coverUrl,
                  episodeCount: book?.chapterCount ?? 0,
                  currentEpisode: record.episode,
                  watchedEpisodeCount: record.progressPercent >= 100
                      ? record.episode
                      : record.episode - 1,
                  onTap: () => controller.openWatchRecord(record),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _WalletRow extends StatelessWidget {
  const _WalletRow({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final WalletInfo? wallet = state.wallet;
    final String? address = wallet?.walletAddress;
    final bool bound = address != null && address.isNotEmpty;
    final String subtitle = bound ? address : copy.accountWalletNetworkHint;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(shapes.radiusControl),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Row(
        children: <Widget>[
          Icon(
            Icons.account_balance_wallet_outlined,
            color: colors.accentPrimary,
            size: 22,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(copy.accountWalletTitle, style: roles.body),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: () => _showWalletSheet(context),
            child: Text(
              bound
                  ? copy.accountWalletReplaceAction
                  : copy.accountWalletBindAction,
            ),
          ),
        ],
      ),
    );
  }
}

class _StaticRows extends StatelessWidget {
  const _StaticRows({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStrings copy = strings(state.language);
    final bool guest = state.session == null;

    return Column(
      children: <Widget>[
        // The bank-card entry stays read-only: no card data is ever collected.
        if (!guest) ...<Widget>[
          _StaticRow(
            icon: Icons.credit_card_outlined,
            title: copy.accountBankCardTitle,
            subtitle: copy.accountBankCardUnsupported,
          ),
          const SizedBox(height: 10),
        ],
        _StaticRow(
          icon: Icons.mail_outline,
          title: copy.accountContactSupportTitle,
          subtitle: copy.accountContactSupportEmail,
          selectable: true,
        ),
      ],
    );
  }
}

class _StaticRow extends StatelessWidget {
  const _StaticRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.selectable = false,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool selectable;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(shapes.radiusControl),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Row(
        children: <Widget>[
          Icon(icon, color: colors.textSecondary, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(title, style: roles.body),
                const SizedBox(height: 2),
                if (selectable)
                  SelectableText(
                    subtitle,
                    style: roles.body.copyWith(color: colors.textSecondary),
                  )
                else
                  Text(
                    subtitle,
                    style: roles.body.copyWith(color: colors.textSecondary),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// The version row: `versionTitle` + `versionName (versionCode)`, trailing a
/// manual check that is disabled while a manual check is in flight.
class _VersionRow extends StatelessWidget {
  const _VersionRow({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final UpdateStrings updateCopy = updateStrings(state.language);
    final UpdateCoordinator? coordinator = UpdateDialogScope.maybeOf(context)
        ?.coordinator;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(shapes.radiusControl),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Row(
        children: <Widget>[
          Icon(
            Icons.system_update_outlined,
            color: colors.textSecondary,
            size: 22,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(updateCopy.versionTitle, style: roles.body),
                const SizedBox(height: 2),
                FutureBuilder<String>(
                  future: _appVersionLabel,
                  builder:
                      (BuildContext context, AsyncSnapshot<String> snapshot) =>
                          Text(
                            snapshot.data ?? '--',
                            style: roles.body.copyWith(
                              color: colors.textSecondary,
                            ),
                          ),
                ),
              ],
            ),
          ),
          if (coordinator != null)
            ValueListenableBuilder<UpdateState>(
              valueListenable: coordinator.state,
              builder:
                  (BuildContext context, UpdateState updateState, Widget? _) {
                    final bool checking =
                        updateState is Checking && updateState.manual;
                    return TextButton(
                      onPressed: checking
                          ? null
                          : () => coordinator.checkForUpdate(manual: true),
                      child: Text(
                        checking
                            ? updateCopy.checking
                            : updateCopy.checkForUpdates,
                      ),
                    );
                  },
            ),
        ],
      ),
    );
  }
}

Future<void> _showWatchHistorySheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _WatchHistorySheet(),
  );
}

Future<void> _showPasswordSheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _PasswordSheet(),
  );
}

Future<void> _showWalletSheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _WalletSheet(),
  );
}

/// The full watch-history sheet: every record, tap resumes that drama.
class _WatchHistorySheet extends StatelessWidget {
  const _WatchHistorySheet();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final List<WatchRecord> records = state.watchHistory;

    return SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 18, 8, 12),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      accountDetailSheetTitle(
                        AccountDetailSheet.watchHistory,
                        state.language,
                      ),
                      style: roles.displayLarge,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: copy.playerClose,
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            if (records.isEmpty)
              Padding(
                padding: const EdgeInsets.all(18),
                child: Text(
                  accountEmptyDetailMessage(
                    AccountDetailSheet.watchHistory,
                    state.language,
                  ),
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              )
            else
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.fromLTRB(18, 0, 18, 24),
                  itemCount: records.length,
                  itemBuilder: (BuildContext context, int index) {
                    final WatchRecord record = records[index];
                    return InkWell(
                      key: ValueKey<String>(
                        '${record.bookId}-${record.episode}',
                      ),
                      onTap: () {
                        Navigator.of(context).pop();
                        controller.openWatchRecord(record);
                      },
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        child: Row(
                          children: <Widget>[
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: colors.accentPrimary.withValues(
                                  alpha: 0.14,
                                ),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                Icons.play_arrow,
                                color: colors.accentPrimary,
                                size: 20,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Text(
                                    record.bookTitle,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: roles.body,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${episodeNumberLabel(record.episode, state.language)}'
                                    ' · ${record.progressPercent.clamp(0, 100)}%',
                                    style: roles.body.copyWith(
                                      color: colors.textSecondary,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.chevron_right,
                              color: colors.textSecondary,
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _PasswordSheet extends StatefulWidget {
  const _PasswordSheet();

  @override
  State<_PasswordSheet> createState() => _PasswordSheetState();
}

class _PasswordSheetState extends State<_PasswordSheet> {
  final TextEditingController _oldPassword = TextEditingController();
  final TextEditingController _newPassword = TextEditingController();

  @override
  void initState() {
    super.initState();
    // The submit gate reads live text, so every keystroke re-evaluates it.
    _oldPassword.addListener(_rebuild);
    _newPassword.addListener(_rebuild);
  }

  void _rebuild() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _oldPassword.dispose();
    _newPassword.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final bool busy = state.accountOperation != null;
    // `!isSubmitting && oldPassword.isNotBlank() && newPassword.length >= 8`.
    final bool enabled =
        !busy &&
        _oldPassword.text.trim().isNotEmpty &&
        _newPassword.text.length >= 8;

    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 18,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(copy.accountChangePasswordTitle, style: roles.displayLarge),
          const SizedBox(height: 16),
          AppTextField(
            controller: _oldPassword,
            label: copy.authPasswordLabel,
            obscureText: true,
          ),
          const SizedBox(height: 12),
          AppTextField(
            controller: _newPassword,
            label: copy.accountPasswordNewLabel,
            // Change-password needs the length rule, not the registration
            // instructions (username and captcha do not apply here).
            helperText: AppUiCopy.passwordMinimumHint,
            obscureText: true,
          ),
          const SizedBox(height: 18),
          // Parity: this sheet does NOT auto-close on success; the failure keeps the input.
          AppButton(
            label: copy.accountChangePasswordTitle,
            isExpanded: true,
            isLoading: busy,
            onPressed: enabled
                ? () => context.read<AppStateController>().changePassword(
                    _oldPassword.text,
                    _newPassword.text,
                  )
                : null,
          ),
        ],
      ),
    );
  }
}

class _WalletSheet extends StatefulWidget {
  const _WalletSheet();

  @override
  State<_WalletSheet> createState() => _WalletSheetState();
}

class _WalletSheetState extends State<_WalletSheet> {
  late final AppStateController _controller;
  final TextEditingController _address = TextEditingController();
  final TextEditingController _password = TextEditingController();
  late int _lastMutationVersion;
  bool _confirmOpen = false;

  @override
  void initState() {
    super.initState();
    _controller = context.read<AppStateController>();
    // Kotlin `remember(wallet)`: replacing pre-fills the bound address.
    final String? bound = _controller.state.wallet?.walletAddress;
    _address.text = bound ?? '';
    _lastMutationVersion = _controller.state.walletMutationVersion;
    // The submit gates read live text, so every keystroke re-evaluates them.
    _address.addListener(_rebuild);
    _password.addListener(_rebuild);
    // `walletMutationVersion` advances when a bind/replace/unbind lands; the
    // sheet closes itself on success exactly then, and stays open with the
    // user's input on failure.
    _controller.addListener(_onControllerChanged);
  }

  void _rebuild() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _address.dispose();
    _password.dispose();
    super.dispose();
  }

  /// `walletSheetShouldDismiss`: any version advance closes the sheet — bind,
  /// replace **and** unbind alike.
  void _onControllerChanged() {
    final AppUiState state = _controller.state;
    if (state.walletMutationVersion == _lastMutationVersion) {
      return;
    }
    _lastMutationVersion = state.walletMutationVersion;
    if (mounted && !_confirmOpen) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final bool bound = (state.wallet?.walletAddress ?? '').isNotEmpty;
    final bool busy = state.accountOperation != null;

    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 18,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            bound
                ? copy.accountWalletReplaceAction
                : copy.accountWalletBindAction,
            style: roles.displayLarge,
          ),
          const SizedBox(height: 4),
          Text(
            copy.accountWalletNetworkHint,
            style: roles.body.copyWith(color: colors.textSecondary),
          ),
          const SizedBox(height: 16),
          AppTextField(
            controller: _address,
            label: copy.accountWalletAddressLabel,
            hintText: '0x…',
          ),
          const SizedBox(height: 12),
          AppTextField(
            controller: _password,
            label: copy.authPasswordLabel,
            obscureText: true,
          ),
          const SizedBox(height: 18),
          // Parity: binding and replacing go straight through; only unbinding confirms.
          AppButton(
            label: bound
                ? copy.accountWalletReplaceAction
                : copy.accountWalletBindAction,
            isExpanded: true,
            isLoading: busy,
            onPressed:
                !busy &&
                    _address.text.trim().isNotEmpty &&
                    _password.text.isNotEmpty
                ? () => _controller.bindWallet(
                    'ERC20',
                    _address.text.trim(),
                    _password.text,
                  )
                : null,
          ),
          if (bound) ...<Widget>[
            const SizedBox(height: 12),
            AppButton(
              label: copy.accountWalletUnbindAction,
              variant: AppButtonVariant.secondary,
              isExpanded: true,
              onPressed: !busy && _password.text.isNotEmpty
                  ? () => _confirmUnbind(context)
                  : null,
            ),
          ],
        ],
      ),
    );
  }

  /// The unbind confirmation: title/body/copy from the shared formatter, both
  /// buttons frozen while a submission runs, and no dismissal path either.
  Future<void> _confirmUnbind(BuildContext context) async {
    _confirmOpen = true;
    bool? confirmed;
    try {
      confirmed = await showDialog<bool>(
        context: context,
        builder: (BuildContext dialogContext) => const _ConfirmDialog(),
      );
    } finally {
      // Lift the gate before the unbind runs so a successful mutation (which
      // advances `walletMutationVersion`) can close the sheet.
      _confirmOpen = false;
    }
    if (confirmed == true) {
      await _controller.unbindWallet(_password.text);
    }
  }
}

/// The wallet-unbind confirmation dialog.
class _ConfirmDialog extends StatelessWidget {
  const _ConfirmDialog();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final bool busy = state.accountOperation != null;

    return PopScope(
      // The Kotlin dialog refuses dismissal while a submission runs.
      canPop: !busy,
      child: AlertDialog(
        title: Text(accountConfirmationTitle(state.language)),
        content: Text(
          accountConfirmationBody('unbind the wallet', state.language),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: busy ? null : () => Navigator.of(context).pop(false),
            child: Text(accountConfirmationCancel(state.language)),
          ),
          TextButton(
            onPressed: busy ? null : () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: colors.accentDanger),
            child: Text(accountConfirmationConfirm(state.language)),
          ),
        ],
      ),
    );
  }
}
