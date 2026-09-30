# GIM Hub agent guidance

Apply the [RuneLite example plugin guidance](https://github.com/runelite/example-plugin/blob/master/AGENTS.md) when working on this plugin. The user's explicit instructions and standing testing policy take precedence.

## Repository

- Keep changes small and specific to existing functionality. Preserve the current formatting and avoid unrelated cleanup.
- Target Java 11 and retain the BSD license. Keep the build compatible with the RuneLite example plugin template.
- Verify with `./gradlew test spotlessCheck build --console=plain`.
- The development client launcher is `./gradlew run`.
- Do not commit or push unless the user explicitly requests it.
- Keep generated classes, build output, temporary files, and service registration files out of source control.

## Threading and lifecycle

- Read game state and call client APIs on the client thread. Use `ClientThread.invoke()` to return to it from background callbacks.
- Keep blocking HTTP requests and filesystem operations off the client thread. Uploads currently use an asynchronous RuneLite schedule; item name downloads use OkHttp callbacks.
- Keep startup and shutdown nonblocking. Do not sleep or await executor termination.
- Cancel any plugin owned scheduled tasks and release listeners, subscriptions, overlays, and executors during shutdown. RuneLite manages this plugin's `@Schedule` methods and event subscriptions.
- Use `CompletableFuture.allOf()` when combining batches of asynchronous work.
- Keep work done every tick or frame small. Track scene objects through events instead of scanning the entire scene.

## APIs and dependencies

- Prefer constants from `net.runelite.api.gameval`. Use named constants for widget components rather than numeric group and child pairs.
- Preserve named script and enum IDs when the resolved RuneLite API has no corresponding constant. Check the actual API before inventing replacements.
- Open links with `LinkBrowser`.
- Inject RuneLite's `OkHttpClient` and `Gson`. Derive customized instances from them when needed.
- Prefer OkHttp `enqueue()` for HTTP work. Never execute a request on the client thread.
- Do not add production dependencies already supplied by RuneLite, such as Gson, Guice, or OkHttp. Test dependencies such as MockWebServer belong in the test configuration.
- If filesystem storage is added, use `Filepath`, set the plugin descriptor's `internalName`, and migrate existing data with `legacyDataDirectory` when applicable. Use `Filepath.Chooser` for file selection.
- Runtime plugin code must not use reflection, native access, external processes, dynamic code loading or generation, or Java object serialization. Test fixtures are outside the shipped plugin.
- Keep image dimensions appropriate for their display size and verify that PNG assets contain PNG data.

## Configuration and privacy

- Preserve the existing `GimHub` config group and saved keys. Provide a migration before renaming them.
- Uploads are enabled by a group token supplied by the user. Keep the default token empty.
- Any new toggle that sends data to a server outside RuneLite must default to disabled and include the exact warning required by the upstream guidance.
- Upload only the user's own player data and permitted NPC interaction data. Do not collect or upload other players' names, positions, equipment, or other private information.
- Keep UI text in sentence case.
- Use debug logging for diagnostics. Reserve info logging for infrequent lifecycle or user relevant events.
- Follow RuneLite's [feature restrictions](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) and [Jagex's client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1). Do not add game input automation, server actions, combat prediction, prohibited PvP helpers, or restricted interface manipulation.

## Testing

- Verify every change, but do not automatically add or update tests. Running existing tests can be sufficient.
- Add a permanent regression test only when it catches a realistic, distinct defect in changed application behavior that existing tests do not cover.
- Test observable, application owned behavior through its runtime boundary. Do not test framework guarantees, implementation details, unchanged behavior, speculative edge cases, or hardcoded tables. Assert exact strings only when they are an explicit public contract.
- Prefer the smallest meaningful coverage. Do not add multiple cases for the same underlying defect.
- Use `/tmp` for exploratory reproductions, benchmarks, and temporary verification scripts. Remove temporary files required inside the repository before finishing.
- Review tests added during the task and remove redundant coverage or tests for abandoned approaches. Do not delete or substantially weaken existing tests without approval.
- Report verification results and meaningful gaps. Passing unit tests, a build, or JVM startup does not establish live game behavior.
- Never automate RuneScape input or interact with the game through browser or computer use tools. The user must perform gameplay checks.
- When gameplay changes, describe specific manual checks and offer to launch the development client. For Jagex Accounts, refer to RuneLite's [login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Describe gameplay as unverified until the user confirms it.
