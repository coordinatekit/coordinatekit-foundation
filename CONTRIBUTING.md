# Contributing to CoordinateKit Foundation

## Mutation Testing

[PITest](https://pitest.org) makes small changes to a module's compiled code, called mutants, and runs the module's tests against each one. It covers every module except `conventions`, which has no code. It runs only when requested, not as part of `check` or `build`, and by default it analyzes the whole module.

```bash
./gradlew pitest              # every module
./gradlew :cli-brand:pitest   # one module
```

Each module writes its own HTML report to `<module>/build/reports/pitest/index.html`, and there is no combined report.

Gradle skips a module's run when none of its inputs have changed. The task then reports `UP-TO-DATE` or `FROM-CACHE` and prints no terminal summary. To run the analysis anyway, add `--rerun` to either command.

On a pull request, CI mutates only the classes whose main source or test changed. To narrow a local run, pass fully qualified class names, separated by commas:

```bash
./gradlew :cli-brand:pitest -Ppitest.targetClasses=org.coordinatekit.foundation.cli.brand.Banner
```

A `SURVIVED` mutant means a test ran the line but no assertion caught the change. A `NO_COVERAGE` mutant means that no test PITest ran reached the line. To judge how well the assertions catch changes, read "Test strength" in the terminal summary, which is the share of mutants detected among those that tests ran.

PITest skips the tests tagged `testkit`. Such a test runs a real Gradle build in a separate daemon, which loads the plugin from a jar on disk rather than from the mutated classes, so it cannot catch a mutant. In a plugin module, code that only a TestKit test reaches therefore shows `NO_COVERAGE`, even though `./gradlew test` runs it. This is often the work of a task action, such as rendering `THIRD-PARTY-LICENSES.txt`. It can also be wiring that takes effect only in a real build, such as the flags `concordance-gradle` adds to a compilation.
