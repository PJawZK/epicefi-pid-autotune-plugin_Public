# TunerStudio Plugin API dependency

PID Tuner requires `TunerStudioPluginAPI.jar` only when compiling the plugin from source.

The API JAR is a separate third-party dependency and is intentionally excluded from this public repository and from PID Tuner release JARs.

## End users

Users installing a prebuilt PID Tuner release do **not** install `TunerStudioPluginAPI.jar` separately. Install only the PID Tuner plugin JAR.

## Building from source

Obtain an authorized copy of the TunerStudio Plugin API from EFI Analytics or from your own TunerStudio installation and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then run:

```bash
mvn clean package
```

The expected plugin output is:

```text
target/pid-autotune-plugin-0.5.24.jar
```

Do not publish, bundle or redistribute the API JAR unless its own applicable licence expressly permits that use.
