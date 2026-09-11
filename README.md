# PID Tuner (EPICEFI)

PID Tuner is a TunerStudio plugin for guided and audited RAM-only PID tuning on EpicEFI.

## Public release

Current release: **v0.5.24**  
Plugin: `pid-autotune-plugin-0.5.24.jar`  
Java target: Java 8 bytecode  
SHA-256: `bd2456a7581b8e601d7af4d4598ec941e107bd990f42553d5fa435bb6846d120`

PID Tuner currently focuses on two controller areas:

- **DC-IAC position control**
- **Idle Closed Loop**

## What it does

### DC-IAC

The DC-IAC workflow separates the parts of the controller so each can be tuned and checked deliberately:

- **Bias Curve** captures and evaluates the feed-forward/open-loop position curve.
- **Position PID** captures DC-IAC position response and evaluates P/I/D behaviour.
- **Validation** checks the combined controller behaviour without introducing unrelated tuning changes.
- **Autonomous RAM tuning** can run the intended Bias -> verify -> Position PID sequence using bounded temporary RAM changes, controller readback and restore handling.

Guided tasks remain independently selectable. An unresolved Bias Curve can reduce confidence for a Position PID result, but it does not prevent the user from choosing and measuring Position PID.

### Idle Closed Loop

Idle Closed Loop uses a task-based workflow covering:

- readiness and preparation;
- baseline capture;
- response analysis;
- candidate review;
- manual candidate testing;
- baseline-versus-candidate comparison;
- final robustness validation;
- session and iteration history;
- log import and analysis.

An audited RAM-only Autonomous workflow is also available for controlled P/I/D testing.

## Evidence and comparison

PID Tuner keeps tuning evidence tied to the conditions that produced it. Current safeguards include:

- stale or missing required live channels are rejected rather than reused as cached evidence;
- comparison history remains scoped to the originating ECU configuration;
- custom capture settings are retained with the baseline and checked before comparison;
- P/I/D values exported to CSV remain locale-safe;
- Reset and iteration archiving preserve a usable rollback/history chain;
- recommendation work is refreshed when meaningful evidence changes rather than on every live sample.

A poor tune can still be useful measurement evidence. Missing or invalid measurement data is not treated as valid quantitative evidence.

## Safety boundary

PID Tuner can make temporary working-tune/RAM changes only through its audited Autonomous paths.

- **No automatic Burn.**
- Temporary writes are followed by controller readback/verification.
- Restore/rollback values are captured before candidate testing.
- DC-IAC excitation is bounded and checks target/position response.
- Guided mode does not silently apply recommendations.
- Vehicle safety and final tuning responsibility remain with the operator.

## Install

1. Download `pid-autotune-plugin-0.5.24.jar` from the GitHub Release.
2. Remove older `pid-autotune-plugin-*.jar` versions from the TunerStudio plugin directory.
3. Install the v0.5.24 JAR using TunerStudio's plugin installation method.
4. Restart TunerStudio.
5. Confirm the plugin reports version `0.5.24`.

Keep a known-good tune and normal ECU recovery method available whenever testing working-tune changes.

## Building from source

The TunerStudio Plugin API JAR is a third-party build dependency and is intentionally **not** redistributed in this repository or inside the PID Tuner release JAR.

See `lib/README.md` for local build setup.

Release details are in `CHANGELOG.md` and `docs/RELEASE_0.5.24.md`.
