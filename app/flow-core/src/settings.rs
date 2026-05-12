use std::path::Path;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FlowSettings {
    pub default_download_folder: Option<String>,
    pub max_concurrent_downloads: usize,
    pub thread_count: usize,
    pub auto_start: bool,
    pub use_system_tray: bool,
    pub browser_integration_enabled: bool,
    pub clipboard_monitoring: bool,
    pub proxy: ProxySettings,
    pub per_host: Vec<PerHostSettings>,
}

impl Default for FlowSettings {
    fn default() -> Self {
        Self {
            default_download_folder: None,
            max_concurrent_downloads: 3,
            thread_count: 8,
            auto_start: false,
            use_system_tray: false,
            browser_integration_enabled: true,
            clipboard_monitoring: false,
            proxy: ProxySettings::default(),
            per_host: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProxySettings {
    pub mode: ProxyMode,
    pub manual: ManualProxy,
    pub exclude_url_patterns: Vec<String>,
}

impl Default for ProxySettings {
    fn default() -> Self {
        Self {
            mode: ProxyMode::Direct,
            manual: ManualProxy::default(),
            exclude_url_patterns: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ProxyMode {
    Direct,
    System,
    Manual,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManualProxy {
    pub scheme: String,
    pub host: String,
    pub port: u16,
    pub username: Option<String>,
    pub password: Option<String>,
}

impl Default for ManualProxy {
    fn default() -> Self {
        Self {
            scheme: "http".to_string(),
            host: "127.0.0.1".to_string(),
            port: 2080,
            username: None,
            password: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerHostSettings {
    pub host: String,
    pub username: Option<String>,
    pub password: Option<String>,
    pub user_agent: Option<String>,
    pub thread_count: Option<usize>,
}

pub fn load_settings(path: &Path) -> FlowSettings {
    std::fs::read_to_string(path)
        .ok()
        .and_then(|value| serde_json::from_str(&value).ok())
        .unwrap_or_default()
}

pub fn save_settings(path: &Path, settings: &FlowSettings) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let text = serde_json::to_string_pretty(settings).map_err(|e| e.to_string())?;
    std::fs::write(path, text).map_err(|e| e.to_string())
}

pub fn per_host_for_url<'a>(settings: &'a FlowSettings, url: &str) -> Option<&'a PerHostSettings> {
    let host = reqwest::Url::parse(url).ok()?.host_str()?.to_string();
    settings
        .per_host
        .iter()
        .filter(|item| !item.host.trim().is_empty())
        .min_by_key(|item| item.host.matches('*').count())
        .filter(|item| wildcard_match(&item.host, &host))
}

pub fn should_use_manual_proxy(settings: &ProxySettings, url: &str) -> bool {
    settings.mode == ProxyMode::Manual
        && !settings
            .exclude_url_patterns
            .iter()
            .any(|pattern| wildcard_match(pattern, url))
}

fn wildcard_match(pattern: &str, value: &str) -> bool {
    let mut remainder = value;
    let parts: Vec<&str> = pattern.split('*').collect();
    if parts.len() == 1 {
        return pattern == value;
    }
    if !pattern.starts_with('*') {
        let first = parts[0];
        if !remainder.starts_with(first) {
            return false;
        }
        remainder = &remainder[first.len()..];
    }
    for part in parts.iter().filter(|part| !part.is_empty()) {
        if let Some(index) = remainder.find(part) {
            remainder = &remainder[index + part.len()..];
        } else {
            return false;
        }
    }
    pattern.ends_with('*') || parts.last().is_some_and(|last| value.ends_with(last))
}
