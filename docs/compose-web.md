# Compose Web (wasm)

This project includes a browser entrypoint at `:app:web` built with Compose Multiplatform `wasmJs`.

## Local development

MediaMP wasm support is currently taken from the local composite build. Clone it next to this repository and point Gradle at it:

```bash
git clone https://github.com/open-ani/mediamp.git ~/Projects/mediamp
grep -q '^ani.build.mediamp.path=' local.properties 2>/dev/null || \
  printf 'ani.build.mediamp.path=%s\n' "$HOME/Projects/mediamp" >> local.properties
```

Start the webpack dev server:

```bash
./gradlew --no-configuration-cache :app:web:wasmJsBrowserDevelopmentRun
```

Then open <http://localhost:8080/>.

## Production build

```bash
./gradlew --no-configuration-cache :app:web:wasmJsBrowserDistribution
```

The static deployable output is generated at:

```text
app/web/build/dist/wasmJs/productionExecutable/
```

Deploy that directory as static web assets. The host must serve `.wasm` files with the
`application/wasm` MIME type and should enable long-cache headers for hashed
`.wasm`/chunk files while keeping `index.html` short-cached.
