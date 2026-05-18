//go:build windows

package main

import (
	"embed"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"
	"unsafe"
)

// Windows system tray implementation using Shell_NotifyIcon API.
// No CGo, no external dependencies - pure syscall.

//go:embed assets/icon.ico
var iconFS embed.FS

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
	pCreateIconFromResourceEx = user32.NewProc("CreateIconFromResourceEx")
	pDestroyIcon         = user32.NewProc("DestroyIcon")
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
	wmLButtonDblClk = 0x0203
	wmRButtonUp   = 0x0205
	wmCommand     = 0x0111

	idShowUI   = 1001
	idSettings = 1002
	idExit     = 1003

	mfString    = 0x00000000
	mfSeparator = 0x00000800
	tpmLeftAlign = 0x0000

	lrDefaultColor = 0x00000000
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
	trayIcon    uintptr
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

	// Load custom app icon from embedded ICO file
	icon := loadEmbeddedIcon()
	if icon == 0 {
		// Fallback to default application icon
		icon, _, _ = pLoadIcon.Call(0, uintptr(32512)) // IDI_APPLICATION
	}
	trayIcon = icon

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

	// Cleanup icon
	if trayIcon != 0 {
		pDestroyIcon.Call(trayIcon)
	}
}

// loadEmbeddedIcon loads the app icon from the embedded ICO file.
func loadEmbeddedIcon() uintptr {
	data, err := iconFS.ReadFile("assets/icon.ico")
	if err != nil || len(data) < 22 {
		return 0
	}

	// ICO format: 6-byte header, then directory entries (16 bytes each), then image data.
	// We want the largest icon (typically 256x256 or 48x48 for tray).
	// For tray, 32x32 or 16x16 is ideal. Let's find the best match.
	numImages := int(data[4]) | int(data[5])<<8
	if numImages == 0 {
		return 0
	}

	// Find the best icon for system tray (prefer 32x32, then 16x16, then largest)
	type iconEntry struct {
		width, height uint8
		offset, size  uint32
	}

	var best iconEntry
	var bestScore int

	for i := 0; i < numImages; i++ {
		off := 6 + i*16
		if off+16 > len(data) {
			break
		}
		w := data[off]
		h := data[off+1]
		size := uint32(data[off+8]) | uint32(data[off+9])<<8 | uint32(data[off+10])<<16 | uint32(data[off+11])<<24
		imgOff := uint32(data[off+12]) | uint32(data[off+13])<<8 | uint32(data[off+14])<<16 | uint32(data[off+15])<<24

		entry := iconEntry{w, h, imgOff, size}

		// Score: prefer 32x32 for tray
		score := 0
		actualW := int(w)
		if actualW == 0 {
			actualW = 256
		}
		if actualW == 32 {
			score = 100
		} else if actualW == 48 {
			score = 90
		} else if actualW == 16 {
			score = 80
		} else if actualW == 24 {
			score = 70
		} else {
			score = actualW
		}

		if score > bestScore {
			bestScore = score
			best = entry
		}
	}

	if best.size == 0 || int(best.offset+best.size) > len(data) {
		return 0
	}

	imgData := data[best.offset : best.offset+best.size]

	// Determine desired size
	desiredW := int(best.width)
	desiredH := int(best.height)
	if desiredW == 0 {
		desiredW = 256
	}
	if desiredH == 0 {
		desiredH = 256
	}

	// Use CreateIconFromResourceEx to create HICON from raw image data
	icon, _, _ := pCreateIconFromResourceEx.Call(
		uintptr(unsafe.Pointer(&imgData[0])),
		uintptr(best.size),
		1, // fIcon = TRUE
		0x00030000, // version
		uintptr(desiredW),
		uintptr(desiredH),
		lrDefaultColor,
	)
	return icon
}

func trayWndProc(hwnd, msg, wParam, lParam uintptr) uintptr {
	switch msg {
	case wmTrayIcon:
		switch lParam {
		case wmLButtonUp, wmLButtonDblClk:
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
		case idSettings:
			// Open settings — launch UI with settings flag
			go LaunchUIWithArgs("--settings")
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
	pAppendMenu.Call(menu, mfString, idSettings, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr("Settings"))))
	pAppendMenu.Call(menu, mfSeparator, 0, 0)
	pAppendMenu.Call(menu, mfString, idExit, uintptr(unsafe.Pointer(syscall.StringToUTF16Ptr("Exit"))))

	var pt point
	pGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))
	pSetForegroundWindow.Call(hwnd)
	pTrackPopupMenu.Call(menu, tpmLeftAlign, uintptr(pt.x), uintptr(pt.y), 0, hwnd, 0)
	pDestroyMenu.Call(menu)
}

// LaunchUI starts the Kotlin UI process.
func LaunchUI() {
	LaunchUIWithArgs()
}

// LaunchUIWithArgs starts the Kotlin UI process with optional arguments.
func LaunchUIWithArgs(args ...string) {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow.exe")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "Flow.exe"
	}
	cmd := exec.Command(uiExe, args...)
	cmd.Dir = dir
	cmd.Start()
}

// LaunchUIWithDownload starts UI and passes a download URL to show the add-download dialog.
func LaunchUIWithDownload(url string) {
	LaunchUIWithArgs("--add-download", url)
}
