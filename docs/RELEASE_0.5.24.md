# PID Tuner v0.5.24

**Public full release — 2026-09-11**

PID Tuner v0.5.24 is the first public release of the current EpicEFI PID tuning workspace.

## Included tuning areas

### DC-IAC position control

- Guided Bias Curve tuning
- Guided Position PID tuning
- Whole-system validation
- Audited Autonomous RAM sequence for Bias → verify → Position PID
- Bounded excitation, exact readback checks and restore handling

### Idle Closed Loop

- Task-based Guided tuning workflow
- Baseline capture and response analysis
- Candidate review and manual candidate testing
- Baseline-versus-candidate comparison
- Final robustness validation
- Session and iteration history
- Log import and analysis
- Audited Autonomous RAM P/I/D testing

## Reliability improvements in this release

v0.5.24 includes hardened handling for stale live telemetry, locale-safe gain CSV data, Reset/archive lifecycle behavior, ECU-configuration-aware history, custom-capture comparison fidelity, background CSV export and event-driven recommendation refresh.

These changes are intended to make longer tuning sessions more dependable and to prevent evidence from being compared when it was captured under incompatible conditions.

## Safety boundary

PID Tuner deliberately keeps permanent ECU changes outside automatic control:

- no automatic Burn;
- Autonomous writes are temporary working-tune/RAM changes;
- every supported candidate write is verified by readback;
- rollback/restore values are captured before candidate testing;
- Guided mode does not silently apply recommendations.

## Installation

1. Download `pid-autotune-plugin-0.5.24.jar`.
2. Remove older PID Tuner plugin JARs from the TunerStudio plugin directory.
3. Install the v0.5.24 JAR using TunerStudio's normal plugin installation method.
4. Restart TunerStudio.
5. Confirm the plugin reports version `0.5.24`.

The TunerStudio Plugin API JAR is **not** included and is not required separately for normal end-user installation.\n\n## Release artifact\n\n**File:** `pid-autotune-plugin-0.5.24.jar`  \n**SHA-256:** `bd2456a7581b8e601d7af4d4598ec941e107bd990f42553d5fa435bb6846d120`

## Validation note

This public release is built from the current hardened 0.5.24 source. Software checks can verify packaging, controller access behavior and regression coverage, but they do not make a vehicle-specific numerical PID recommendation universally correct. Review and validate tuning results on the actual vehicle.
