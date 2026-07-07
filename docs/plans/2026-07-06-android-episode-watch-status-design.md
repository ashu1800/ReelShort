# Android Episode Watch Status Design

## Goal

Improve the player episode selector so users can see which episode is current, which episodes have progress, and which episodes are already watched, without changing backend APIs.

## Scope

- Android App only.
- Reuse existing `AppUiState.watchHistory`.
- No backend, content-provider, schema, or API changes.
- Preserve the current immersive dark player layout, bottom sheet, and direct episode switching flow.

## Design

The episode bottom sheet remains a compact grid. Each cell keeps the episode number as the primary signal and adds a small secondary status:

- Current episode: `Current` / `目前`
- Completed episode: `Watched` / `已看`
- Partially watched episode: `58%`
- Unknown/unwatched episode: no secondary label

State priority is current > completed > in progress > empty. Current episode still uses the strongest gold fill. Completed episodes use a subdued watched state with a gold outline and a small progress accent. Partially watched episodes keep the normal dark surface and show a thin progress line, so the grid stays scannable.

## Data Flow

`PlayerScreen` already receives `AppUiState`. The sheet can derive an `EpisodeWatchStatus` from:

- `state.selectedBook?.id`
- `state.watchHistory`
- the episode item
- `state.selectedEpisode`

The helper lives in `UiFormats.kt` for contract testing. The Compose grid only consumes the helper result and renders it.

## Testing

- Add unit contract tests for status priority and localized labels.
- Keep current episode selection behavior unchanged.
- Run Android app-core/app unit tests and `assembleDebug`.
- Install the APK to emulator and manually verify the player episode sheet.

