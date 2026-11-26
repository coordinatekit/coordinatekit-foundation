# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-11-26

### Features

- replace public records rule with public api rule ([#70](https://github.com/coordinatekit/coordinatekit-foundation/pull/70))
- resolve compile dependencies and BOMs ([#67](https://github.com/coordinatekit/coordinatekit-foundation/pull/67))
- add support for resolving compile dependencies and BOMs in 'aggregateJavadoc' task ([5267240730594b0](https://github.com/coordinatekit/coordinatekit-foundation/commit/5267240730594b0a296c23a630b40b1dffdca7fb))
- add logger, package cycle, and picocli rules ([#50](https://github.com/coordinatekit/coordinatekit-foundation/pull/50))
- add rules into FoundationArchitecture ([#49](https://github.com/coordinatekit/coordinatekit-foundation/pull/49))
- add FoundationArchitecture ArchUnit rules module ([#44](https://github.com/coordinatekit/coordinatekit-foundation/pull/44))
- render unreleased commits under a releaseVersion ([#42](https://github.com/coordinatekit/coordinatekit-foundation/pull/42))
- replace bump plugin with release rewrite ([#38](https://github.com/coordinatekit/coordinatekit-foundation/pull/38))
- add Gradle plugin that replaces shell script ([#37](https://github.com/coordinatekit/coordinatekit-foundation/pull/37))

### Build

- publish modules through vanniktech maven publish plugin ([#72](https://github.com/coordinatekit/coordinatekit-foundation/pull/72))
- validate central deployment before the release goes live ([#71](https://github.com/coordinatekit/coordinatekit-foundation/pull/71))
- bump actions/setup-java from 6.2.0 to 6.3.0 ([#68](https://github.com/coordinatekit/coordinatekit-foundation/pull/68))
- bump net.ltgt.errorprone from 5.1.1 to 5.2.0 ([#66](https://github.com/coordinatekit/coordinatekit-foundation/pull/66))
- bump org.jline:jline from 4.5.0 to 4.7.0 ([#65](https://github.com/coordinatekit/coordinatekit-foundation/pull/65))
- bump https://github.com/rbubley/mirrors-prettier from v3.9.10 to 3.10.1 ([#62](https://github.com/coordinatekit/coordinatekit-foundation/pull/62))
- bump gradle-wrapper from 9.8.2 to 9.9.1 ([#63](https://github.com/coordinatekit/coordinatekit-foundation/pull/63))
- bump org.eclipse.jgit:org.eclipse.jgit from 7.8.0.202609011348-r to 7.8.1.202611041320-r ([#57](https://github.com/coordinatekit/coordinatekit-foundation/pull/57))
- bump com.github.jk1:gradle-license-report from 3.1.4 to 3.1.5 ([#61](https://github.com/coordinatekit/coordinatekit-foundation/pull/61))
- bump com.diffplug.spotless from 8.10.4 to 8.11.0 ([#60](https://github.com/coordinatekit/coordinatekit-foundation/pull/60))
- bump org.jline:jline from 4.4.8 to 4.5.0 ([#58](https://github.com/coordinatekit/coordinatekit-foundation/pull/58))
- bump https://github.com/compilerla/conventional-pre-commit from v4.4.0 to 4.5.0 ([#56](https://github.com/coordinatekit/coordinatekit-foundation/pull/56))
- bump actions/setup-java from 6.1.0 to 6.2.0 ([#55](https://github.com/coordinatekit/coordinatekit-foundation/pull/55))
- bump com.google.errorprone:error_prone_core from 2.50.0 to 2.51.0 ([#54](https://github.com/coordinatekit/coordinatekit-foundation/pull/54))
- bump com.diffplug.spotless from 8.10.3 to 8.10.4 ([#46](https://github.com/coordinatekit/coordinatekit-foundation/pull/46))
- bump actions/create-github-app-token from 2 to 3 ([#45](https://github.com/coordinatekit/coordinatekit-foundation/pull/45))
- bump https://github.com/python-jsonschema/check-jsonschema from 0.38.2 to 0.39.0 ([#53](https://github.com/coordinatekit/coordinatekit-foundation/pull/53))
- bump errorprone-target from 2.50.0 to 2.51.0 ([#51](https://github.com/coordinatekit/coordinatekit-foundation/pull/51))
- bump gradle-wrapper from 9.8.1 to 9.8.2 ([#52](https://github.com/coordinatekit/coordinatekit-foundation/pull/52))
- bump actions/setup-java from 6.0.1 to 6.1.0 ([#47](https://github.com/coordinatekit/coordinatekit-foundation/pull/47))
- bump org.jline:jline from 4.4.7 to 4.4.8 ([#48](https://github.com/coordinatekit/coordinatekit-foundation/pull/48))
- bump gradle-wrapper from 9.8.0 to 9.8.1 ([#40](https://github.com/coordinatekit/coordinatekit-foundation/pull/40))
- bump org.jline:jline from 4.4.6 to 4.4.7 ([#39](https://github.com/coordinatekit/coordinatekit-foundation/pull/39))
- release through gradle-release and retire the bump-version script ([#43](https://github.com/coordinatekit/coordinatekit-foundation/pull/43))
- remove aggregate-jacoco-gradle plugin ([#41](https://github.com/coordinatekit/coordinatekit-foundation/pull/41))
- remove custom Jacoco stylesheet configuration ([53ece214469ec19](https://github.com/coordinatekit/coordinatekit-foundation/commit/53ece214469ec19e7214ca04d5dcce156d49fa3f))

[0.4.0]: https://github.com/coordinatekit/coordinatekit-foundation/releases/tag/v0.4.0

## [0.3.0] - 2026-10-08

### Features

- add third-party-licenses Gradle plugin ([#35](https://github.com/coordinatekit/coordinatekit-foundation/pull/35))
- introduce aggregate-javadoc and aggregate-jacoco Gradle plugins ([#33](https://github.com/coordinatekit/coordinatekit-foundation/pull/33))
- add changelog-gradle Gradle plugin ([#27](https://github.com/coordinatekit/coordinatekit-foundation/pull/27))

### Build

- bump se.bjurr.gitchangelog:git-changelog-gradle-plugin from 3.4.0 to 3.4.2 ([#34](https://github.com/coordinatekit/coordinatekit-foundation/pull/34))
- bump com.gradleup.nmcp.aggregation from 1.6.1 to 1.6.2 ([#29](https://github.com/coordinatekit/coordinatekit-foundation/pull/29))
- bump net.ltgt.gradle:gradle-errorprone-plugin from 5.1.0 to 5.1.1 ([#31](https://github.com/coordinatekit/coordinatekit-foundation/pull/31))
- bump gradle-wrapper from 9.7.1 to 9.8.0 ([#30](https://github.com/coordinatekit/coordinatekit-foundation/pull/30))
- bump org.slf4j:slf4j-api from 2.0.18 to 2.0.20 ([#28](https://github.com/coordinatekit/coordinatekit-foundation/pull/28))
- bump com.diffplug.spotless from 8.10.0 to 8.10.3 ([#32](https://github.com/coordinatekit/coordinatekit-foundation/pull/32))

[0.3.0]: https://github.com/coordinatekit/coordinatekit-foundation/releases/tag/v0.3.0

## [0.2.0] - 2026-09-27

### Features

- add concordance member-order enforcement ([#24](https://github.com/coordinatekit/coordinatekit-foundation/pull/24))

### Build

- bump https://github.com/python-jsonschema/check-jsonschema ([#25](https://github.com/coordinatekit/coordinatekit-foundation/pull/25))
- bump org.junit:junit-bom from 6.1.2 to 6.1.3 ([#16](https://github.com/coordinatekit/coordinatekit-foundation/pull/16))
- bump com.diffplug.spotless from 8.9.0 to 8.10.0 ([#18](https://github.com/coordinatekit/coordinatekit-foundation/pull/18))
- bump net.ltgt.errorprone from 5.1.0 to 5.1.1 ([#20](https://github.com/coordinatekit/coordinatekit-foundation/pull/20))
- bump gradle-wrapper from 9.6.1 to 9.7.1 ([#19](https://github.com/coordinatekit/coordinatekit-foundation/pull/19))
- bump org.jline:jline from 4.3.1 to 4.4.6 ([#21](https://github.com/coordinatekit/coordinatekit-foundation/pull/21))
- bump actions/setup-java from 5.6.0 to 6.0.1 ([#23](https://github.com/coordinatekit/coordinatekit-foundation/pull/23))
- enable Gradle caching ([7081b02bea1ff3a](https://github.com/coordinatekit/coordinatekit-foundation/commit/7081b02bea1ff3a593b2865680056914491cfb0a))
- remove deprecated functionality ([8833a60430d644b](https://github.com/coordinatekit/coordinatekit-foundation/commit/8833a60430d644b093acf0b50efcadabe7a62a2d))
- bump com.diffplug.spotless from 8.8.0 to 8.9.0 ([#13](https://github.com/coordinatekit/coordinatekit-foundation/pull/13))
- bump org.junit:junit-bom from 6.0.3 to 6.1.2 ([#12](https://github.com/coordinatekit/coordinatekit-foundation/pull/12))
- bump actions/setup-java from 5 to 5.6.0 ([#11](https://github.com/coordinatekit/coordinatekit-foundation/pull/11))

[0.2.0]: https://github.com/coordinatekit/coordinatekit-foundation/releases/tag/v0.2.0

## [0.1.0] - 2026-07-31

### Features

- add CLI brand and conventions modules ([#6](https://github.com/coordinatekit/coordinatekit-foundation/pull/6))

### Build

- bump net.ltgt.errorprone from 4.1.0 to 5.1.0 ([#2](https://github.com/coordinatekit/coordinatekit-foundation/pull/2))
- bump gradle-wrapper from 9.4.0 to 9.6.1 ([#5](https://github.com/coordinatekit/coordinatekit-foundation/pull/5))
- bump com.diffplug.spotless from 8.3.0 to 8.8.0 ([#1](https://github.com/coordinatekit/coordinatekit-foundation/pull/1))
- bump com.google.errorprone:error_prone_core from 2.48.0 to 2.50.0 ([#3](https://github.com/coordinatekit/coordinatekit-foundation/pull/3))
- bump org.slf4j:slf4j-api from 2.0.17 to 2.0.18 ([#4](https://github.com/coordinatekit/coordinatekit-foundation/pull/4))
- implement publishing to Maven Central with snapshot and release automation ([#7](https://github.com/coordinatekit/coordinatekit-foundation/pull/7))

[0.1.0]: https://github.com/coordinatekit/coordinatekit-foundation/releases/tag/v0.1.0
