# Final Parity Gap Checklist (IDM -> Flow)

Nguon doi chieu: `D:\Repos\IDM`

Muc tieu tai lieu: liet ke ro cac phan chua parity 100% de dong theo cum lon, khong sua le te.

## A) Downloader Engine gaps
- [ ] Dynamic part splitting khi co part cham (split active part)
- [ ] Global/per-job speed throttling (token bucket)
- [ ] HLS/M3U8 pipeline (`hls/*` parity)
- [ ] File changed validation day du (ETag/Last-Modified + strict resume edge-cases)

## B) Queue semantics gaps
- [ ] Queue events parity day du (start/stop/empty/time reached)
- [ ] Full named queue semantics (beyond groundwork):
  - [ ] queue-scoped mutation policies
  - [ ] queue-scoped resume ordering
- [ ] Queue item ordering UX parity (move/swap/requeue full behaviors)

## C) Settings parity gaps
- [ ] Settings page sectioned, full form parity:
  - [ ] Appearance details
  - [ ] DownloadEngine details
  - [ ] BrowserIntegration details
- [ ] Proxy editor parity:
  - [ ] full manual fields + validations
  - [ ] system/manual switching UX
  - [ ] PAC script mode
- [ ] Per-host editor parity:
  - [ ] full list editor
  - [ ] rule editing UX
  - [ ] ordering/uniqueness constraints

## D) Desktop UX gaps
- [ ] Tray UX final polish (consistent show/hide/quit behavior in all cases)
- [ ] Clipboard popup UX parity (confirm/create/edit options)

## E) Release/installer gaps
- [ ] Firefox registration flow hoàn chỉnh (UX + docs + scripts validated)
- [ ] Reinstall/uninstall hardening checklist (idempotent host registration cleanup)
- [ ] Release validation checklist/script

## Execution order (no small-fix mode)
1. Queue semantics closeout
2. Settings full-form closeout
3. Tray/clipboard UX closeout
4. Engine hard features closeout
5. Packaging closeout
