# Contributing to TranscodeX SDK

We welcome contributions from the community! To maintain the quality, performance, and style consistency of this SDK, please follow these guidelines.

---

## 1. Getting Started

### Prerequisites
- **Java**: JDK 25 or higher
- **Maven**: 3.9+
- **System Dependencies**: Native `ffmpeg` and `ffprobe` binaries must be installed and available in your system `PATH`.

### Clone the Repository
```bash
git clone https://github.com/tanmaysinghx/transcodex-sdk.git
cd transcodex-sdk
```

---

## 2. Code Style Guidelines

We enforce clean, automated styling via Spotless using Google Java Style rules.

- Do **not** manually format code or use IDE default formatters that disagree with Spotless.
- Before committing any code, run:
  ```bash
  mvn spotless:apply
  ```
- Any PR that fails the `mvn spotless:check` step in the CI build will be automatically rejected.

---

## 3. Pull Request Guidelines

1. **Branch Naming**: Use descriptive branch names like `feature/add-hls-transcoder` or `fix/io-stream-leak`.
2. **Local Verification**: Ensure all tests and compilation steps pass locally:
   ```bash
   mvn clean test
   ```
3. **Keep PRs Focused**: Each Pull Request should address a single concern, bug fix, or feature. Larger refactors should be split into multiple PRs.
4. **No Code Without Tests**: Every bug fix or new feature must be accompanied by relevant unit and/or integration tests.

---

## 4. Commit Message Format

We follow the **Conventional Commits** specification. Commits should look like this:

`type(scope): description`

### Allowed Types:
- `feat`: A new feature (e.g. metadata extraction resolution computation).
- `fix`: A bug fix (e.g. fixing process stream buffer block).
- `docs`: Documentation changes only.
- `style`: Changes that do not affect the meaning of the code (white-space, formatting).
- `refactor`: A code change that neither fixes a bug nor adds a feature.
- `perf`: A code change that improves performance (e.g. virtual thread pool swapping).
- `test`: Adding missing tests or correcting existing tests.
- `chore`: Build system modifications or dependency updates.

### Examples:
- `feat(ffmpeg): implement FfmpegThumbnailGenerator`
- `fix(executor): resolve buffer deadlock by draining stdout and stderr asynchronously`
- `docs(readme): update getting started code examples`
