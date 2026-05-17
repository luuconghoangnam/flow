#!/usr/bin/env bash
# Flow Download Manager — Linux uninstaller

set -euo pipefail

APP_NAME="Flow"
PACKAGE_NAME="com.flowspeed.link"
LOG_FILE="/tmp/flow-uninstaller.log"

logger() {
  local timestamp
  timestamp=$(date +"%Y/%m/%d %H:%M:%S")
  if [[ "$1" == "error" ]]; then
    echo -e "${timestamp} -- Flow-Uninstaller [Error]: \033[0;31m${*:2}\033[0m" | tee -a "${LOG_FILE}"
  else
    echo -e "${timestamp} -- Flow-Uninstaller [Info]: ${*:2}" | tee -a "${LOG_FILE}"
  fi
}

remove_if_exists() {
  local target="$1"
  if [ -e "$target" ]; then
    logger info "Removing: $target"
    rm -rf "$target"
  fi
}

delete_config_dir() {
  local answer
  read -rp "Also remove settings and download history (~/.flow)? [Y/n]: " answer
  answer=${answer:-Y}
  case $answer in
    [Yy]*) remove_if_exists "$HOME/.flow" ;;
    [Nn]*) logger info "Keeping $HOME/.flow — remove manually if needed." ;;
    *) logger error "Please answer yes or no."; delete_config_dir ;;
  esac
}

uninstall_app() {
  local PIDS
  PIDS=$(pidof "$APP_NAME") || true
  if [ -n "$PIDS" ]; then
    logger info "Stopping ${APP_NAME}..."
    kill "$PIDS" 2>/dev/null || true
    sleep 2
    PIDS=$(pidof "$APP_NAME") || true
    [ -n "$PIDS" ] && kill -9 "$PIDS" 2>/dev/null || true
  fi

  remove_if_exists "$HOME/.local/share/applications/${PACKAGE_NAME}.desktop"
  remove_if_exists "$HOME/.local/bin/${APP_NAME}"
  remove_if_exists "$HOME/.local/${APP_NAME}"
  remove_if_exists "$HOME/.config/autostart/${PACKAGE_NAME}.desktop"

  if [ -e "$HOME/.flow" ]; then
    delete_config_dir
  fi

  logger info "Flow uninstalled successfully."
}

main() {
  uninstall_app
}

main "$@"
