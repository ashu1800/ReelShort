/// Auth sheet: the login / register modal.
///
/// Contract parity with the Kotlin auth surface (`AuthScreen.kt` +
/// `AccountScreen.kt` entries): login is the default mode and register the
/// secondary entry, one primary CTA per mode, exact register enablement
/// through `authRegisterEnabled`, an auto captcha fetch when entering register
/// mode without an image, the remember-password toggle only in login mode,
/// local form state seeded from the saved credentials (reseeded when they
/// change, like Kotlin `remember(state.savedCredentials)`), no inline form
/// errors (they surface through the global message bar), and
/// `dismissAuthPrompt()` on every close path without extra clearing.
///
/// Phase 3: the sheet is a modal route (`AppSheet.show` from the shell) instead
/// of a `Stack` child, so it owns the barrier, the drag handle and the system
/// back gesture. It is therefore only the sheet *body*: the route's bottom
/// sheet provides the surface, the shape and the drag handle, and `AppSheet`
/// provides the page padding, the keyboard inset and the route semantics. The
/// [PopScope] keeps the busy gate the old close button had — while a login or
/// register call is in flight the panel cannot be dismissed.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';
import '../components/app_text_field.dart';

/// The authentication sheet, mounted by the shell as a stack overlay.
class AuthSheet extends StatefulWidget {
  /// Creates the auth sheet. The shell instantiates it without arguments.
  const AuthSheet({super.key});

  @override
  State<AuthSheet> createState() => _AuthSheetState();
}

class _AuthSheetState extends State<AuthSheet> {
  final TextEditingController _username = TextEditingController();
  final TextEditingController _password = TextEditingController();
  final TextEditingController _confirmPassword = TextEditingController();
  final TextEditingController _captchaAnswer = TextEditingController();

  /// The saved-credentials snapshot the local fields were seeded from.
  SavedCredentials? _seedCredentials;
  bool _rememberPassword = false;

  /// The auth mode whose entry already auto-fetched a captcha, so the
  /// automatic fetch runs once per mode entry like the Kotlin
  /// `LaunchedEffect(state.authMode)` instead of on every rebuild.
  AuthMode? _autoFetchedMode;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    _captchaAnswer.dispose();
    super.dispose();
  }

  /// Reseed the fields only when the saved-credentials instance changes,
  /// exactly like the Kotlin `remember(state.savedCredentials) { ... }`
  /// state holders; this never overwrites what the user is typing.
  void _syncSeed(AppUiState state) {
    final SavedCredentials? credentials = state.savedCredentials;
    if (identical(credentials, _seedCredentials)) {
      return;
    }
    _seedCredentials = credentials;
    _username.text = credentials?.username ?? '';
    _password.text = credentials?.password ?? '';
    _rememberPassword = credentials?.rememberPassword ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final bool registerMode = state.authMode == AuthMode.register;
    final bool busy = state.isLoading;
    final bool fieldsEnabled = !busy;

    _syncSeed(state);

    // Register mode with no captcha image yet: fetch one after this frame so
    // the fetch never fires inside the build pass. Like the Kotlin
    // `LaunchedEffect(state.authMode)`, this runs once per entry into register
    // mode; the manual refresh tap on the captcha image re-fetches.
    if (registerMode && _autoFetchedMode != AuthMode.register) {
      _autoFetchedMode = AuthMode.register;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          controller.fetchCaptcha();
        }
      });
    } else if (!registerMode && _autoFetchedMode != null) {
      _autoFetchedMode = null;
    }

    final AuthSheetCopy sheetCopy = authSheetCopy(
      state.authMode,
      state.pendingPlaybackEpisode != null,
      state.language,
    );
    final bool canSubmit = registerMode
        ? authRegisterEnabled(
            busy,
            _username.text,
            _password.text,
            _confirmPassword.text,
            _captchaAnswer.text,
            state.captchaImageBase64.trim().isNotEmpty,
          )
        : !busy &&
            _username.text.trim().isNotEmpty &&
            _password.text.trim().isNotEmpty;

    return PopScope(
      // Busy gate: the route's own dismiss gestures (scrim tap, system back)
      // must respect the same lock as the close button below.
      canPop: !busy,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(sheetCopy.title, style: roles.displayLarge),
                      const SizedBox(height: 4),
                      Text(
                        sheetCopy.subtitle,
                        style: roles.body.copyWith(
                          color: colors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                TextButton(
                  onPressed: busy ? null : controller.dismissAuthPrompt,
                  child: Text(
                    copy.authClose,
                    style: roles.body.copyWith(color: colors.textSecondary),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            AppTextField(
              controller: _username,
              label: copy.authUsernameLabel,
              enabled: fieldsEnabled,
              textInputAction: TextInputAction.next,
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 14),
            AppTextField(
              controller: _password,
              label: copy.authPasswordLabel,
              obscureText: true,
              enabled: fieldsEnabled,
              textInputAction: registerMode
                  ? TextInputAction.next
                  : TextInputAction.done,
              onChanged: (_) => setState(() {}),
              onSubmitted: registerMode
                  ? null
                  : (_) => _submitLogin(controller, canSubmit),
            ),
            if (!registerMode) ...<Widget>[
              _RememberPasswordRow(
                checked: _rememberPassword,
                enabled: fieldsEnabled,
                label: rememberPasswordLabel(state.language),
                onChanged: (bool value) =>
                    setState(() => _rememberPassword = value),
              ),
              AppButton(
                label: busy ? copy.authLoginLoading : sheetCopy.primaryAction,
                isExpanded: true,
                isLoading: busy,
                loadingLabel: copy.authLoginLoading,
                onPressed:
                    canSubmit ? () => _submitLogin(controller, canSubmit) : null,
              ),
              _ModeSwitchButton(
                label: sheetCopy.secondaryAction,
                enabled: fieldsEnabled,
                onTap: controller.showRegisterAuthMode,
              ),
            ] else ...<Widget>[
              const SizedBox(height: 14),
              AppTextField(
                controller: _confirmPassword,
                label: copy.authConfirmPasswordLabel,
                obscureText: true,
                enabled: fieldsEnabled,
                textInputAction: TextInputAction.next,
                onChanged: (_) => setState(() {}),
              ),
              const SizedBox(height: 14),
              _CaptchaRow(
                imageBase64: state.captchaImageBase64,
                answerController: _captchaAnswer,
                answerLabel: authCaptchaAnswerLabel(state.language),
                refreshLabel: copy.authCaptchaRefresh,
                loadingLabel: copy.authCaptchaLoading,
                enabled: fieldsEnabled,
                onRefresh: controller.fetchCaptcha,
                onAnswerChanged: (_) => setState(() {}),
              ),
              const SizedBox(height: 14),
              AppButton(
                label: sheetCopy.primaryAction,
                isExpanded: true,
                isLoading: busy,
                onPressed: canSubmit
                    ? () => _submitRegister(controller, canSubmit)
                    : null,
              ),
              _ModeSwitchButton(
                label: sheetCopy.secondaryAction,
                enabled: fieldsEnabled,
                onTap: controller.showLoginAuthMode,
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _submitLogin(AppStateController controller, bool canSubmit) {
    if (!canSubmit) {
      return;
    }
    controller.login(
      _username.text.trim(),
      _password.text,
      rememberPassword: _rememberPassword,
    );
  }

  void _submitRegister(AppStateController controller, bool canSubmit) {
    if (!canSubmit) {
      return;
    }
    controller.register(
      _username.text.trim(),
      _password.text,
      controller.state.captchaId,
      _captchaAnswer.text.trim(),
    );
  }
}

/// The remember-password checkbox row, login mode only.
class _RememberPasswordRow extends StatelessWidget {
  const _RememberPasswordRow({
    required this.checked,
    required this.enabled,
    required this.label,
    required this.onChanged,
  });

  final bool checked;
  final bool enabled;
  final String label;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    return CheckboxListTile(
      value: checked,
      controlAffinity: ListTileControlAffinity.leading,
      contentPadding: EdgeInsets.zero,
      dense: true,
      title: Text(
        label,
        style: roles.body.copyWith(color: colors.textSecondary),
      ),
      onChanged: enabled ? (bool? value) => onChanged(value ?? false) : null,
    );
  }
}

/// The captcha answer field plus the image tap target.
///
/// The backend image payload may carry a `data:image/png;base64,` prefix; the
/// prefix is stripped before decoding. A payload that fails to decode renders
/// the refresh label instead of crashing.
class _CaptchaRow extends StatelessWidget {
  const _CaptchaRow({
    required this.imageBase64,
    required this.answerController,
    required this.answerLabel,
    required this.refreshLabel,
    required this.loadingLabel,
    required this.enabled,
    required this.onRefresh,
    required this.onAnswerChanged,
  });

  final String imageBase64;
  final TextEditingController answerController;
  final String answerLabel;
  final String refreshLabel;
  final String loadingLabel;
  final bool enabled;
  final VoidCallback onRefresh;
  final ValueChanged<String> onAnswerChanged;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final Uint8List? image = _decodeCaptcha(imageBase64);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: <Widget>[
        Expanded(
          child: AppTextField(
            controller: answerController,
            label: answerLabel,
            keyboardType: TextInputType.number,
            maxLength: 4,
            enabled: enabled,
            onChanged: onAnswerChanged,
          ),
        ),
        const SizedBox(width: 10),
        Semantics(
          button: true,
          label: refreshLabel,
          child: GestureDetector(
            onTap: image != null && enabled ? onRefresh : null,
            child: Container(
              width: 110,
              height: 52,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: colors.surfaceAlt,
                borderRadius: BorderRadius.circular(shapes.radiusControl),
                border: Border.all(
                  color: colors.hairline,
                  width: shapes.strokeHairline,
                ),
              ),
              child: image == null
                  ? Text(
                      imageBase64.trim().isEmpty ? loadingLabel : refreshLabel,
                      style: textTheme.labelSmall!.copyWith(
                        color: colors.textSecondary,
                      ),
                    )
                  : Image.memory(image, gaplessPlayback: true),
            ),
          ),
        ),
      ],
    );
  }
}

/// Decodes the captcha payload; accepts both raw base64 and the
/// `data:image/png;base64,` data-URI form. Returns null on any failure.
Uint8List? _decodeCaptcha(String payload) {
  final String trimmed = payload.trim();
  if (trimmed.isEmpty) {
    return null;
  }
  final String pureBase64 = trimmed.contains(',')
      ? trimmed.substring(trimmed.indexOf(',') + 1)
      : trimmed;
  try {
    final Uint8List bytes = base64Decode(pureBase64);
    return bytes.isEmpty ? null : bytes;
  } on FormatException {
    return null;
  }
}

/// The secondary mode-switch text button.
class _ModeSwitchButton extends StatelessWidget {
  const _ModeSwitchButton({
    required this.label,
    required this.enabled,
    required this.onTap,
  });

  final String label;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    return TextButton(
      onPressed: enabled ? onTap : null,
      child: Text(
        label,
        style: roles.body.copyWith(color: colors.textSecondary),
      ),
    );
  }
}
