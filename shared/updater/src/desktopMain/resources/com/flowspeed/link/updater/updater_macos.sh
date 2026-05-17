#!/usr/bin/env bash
# Flow Download Manager — macOS updater script
# Called by the app to apply an update while it is not running.

APP_NAME="Flow"

awaitTermination() {
  local processName="${1:?}"
  local count=0
  while true; do
    local pids
    pids=$(pgrep -x "$processName") || true
    [ -z "$pids" ] && break
    if [ $count -ge 10 ]; then
      echo "Timeout waiting for $processName to terminate."
      break
    fi
    echo "Waiting for $processName to stop..."
    sleep 1
    ((count++))
  done
}

stopApp() {
  echo "Stopping ${APP_NAME}..."
  local pids
  pids=$(pgrep -x "$APP_NAME") || true
  if [ -z "$pids" ]; then
    echo "${APP_NAME} is not running."
    return 0
  fi
  kill -9 $pids
  awaitTermination "$APP_NAME"
  echo "${APP_NAME} stopped."
}

removeCurrentInstallation() {
  local installationFolder="${1:?}"
  echo "Removing current installation: $installationFolder"
  rm -rf "$installationFolder"
}

copyUpdateToInstallationFolder() {
  local updateFile="$1"
  local installationFolder="${2:?}"
  echo "Applying update..."
  cp -Rp "$updateFile" "$installationFolder"
}

removeUpdateFiles() {
  local updateFile="$1"
  echo "Cleaning up update files..."
  rm -rf "$updateFile"
}

executeProgram() {
  local installationFolder="$1"
  echo "Starting ${APP_NAME}..."
  open "$installationFolder"
}

main() {
  local updateFile="$1"
  local installationFolder="$2"
  stopApp || { executeProgram "$installationFolder"; exit 1; }
  removeCurrentInstallation "$installationFolder"
  copyUpdateToInstallationFolder "$updateFile" "$installationFolder"
  removeUpdateFiles "$updateFile"
  executeProgram "$installationFolder"
}

main "$@"
