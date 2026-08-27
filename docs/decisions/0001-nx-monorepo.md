# ADR-0001: Nx with native ecosystem builds

Status: Accepted — 2026-08-25

## Decision

Use pnpm and Nx as the repository task graph. Integrate the Java project with the official `@nx/gradle` plugin. Keep Gradle authoritative for Java and Vite/pnpm authoritative for the web application.

## Rationale

Nx understands both project types, provides affected execution and caching, and does not require replacing mature Spring or Vue workflows. Bazel is valuable for very large, hermetic build graphs, but at this size its rule/toolchain ownership and Spring/Node integration cost would exceed the demonstrated benefit. This choice can be revisited if build scale, remote execution, or hermeticity becomes a measured constraint.

## Consequences

Cross-project tasks have one entry point while every underlying command remains debuggable. Adding unsupported wrapper scripts around Gradle is prohibited; use an official Nx plugin or leave a task native.
