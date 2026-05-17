<div align="center">
  <img width="180" src="assets/logo/app_logo_with_background.svg" alt="Flow Logo">
</div>
<h1 align="center">Flow</h1>
<p align="center">A fast, modern download manager for Desktop and Android.</p>

## Features

- ⚡️ Faster Download Speed
- ⏰ Queues and Schedulers
- 🌐 Browser Extensions
- 💻 Multiplatform (Android / Windows / Linux / Mac)
- 🌙 Multiple Themes (Dark/Light/Black and more) with modern UI
- ❤️ Free and Open Source

## Build From Source

1. Clone the project.
2. Download and extract the [JBR](https://github.com/JetBrains/JetBrainsRuntime/releases), and make it available by either:
    - Adding it to your `PATH`, or
    - Setting the `JAVA_HOME` environment variable to its installation path.
3. Navigate to the project directory, open your terminal and execute:
    ```bash
    ./gradlew createReleaseFolderForCi
    ```
4. The output will be available at `<project_dir>/build/ci-release`

## License

Apache License 2.0
