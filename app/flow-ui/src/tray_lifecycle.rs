use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use slint::{CloseRequestResponse, ComponentHandle};
use tray_icon::menu::{Menu, MenuItem};
use tray_icon::TrayIconBuilder;

use crate::MainWindow;
use flow_core::{flow_data_dir, load_settings};

pub(crate) struct TrayContext {
    _tray_icon: tray_icon::TrayIcon,
    _timer: slint::Timer,
}

pub(crate) fn setup_tray_if_enabled(app: &MainWindow) -> Option<TrayContext> {
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    if !settings.use_system_tray {
        return None;
    }

    let menu = Menu::new();
    let show_hide = MenuItem::new("Open Flow", true, None);
    let quit = MenuItem::new("Quit Flow", true, None);
    let _ = menu.append(&show_hide);
    let _ = menu.append(&quit);

    let tray_icon = TrayIconBuilder::new()
        .with_menu(Box::new(menu))
        .with_tooltip("Flow Download Manager")
        .build()
        .ok()?;

    let close_notice_seen = Arc::new(AtomicBool::new(false));
    let close_notice_seen_for_close = Arc::clone(&close_notice_seen);
    let close_weak = app.as_weak();
    app.window().on_close_requested(move || {
        if let Some(app) = close_weak.upgrade() {
            app.set_status_message("Flow is still running in system tray. Use tray icon to reopen or quit.".into());
            if !close_notice_seen_for_close.swap(true, Ordering::Relaxed) {
                app.set_overlay_notification_text("Flow is minimized to tray and still running".into());
                app.set_show_overlay_notification(true);
            }
        }
        CloseRequestResponse::HideWindow
    });

    let show_hide_id = show_hide.id().clone();
    let quit_id = quit.id().clone();
    let app_weak = app.as_weak();
    let timer = slint::Timer::default();
    timer.start(
        slint::TimerMode::Repeated,
        std::time::Duration::from_millis(180),
        move || {
            while let Ok(event) = tray_icon::menu::MenuEvent::receiver().try_recv() {
                if event.id == show_hide_id {
                    let weak = app_weak.clone();
                    let _ = weak.upgrade_in_event_loop(|app| {
                        let visible = app.window().is_visible();
                        if visible {
                            app.window().hide().ok();
                            app.set_status_message("Flow minimized to system tray".into());
                        } else {
                            app.window().show().ok();
                            app.window().set_minimized(false);
                            app.set_status_message("Flow restored from system tray".into());
                        }
                    });
                } else if event.id == quit_id {
                    slint::quit_event_loop().ok();
                }
            }
        },
    );

    Some(TrayContext {
        _tray_icon: tray_icon,
        _timer: timer,
    })
}
