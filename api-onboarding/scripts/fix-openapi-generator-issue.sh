#!/usr/bin/env bash
#
# Workaround for this known issue: OpenAPITools/openapi-generator#19019
#
GENERATED_API_DIR="${1:?usage: script.sh <generated_api_dir>}"

find "$GENERATED_API_DIR" -name "*.java" -exec \
  sed -i.bak -E 's/@RequestParam\(value = "", required = ([a-z]+)\) @Nullable Map<String, Object> filters/@RequestParam(value = "filters", required = \1) @Nullable Map<String, Object> filters/g' {} \;
find "$GENERATED_API_DIR" -name "*.bak" -delete