These two jars aren't on Maven Central, so they're built from source and kept here:

- `newpipe-extractor-0.27.1.jar` – https://github.com/TeamNewPipe/NewPipeExtractor tag `v0.27.1`
  (extractor + timeago-parser sources, protobuf classes generated with protoc 4.36.2 `--java_out=lite`)
- `nanojson.jar` – https://github.com/TeamNewPipe/nanojson commit `e9d656ddb49a412a5a0a5d5ef20ca7ef09549996`

Both are GPL-3.0 (NewPipeExtractor) / MIT (nanojson); see their repositories for the full licences.
