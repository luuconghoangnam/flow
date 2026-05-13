use slint::SharedString;

use crate::{
    infer_file_name,
    is_http_url,
    normalize_category_input,
    resolve_default_download_folder,
    AddUrlDialog,
};

pub(crate) struct ManualDownloadSubmission {
    pub(crate) url: String,
    pub(crate) file_name: String,
    pub(crate) output_dir: String,
    pub(crate) category: String,
}

pub(crate) fn resolve_manual_file_name(url: &str, file_name: &str) -> String {
    if file_name.trim().is_empty() {
        infer_file_name(url)
    } else {
        file_name.trim().to_string()
    }
}

pub(crate) fn resolve_manual_output_dir(output_dir: &str) -> String {
    if output_dir.trim().is_empty() {
        resolve_default_download_folder()
    } else {
        output_dir.trim().to_string()
    }
}

pub(crate) fn resolve_manual_category(file_name: &str, url: &str, category: &str) -> String {
    normalize_category_input(file_name, url, category)
}

pub(crate) fn render_add_url_preview(
    dialog: &AddUrlDialog,
    url: &str,
    file_name: &str,
    output_dir: &str,
    category: &str,
) {
    let resolved_file_name = resolve_manual_file_name(url, file_name);
    let resolved_output_dir = resolve_manual_output_dir(output_dir);
    let resolved_category = resolve_manual_category(resolved_file_name.as_str(), url, category);
    let url_trimmed = url.trim();
    let url_valid = url_trimmed.is_empty() || is_http_url(url_trimmed);
    let hint = if url_trimmed.is_empty() {
        "Paste a direct HTTP or HTTPS link. Flow will infer the file name and category automatically.".to_string()
    } else if url_valid {
        "Download looks ready. Review the resolved file name, folder, and category before continuing.".to_string()
    } else {
        "Only HTTP and HTTPS URLs are supported in this dialog".to_string()
    };

    dialog.set_url_valid(url_valid);
    dialog.set_dialog_hint(SharedString::from(hint));
    dialog.set_resolved_file_name(SharedString::from(resolved_file_name));
    dialog.set_resolved_output_dir(SharedString::from(resolved_output_dir));
    dialog.set_category_hint(SharedString::from(resolved_category));
}

pub(crate) fn render_add_url_error(
    dialog: &AddUrlDialog,
    message: &str,
    url: &str,
    file_name: &str,
    output_dir: &str,
    category: &str,
) {
    dialog.set_url_valid(false);
    dialog.set_dialog_hint(SharedString::from(message.to_string()));
    dialog.set_resolved_file_name(SharedString::from(resolve_manual_file_name(url, file_name)));
    dialog.set_resolved_output_dir(SharedString::from(resolve_manual_output_dir(output_dir)));
    dialog.set_category_hint(SharedString::from(resolve_manual_category(file_name, url, category)));
}

pub(crate) fn prepare_manual_download_submission(
    url: &str,
    file_name: &str,
    output_dir: &str,
    category: &str,
) -> Result<ManualDownloadSubmission, String> {
    let normalized_url = url.trim().to_string();
    if normalized_url.is_empty() {
        return Err("Download URL cannot be empty".to_string());
    }
    if !is_http_url(normalized_url.as_str()) {
        return Err("Only HTTP and HTTPS URLs are supported in this dialog".to_string());
    }
    let resolved_file_name = resolve_manual_file_name(normalized_url.as_str(), file_name);
    let resolved_output_dir = resolve_manual_output_dir(output_dir);
    if resolved_output_dir.trim().is_empty() {
        return Err("Download folder cannot be empty".to_string());
    }
    Ok(ManualDownloadSubmission {
        url: normalized_url.clone(),
        file_name: resolved_file_name.clone(),
        output_dir: resolved_output_dir,
        category: resolve_manual_category(resolved_file_name.as_str(), normalized_url.as_str(), category),
    })
}
