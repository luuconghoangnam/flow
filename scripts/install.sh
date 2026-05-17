#!/usr/bin/env bash
# Flow Download Manager — Linux installer
# Downloads the latest release from GitHub and installs to ~/.local/

set -euo pipefail

DEPENDENCIES=(curl tar)
APP_NAME="Flow"
PACKAGE_NAME="com.flowspeed.link"
LOG_FILE="/tmp/flow-installer.log"

logger() {
  local timestamp
  timestamp=$(date +"%Y/%m/%d %H:%M:%S")
  if [[ "$1" == "error" ]]; then
    echo -e "${timestamp} -- Flow-Installer [Error]: \033[0;31m${*:2}\033[0m" | tee -a "${LOG_FILE}"
  else
    echo -e "${timestamp} -- Flow-Installer [Info]: ${*:2}" | tee -a "${LOG_FILE}"
  fi
}

remove_if_exists() {
  local target="$1"
  if [ -z "$target" ]; then
    logger error "No target specified in remove_if_exists"
    return 1
  fi
  if [ -e "$target" ]; then
    logger info "Removing: $target"
    rm -rf "$target"
  fi
}

detect_package_manager() {
  if [ -f /etc/os-release ]; then
    source /etc/os-release
    local OS="${NAME}"
  elif type lsb_release >/dev/null 2>&1; then
    local OS
    OS=$(lsb_release -si)
  else
    logger error "Unsupported Linux distribution."
    exit 1
  fi

  if grep -qE 'Debian|Ubuntu' <<< "$OS"; then
    systemPackage="apt"
  elif grep -qE 'Fedora|CentOS|Red Hat|AlmaLinux' <<< "$OS"; then
    systemPackage="dnf"
  fi
}

detect_package_manager

install_dependencies() {
  local answer
  read -rp "Install $1? [Y/n]: " answer
  answer=${answer:-Y}
  case $answer in
    [Yy]*)
      sudo "${systemPackage}" update -y
      sudo "${systemPackage}" install -y "$1"
      ;;
    [Nn]*) logger info "Skipping $1." ;;
    *) logger error "Please answer yes or no."; install_dependencies "$1" ;;
  esac
}

check_dependencies() {
  for pkg in "${DEPENDENCIES[@]}"; do
    if ! command -v "$pkg" >/dev/null 2>&1; then
      logger info "$pkg not found. Installing..."
      install_dependencies "$pkg"
    fi
  done
}

get_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "x64" ;;
    aarch64|arm64) echo "arm64" ;;
    *) logger error "Unsupported architecture: $(uname -m)"; return 1 ;;
  esac
}

PLATFORM="linux"
ARCH="$(get_arch)" || exit 1
EXT="tar.gz"
RELEASE_URL="https://api.github.com/repos/luuconghoangnam/flowspeed.link/releases/latest"
GITHUB_RELEASE_DOWNLOAD="https://github.com/luuconghoangnam/flowspeed.link/releases/download"
LATEST_VERSION=$(curl -fSs "${RELEASE_URL}" | grep '"tag_name":' | sed -E 's/.*"tag_name": ?"([^"]+)".*/\1/')
ASSET_NAME="${APP_NAME}_${LATEST_VERSION:1}_${PLATFORM}_${ARCH}.${EXT}"
DOWNLOAD_URL="${GITHUB_RELEASE_DOWNLOAD}/${LATEST_VERSION}/${ASSET_NAME}"
APP_PATH="$HOME/.local/${APP_NAME}"
BINARY_PATH="${APP_PATH}/bin/${APP_NAME}"
ICON_PATH="${APP_PATH}/lib/${APP_NAME}.png"

delete_old_version() {
  local PIDS
  PIDS=$(pidof "$APP_NAME") || true
  if [ -n "$PIDS" ]; then
    kill "$PIDS" 2>/dev/null || true
    sleep 2
    PIDS=$(pidof "$APP_NAME") || true
    [ -n "$PIDS" ] && kill -9 "$PIDS" 2>/dev/null || true
  fi
  remove_if_exists "$HOME/.local/bin/${APP_NAME}"
  remove_if_exists "$HOME/.local/${APP_NAME}"
}

generate_desktop_file() {
  cat > "$HOME/.local/share/applications/${PACKAGE_NAME}.desktop" <<EOF
[Desktop Entry]
Name=${APP_NAME}
Comment=Fast, open-source download manager
GenericName=Downloader
Categories=Utility;Network;
Exec="${BINARY_PATH}"
Icon=${ICON_PATH}
Terminal=false
Type=Application
StartupWMClass=com-flowspeed-link-desktop-AppKt
EOF
}

download_archive() {
  remove_if_exists "/tmp/${ASSET_NAME}"
  logger info "Downloading Flow ${LATEST_VERSION}..."
  if curl --progress-bar -fSL -o "/tmp/${ASSET_NAME}" "${DOWNLOAD_URL}"; then
    logger info "Download complete."
  else
    logger error "Download failed."
    remove_if_exists "/tmp/${ASSET_NAME}"
    exit 1
  fi
}

install_app() {
  logger info "Installing Flow..."
  mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications"
  tar -xzf "/tmp/${ASSET_NAME}" -C "$HOME/.local"
  remove_if_exists "/tmp/${ASSET_NAME}"
  ln -sf "${BINARY_PATH}" "$HOME/.local/bin/${APP_NAME}"
  generate_desktop_file
  logger info "Flow installed. Run '${APP_NAME}' or find it in your app menu."
  logger info "Ensure \$HOME/.local/bin is in your PATH."
}

check_if_installed() {
  "${APP_NAME}" --version 2>/dev/null || echo ""
}

main() {
  echo "" > "${LOG_FILE}"
  check_dependencies
  local installed_version
  installed_version=$(check_if_installed)
  if [ -n "$installed_version" ]; then
    logger info "Flow v${installed_version} is installed."
    if [ "$installed_version" != "${LATEST_VERSION:1}" ]; then
      logger info "Updating to ${LATEST_VERSION}..."
      download_archive
      delete_old_version
      install_app
    else
      logger info "Already up to date."
    fi
  else
    download_archive
    install_app
  fi
}

main "$@"
