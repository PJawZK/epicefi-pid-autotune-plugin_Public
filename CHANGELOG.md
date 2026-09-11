# Changelog

## 0.5.24 — 2026-09-11

Status: **PUBLIC RELEASE**

This is the first public PID Tuner release from the current 0.5.x controller-tuning line.

### User workflow

- Consolidated **Idle Closed Loop** into a task-based guided workflow instead of separate top-level P, I and D pages.
- Added separate **Guided** and **Autonomous RAM** operating modes where supported.
- DC-IAC Guided tuning exposes **Bias Curve**, **Position PID** and **Validation** as independently selectable tasks.
- DC-IAC Autonomous RAM tuning follows the intended Bias → verify → Position PID sequence.
- Added session/iteration history, baseline-versus-candidate comparison and rollback-oriented review.
- Added log import and analysis for retained tuning evidence.

### Reliability and evidence handling

- Active Idle captures reject stale or missing required live channels rather than continuing with cached values.
- P/I/D values exported through CSV remain locale-safe.
- Reset/archive handling preserves the active iteration and tuning-history chain correctly.
- Baselines and history retain the ECU configuration that produced them, preventing invalid cross-configuration comparisons.
- Custom capture settings are retained with evidence and checked before comparison.
- CSV export performs file work away from the Swing UI thread.
- Recommendation refresh work is event-driven so live display updates remain responsive.

### Safety

- No automatic Burn is provided.
- Autonomous changes are temporary working-tune/RAM writes only.
- Candidate writes are followed by controller readback verification.
- Restore/rollback values are captured before candidate testing.
- DC-IAC autonomous excitation is bounded and monitors the actual target/position response.
- Guided mode does not silently apply recommendations.

Vehicle-specific tuning results remain the operator's responsibility and should be validated on the vehicle before being treated as final.
