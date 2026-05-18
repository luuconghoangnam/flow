//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"
	"unsafe"
)

// Windows system tray implementation using Shell_NotifyIcon API.
// No CGo, no external dependencies - pure syscall.

var (
	shell32              = syscall.NewLazyDLL("shell32.dll")
	user32               = syscall.NewLazyDLL("user32.dll")
	pShellNotifyIcon     = shell32.NewProc("Shell_NotifyIconW")
	pCreateWindowEx      = user32.NewProc("CreateWindowExW")
	pDefWindowProc       = user32.NewProc("DefWindowProcW")
	pRegisterClassEx     = user32.NewProc("RegisterClassExW")
	pGetMessage          = user32.NewProc("GetMessageW")
	pTranslateMessage    = user32.NewProc("TranslateMessage")
	pDispatchMessage     = user32.NewProc("DispatchMessageW")
	pPostQuitMessage     = user32.NewProc("PostQuitMessage")
	pCreatePopupMenu     = user32.NewProc("CreatePopupMenu")
	pAppendMenu          = user32.NewProc("AppendMenuW")
	pTrackPopupMenu      = user32.NewProc("TrackPopupMenu")
	pDestroyMenu         = user32.NewProc("DestroyMenu")
	pGetCursorPos        = user32.NewProc("GetCursorPos")
	pSetForegroundWindow = user32.NewProc("SetForegroundWindow")
	pLoadIcon            = user32.NewProc("LoadIconW")
)

const (
	nimAdd    = 0x00000000
	nimModify = 0x00000001
	nimDelete = 0x00000002

	nifMessage = 0x00000001
	nifIcon    = 0x00000002
	nifTip     = 0x00000004

	wmApp         = 0x8000
	wmTrayIcon    = wmApp + 1
	wmLButtonUp   = 0x0202
	wmRButtonUp   = 0x0205
	wmCommand     = 0x0111

	idShowUI = 1001
	idExit   = 1002

	mfString = 0x00000000
	tpmLeftAlign = 0x0000
)

type notifyIconData struct {
	cbSize           uint32
	hWnd             uintptr
	uID              uint32
	uFlags           uint32
	uCallbackMessage uint32
	hIcon            uintptr
	szTip            [128]uint16
	dwState          uint32
	dwStateMask      uint32
	szInfo           [256]uint16
	uVersion         uint32
	szInfoTitle      [64]uint16
	dwInfoFlags      uint32
	guidItem         [16]byte
	hBalloonIcon     uintptr
}

type point struct {
	x, y int32
}

type msg struct {
	hwnd    uintptr
	message uint32
	wParam  uintptr
	lParam  uintptr
	time    uint32
	pt      point
}

type wndClassEx struct {
	cbSize        uint32
	style         uint32
	lpfnWndProc   uintptr
	cbClsExtra    int32
	cbWndExtra    int32
	hInstance     uintptr
	hIcon         uintptr
	hCursor       uintptr
	hbrBackground uintptr
	lpszMenuName  *uint16
	lpszClassName *uint16
	hIconSm       uintptr
}

var (
	trayHwnd    uintptr
	trayOnShow  func()
	trayOnExit  func()
	trayMu      sync.Mutex
)

// RunTray starts the system tray icon and message loop.
// This blocks until the tray is closed.
func RunTray(tooltip string, onShow func(), onExit func()) {
	trayOnShow = onShow
	trayOnExit = onExit

	className := syscall.StringToUTF16Ptr("FlowServiceTray")

	wc := wndClassEx{
		lpfnWndProc:   syscall.NewCallback(trayWndProc),
		lpszClassName: className,
	}
	wc.cbSize = uint32(unsafe.Sizeof(wc))
	pRegisterClassEx.Call(uintptr(unsafe.Pointer(&wc)))

	hwnd, _, _ := pCreateWindowEx.Call(
		0, uintptr(unsafe.Pointer(className)), 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0,
	)
	trayHwnd = hwnd

	// Load default app icon
	icon, _, _ := pLoadIcon.Call(0, uintptr(32512)) // IDI_APPLICATION

	// Add tray icon
	nid := notifyIconData{
		hWnd:             hwnd,
		uID:              1,
		uFlags:           nifMessage | nifIcon | nifTip,
		uCallbackMessage: wmTrayIcon,
		hIcon:            icon,
	}
	nid.cbSize = uint32(unsafe.Sizeof(nid))
	copy(nid.szTip[:], syscall.StringToUTF16(tooltip))
	pShellNotifyIcon.Call(nimAdd, uintptr(unsafe.Pointer(&nid)))

	// Message loop
	var m msg
	for {
		ret, _, _ := pGetMessage.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if ret == 0 {
			break
		}
		pTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		pDispatchMessage.Call(uintptr(unsafe.Pointer(&m)))
	}

	// Remove tray icon
	pShellNotifyIcon.Call(nimDelete, uintptr(unsafe.Pointer(&nid)))
}

func trayWndProc(hwnd, msg, wParam, lParam uintptr) uintptr {
	switch msg {
	case wmTrayIcon:
		switch lParam {
		case wmLButtonUp:
			if trayOnShow != nil {
				go trayOnShow()
			}
		case wmRButtonUp:
			showTrayMenu(hwnd)
		}
		return 0
	case wmCommand:
		switch wParam {
		case idShowUI:
			if trayOnShow != nil {
				go trayOnShow()
			}
		case idExit:
			if trayOnExit != nil {
				go trayOnExit()
			}
			pPostQuitMessage.Call(0)
		}
		return 0
	}
	ret, _, _ := pDefWindowProc.Call(hwnd, msg, wParam, lParam)
	return ret
}

func showTrayMenu(hwnd uintptr) {
	menu, _, _ := pCreatePopupMenu.Call()
	pAppendMenu.Call(menu, mfString, idShowUI, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr("Show Downloads"))))
	pAppendMenu.Call(menu, mfString, idExit, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr("Exit"))))

	var pt point
	pGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))
	pSetForegroundWindow.Call(hwnd)
	pTrackPopupMenu.Call(menu, tpmLeftAlign, uintptr(pt.x), uintptr(pt.y), 0, hwnd, 0)
	pDestroyMenu.Call(menu)
}

// LaunchUI starts the Kotlin UI process.
func LaunchUI() {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow.exe")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "Flow.exe"
	}
	cmd := exec.Command(uiExe)
	cmd.Dir = dir
	cmd.Start()
}

// LaunchUIWithDownload starts UI and passes a download URL to show the add-download dialog.
func LaunchUIWithDownload(url string) {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow.exe")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "Flow.exe"
	}
	cmd := exec.Command(uiExe, "--add-download", url)
	cmd.Dir = dir
	cmd.Start()
}
