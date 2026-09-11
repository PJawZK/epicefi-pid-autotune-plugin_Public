# PID Tuner user guide

PID Tuner provides two main tuning areas: **DC-IAC position control** and **Idle Closed Loop**.

## DC-IAC

### Guided

Use Guided mode when you want to choose the next tuning task yourself.

**Bias Curve** measures and evaluates the feed-forward/open-loop position curve. It is concerned with the position request needed by the DC-IAC controller, not with achieving the engine's final idle-speed target.

**Position PID** measures how well the DC-IAC position controller follows its own requested position and evaluates the position-control P/I/D response.

**Validation** checks the combined DC-IAC behavior after tuning. It is intended to confirm operation rather than introduce unrelated tuning changes.

Bias state can affect confidence in later conclusions, but Guided mode does not force you through a rigid task order unless a real safety or data-integrity requirement makes evidence unusable.

### Autonomous RAM

Autonomous RAM mode is intended for controlled automatic testing. It owns the tuning sequence and can temporarily manipulate supported working-tune settings so it can observe the controller response itself.

For DC-IAC it follows the intended sequence:

1. characterize/tune Bias;
2. verify the resulting operating point;
3. test Position PID candidates;
4. compare the result;
5. restore when required.

Autonomous changes are temporary RAM changes and are read back from the controller after writing. There is no automatic Burn.

## Idle Closed Loop

Idle Closed Loop controls engine speed around the ECU idle target. This is separate from the DC-IAC position controller: Idle Closed Loop requests more or less actuator position when engine RPM differs from the idle target, while the DC-IAC position loop controls how the actuator reaches its own requested position.

The Guided workflow is organized around the tuning job rather than separate P/I/D pages:

1. readiness and preparation;
2. baseline capture;
3. response analysis;
4. candidate review;
5. manual candidate testing;
6. baseline-versus-candidate comparison;
7. final robustness validation.

Sessions & Logs retain iteration history, imported log analysis and accepted/rejected evidence.

Autonomous RAM mode can perform audited temporary P/I/D candidate tests where the required controller mapping and safety gates are available.

## Evidence quality

A poor controller response can still be useful tuning evidence. PID Tuner therefore separates **bad tuning behavior** from **bad measurement data**.

Examples of invalid quantitative evidence include missing/stale required channels, incompatible ECU configuration, or capture settings that do not match the baseline comparison contract.

## Before using Autonomous RAM

- Keep a known-good tune available.
- Make sure the vehicle is in a safe stationary test condition when the task requires it.
- Confirm the plugin resolves the expected EpicEFI settings/channels.
- Do not rely on temporary RAM values as permanent calibration.
- Review the result before making any permanent ECU change.
